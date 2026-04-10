package com.learnmart.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnmart.app.data.local.LearnMartRoomDatabase
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Dedicated worker for reconciliation jobs. Executes real reconciliation logic:
 * matches settlement rows against payment records, creates discrepancy cases,
 * all within a Room transaction. Scheduled with idle+charging constraints.
 */
@HiltWorker
class ReconciliationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val operationsRepository: OperationsRepository,
    private val paymentRepository: PaymentRepository,
    private val auditRepository: AuditRepository,
    private val database: LearnMartRoomDatabase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val batchId = inputData.getString("batchId") ?: return Result.failure()
        val now = TimeUtils.nowUtc()

        return try {
            val batch = operationsRepository.getSettlementBatchById(batchId)
                ?: return Result.failure()

            val rows = operationsRepository.getSettlementRowsByBatch(batchId)
            val validRows = rows.filter { it.isValid && !it.isDuplicate }
            if (validRows.isEmpty()) return Result.failure()

            val runId = IdGenerator.newId()
            val matches = mutableListOf<ReconciliationMatch>()
            val discrepancies = mutableListOf<DiscrepancyCase>()
            var matchedCount = 0
            var unmatchedCount = 0
            val duplicateCount = rows.count { it.isDuplicate }

            for (row in validRows) {
                val paymentRef = row.paymentReference
                if (paymentRef == null) {
                    unmatchedCount++
                    discrepancies.add(DiscrepancyCase(
                        id = IdGenerator.newId(), reconciliationRunId = runId,
                        settlementRowId = row.id, paymentRecordId = null,
                        discrepancyType = "NO_REFERENCE", description = "No payment reference",
                        notes = "Row: ${row.externalRowId}", status = DiscrepancyCaseStatus.OPEN,
                        resolvedBy = null, resolvedAt = null, resolutionNote = null,
                        createdBy = "SYSTEM", createdAt = now, updatedAt = now
                    ))
                    continue
                }

                val matchedPayment = paymentRepository.getPaymentById(paymentRef)
                if (matchedPayment != null) {
                    if (matchedPayment.amount.compareTo(row.amount) != 0) {
                        discrepancies.add(DiscrepancyCase(
                            id = IdGenerator.newId(), reconciliationRunId = runId,
                            settlementRowId = row.id, paymentRecordId = matchedPayment.id,
                            discrepancyType = "AMOUNT_MISMATCH",
                            description = "Settlement ${row.amount} != payment ${matchedPayment.amount}",
                            notes = "", status = DiscrepancyCaseStatus.OPEN,
                            resolvedBy = null, resolvedAt = null, resolutionNote = null,
                            createdBy = "SYSTEM", createdAt = now, updatedAt = now
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
                        discrepancyType = "UNMATCHED", description = "No match for: $paymentRef",
                        notes = "", status = DiscrepancyCaseStatus.OPEN,
                        resolvedBy = null, resolvedAt = null, resolutionNote = null,
                        createdBy = "SYSTEM", createdAt = now, updatedAt = now
                    ))
                }
            }

            // Atomic write of all reconciliation results within a Room transaction.
            // If any write fails, the entire reconciliation run rolls back — no partial state.
            val run = ReconciliationRun(
                id = runId, batchId = batchId,
                matchedCount = matchedCount, unmatchedCount = unmatchedCount,
                duplicateCount = duplicateCount, discrepancyCount = discrepancies.size,
                runBy = "SYSTEM", runAt = now, status = "COMPLETED"
            )
            database.withTransaction {
                operationsRepository.createReconciliationRun(run)
                if (matches.isNotEmpty()) operationsRepository.createReconciliationMatches(matches)
                discrepancies.forEach { operationsRepository.createDiscrepancyCase(it) }

                val importJob = operationsRepository.getImportJobById(batch.importJobId)
                if (importJob != null) {
                    operationsRepository.updateImportJob(importJob.copy(
                        status = ImportJobStatus.APPLIED, updatedAt = now
                    ))
                }

                auditRepository.logEvent(AuditEvent(
                    id = IdGenerator.newId(), actorId = "SYSTEM", actorUsername = "SYSTEM",
                    actionType = AuditActionType.RECONCILIATION_RUN,
                    targetEntityType = "ReconciliationRun", targetEntityId = runId,
                    beforeSummary = null,
                    afterSummary = "matched=$matchedCount, unmatched=$unmatchedCount, discrepancies=${discrepancies.size}",
                    reason = null, sessionId = null,
                    outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
                ))
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}
