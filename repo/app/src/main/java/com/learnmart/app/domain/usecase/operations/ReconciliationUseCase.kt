package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.data.local.TransactionRunner
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import java.security.MessageDigest
import javax.inject.Inject

class ReconciliationUseCase @Inject constructor(
    private val operationsRepository: OperationsRepository,
    private val paymentRepository: PaymentRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager,
    private val transactionRunner: TransactionRunner
) {
    /**
     * Run reconciliation for a batch. When [systemCaller] is true, permission
     * checks are skipped (used by WorkManager-scheduled background jobs that
     * have no interactive session). Interactive callers must leave it false.
     */
    suspend fun runReconciliation(
        batchId: String,
        systemCaller: Boolean = false
    ): AppResult<ReconciliationRun> {
        if (!systemCaller && !checkPermission.hasPermission(Permission.PAYMENT_RECONCILE)) {
            return AppResult.PermissionError("Requires payment.reconcile")
        }

        val batch = operationsRepository.getSettlementBatchById(batchId)
            ?: return AppResult.NotFoundError("BATCH_NOT_FOUND")

        val rows = operationsRepository.getSettlementRowsByBatch(batchId)
        val validRows = rows.filter { it.isValid && !it.isDuplicate }

        if (validRows.isEmpty()) {
            return AppResult.ValidationError(globalErrors = listOf("No valid rows to reconcile"))
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"
        val runId = IdGenerator.newId()

        val matches = mutableListOf<ReconciliationMatch>()
        val discrepancies = mutableListOf<DiscrepancyCase>()
        var matchedCount = 0
        var unmatchedCount = 0
        val duplicateCount = rows.count { it.isDuplicate }
        var paymentUpdatesApplied = 0

        for (row in validRows) {
            val paymentRef = row.paymentReference
            if (paymentRef == null) {
                unmatchedCount++
                discrepancies.add(DiscrepancyCase(
                    id = IdGenerator.newId(), reconciliationRunId = runId,
                    settlementRowId = row.id, paymentRecordId = null,
                    discrepancyType = "NO_REFERENCE", description = "Settlement row has no payment reference",
                    notes = "Row external_id: ${row.externalRowId}", status = DiscrepancyCaseStatus.OPEN,
                    resolvedBy = null, resolvedAt = null, resolutionNote = null,
                    createdBy = userId, createdAt = now, updatedAt = now
                ))
                continue
            }

            // Try to match against payment records by external reference
            val payments = paymentRepository.getPaymentsForOrder(paymentRef)
            // Also try matching by payment ID directly
            val directMatch = paymentRepository.getPaymentById(paymentRef)

            val matchedPayment = directMatch ?: payments.firstOrNull()

            if (matchedPayment != null) {
                // Amount check
                if (matchedPayment.amount.compareTo(row.amount) != 0) {
                    discrepancies.add(DiscrepancyCase(
                        id = IdGenerator.newId(), reconciliationRunId = runId,
                        settlementRowId = row.id, paymentRecordId = matchedPayment.id,
                        discrepancyType = "AMOUNT_MISMATCH",
                        description = "Settlement amount ${row.amount} != payment amount ${matchedPayment.amount}",
                        notes = "Row external_id: ${row.externalRowId ?: "N/A"}, " +
                            "payment_ref: $paymentRef, " +
                            "settlement_amount: ${row.amount}, " +
                            "payment_amount: ${matchedPayment.amount}, " +
                            "difference: ${row.amount.subtract(matchedPayment.amount)}",
                        status = DiscrepancyCaseStatus.OPEN,
                        resolvedBy = null, resolvedAt = null, resolutionNote = null,
                        createdBy = userId, createdAt = now, updatedAt = now
                    ))
                }

                matches.add(ReconciliationMatch(
                    id = IdGenerator.newId(), reconciliationRunId = runId,
                    settlementRowId = row.id, paymentRecordId = matchedPayment.id,
                    matchType = "REFERENCE", matchedAt = now
                ))
                matchedCount++
            } else {
                unmatchedCount++
                discrepancies.add(DiscrepancyCase(
                    id = IdGenerator.newId(), reconciliationRunId = runId,
                    settlementRowId = row.id, paymentRecordId = null,
                    discrepancyType = "UNMATCHED", description = "No matching payment found for reference: $paymentRef",
                    notes = "Row external_id: ${row.externalRowId ?: "N/A"}, " +
                        "payment_ref: $paymentRef, " +
                        "settlement_amount: ${row.amount}, " +
                        "tender_type: ${row.tenderType ?: "N/A"}",
                    status = DiscrepancyCaseStatus.OPEN,
                    resolvedBy = null, resolvedAt = null, resolutionNote = null,
                    createdBy = userId, createdAt = now, updatedAt = now
                ))
            }
        }

        // Atomic: create run + matches + discrepancies + payment status updates + update import job + audit
        val run = ReconciliationRun(
            id = runId, batchId = batchId,
            matchedCount = matchedCount, unmatchedCount = unmatchedCount,
            duplicateCount = duplicateCount, discrepancyCount = discrepancies.size,
            runBy = userId, runAt = now, status = "COMPLETED"
        )
        try {
            transactionRunner.runInTransaction {
                operationsRepository.createReconciliationRun(run)
                if (matches.isNotEmpty()) operationsRepository.createReconciliationMatches(matches)
                discrepancies.forEach { operationsRepository.createDiscrepancyCase(it) }

                // --- Idempotent payment-status updates from settlement rows ---
                for (match in matches) {
                    val row = validRows.find { it.id == match.settlementRowId } ?: continue
                    val rowStatus = row.status ?: continue

                    // Compute idempotency key: externalRowId + status hash
                    val idempotencyKey = computeSettlementUpdateKey(
                        row.externalRowId ?: row.id, rowStatus
                    )

                    // Check if already applied
                    if (operationsRepository.hasAppliedSettlementUpdate(idempotencyKey)) {
                        continue // Skip - already applied
                    }

                    // Map settlement status to payment status
                    val targetPaymentStatus = mapSettlementStatusToPaymentStatus(rowStatus)
                    if (targetPaymentStatus != null) {
                        val payment = paymentRepository.getPaymentById(match.paymentRecordId)
                        if (payment != null && payment.status != targetPaymentStatus &&
                            payment.status.canTransitionTo(targetPaymentStatus)) {
                            paymentRepository.updatePaymentStatus(
                                payment.id, targetPaymentStatus, payment.version
                            )
                            paymentUpdatesApplied++
                        }
                    }

                    // Persist dedup record
                    operationsRepository.saveAppliedSettlementUpdate(
                        idempotencyKey = idempotencyKey,
                        settlementRowId = row.id,
                        paymentId = match.paymentRecordId,
                        appliedAt = now
                    )
                }

                // Update import job
                val importJob = operationsRepository.getImportJobById(batch.importJobId)
                if (importJob != null) {
                    operationsRepository.updateImportJob(importJob.copy(
                        status = ImportJobStatus.APPLIED, updatedAt = now
                    ))
                }

                auditRepository.logEvent(AuditEvent(
                    id = IdGenerator.newId(), actorId = userId, actorUsername = null,
                    actionType = AuditActionType.RECONCILIATION_RUN,
                    targetEntityType = "ReconciliationRun", targetEntityId = runId,
                    beforeSummary = null,
                    afterSummary = "matched=$matchedCount, unmatched=$unmatchedCount, discrepancies=${discrepancies.size}, paymentUpdates=$paymentUpdatesApplied",
                    reason = null, sessionId = sessionManager.getCurrentSessionId(),
                    outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
                ))
            }
        } catch (e: Exception) {
            return AppResult.SystemError(
                "RECONCILIATION_FAILED",
                "Reconciliation failed during atomic write: ${e.message}",
                retryable = true
            )
        }

        return AppResult.Success(run)
    }

    suspend fun resolveDiscrepancy(caseId: String, resolutionNote: String): AppResult<DiscrepancyCase> {
        if (!checkPermission.hasPermission(Permission.PAYMENT_RECONCILE)) {
            return AppResult.PermissionError()
        }
        if (resolutionNote.isBlank()) {
            return AppResult.ValidationError(fieldErrors = mapOf("resolutionNote" to "Resolution note is required"))
        }

        val case_ = operationsRepository.getDiscrepancyCaseById(caseId)
            ?: return AppResult.NotFoundError("DISCREPANCY_NOT_FOUND")

        val now = TimeUtils.nowUtc()
        val resolved = case_.copy(
            status = DiscrepancyCaseStatus.RESOLVED,
            resolvedBy = sessionManager.getCurrentUserId(), resolvedAt = now,
            resolutionNote = resolutionNote, updatedAt = now
        )
        operationsRepository.updateDiscrepancyCase(resolved)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(), actorId = sessionManager.getCurrentUserId(), actorUsername = null,
            actionType = AuditActionType.DISCREPANCY_RESOLVED,
            targetEntityType = "DiscrepancyCase", targetEntityId = caseId,
            beforeSummary = "status=${case_.status}", afterSummary = "status=RESOLVED",
            reason = resolutionNote, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        return AppResult.Success(resolved)
    }

    suspend fun getAllDiscrepancies(limit: Int, offset: Int): List<DiscrepancyCase> =
        operationsRepository.getAllDiscrepancyCases(limit, offset)

    suspend fun getOpenDiscrepancies(): List<DiscrepancyCase> =
        operationsRepository.getDiscrepancyCasesByStatus(DiscrepancyCaseStatus.OPEN)

    private fun computeSettlementUpdateKey(externalRowId: String, status: String): String {
        val raw = "$externalRowId|$status"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun mapSettlementStatusToPaymentStatus(settlementStatus: String): PaymentStatus? {
        return when (settlementStatus.uppercase().trim()) {
            "CLEARED", "SETTLED", "CONFIRMED" -> PaymentStatus.CLEARED
            "VOIDED", "VOID", "CANCELLED" -> PaymentStatus.VOIDED
            "DISCREPANCY", "FLAGGED", "DISPUTED" -> PaymentStatus.DISCREPANCY_FLAGGED
            "RESOLVED" -> PaymentStatus.RESOLVED
            else -> null
        }
    }
}
