package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.security.SettlementSignatureVerifier
import com.learnmart.app.security.SignatureResult
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class ImportSettlementUseCase @Inject constructor(
    private val operationsRepository: OperationsRepository,
    private val paymentRepository: PaymentRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager,
    private val signatureVerifier: SettlementSignatureVerifier
) {
    suspend fun importFile(
        fileName: String,
        fileType: String,
        fileSizeBytes: Long,
        rawRows: List<Map<String, String>>, // Parsed CSV/JSON rows as key-value maps
        fileContent: ByteArray? = null,
        signatureHex: String? = null
    ): AppResult<ImportJob> {
        if (!checkPermission.hasPermission(Permission.IMPORT_MANAGE)) {
            return AppResult.PermissionError("Requires import.manage")
        }

        // Validate file size
        val maxSize = policyRepository.getPolicyLongValue(PolicyType.IMPORT_MAPPING, "max_import_size_bytes", 26214400)
        if (fileSizeBytes > maxSize) {
            return AppResult.ValidationError(globalErrors = listOf("File exceeds maximum size of ${maxSize / 1048576}MB"))
        }

        // Validate file type
        if (fileType !in listOf("csv", "json")) {
            return AppResult.ValidationError(globalErrors = listOf("Unsupported file type: $fileType"))
        }

        // Signature verification
        val sigRequired = policyRepository.getPolicyBoolValue(
            PolicyType.IMPORT_MAPPING, "signature_verification_required", false
        )
        var signatureValid: Boolean? = null
        if (sigRequired) {
            if (signatureHex.isNullOrBlank() || fileContent == null) {
                return AppResult.ValidationError(
                    globalErrors = listOf("Signature verification is required but no signature or file content was provided")
                )
            }
            val sharedSecret = policyRepository.getPolicyValue(
                PolicyType.IMPORT_MAPPING, "settlement_signature_secret", ""
            )
            when (val result = signatureVerifier.verify(fileContent, signatureHex, sharedSecret)) {
                is SignatureResult.Valid -> signatureValid = true
                is SignatureResult.Invalid -> {
                    return AppResult.ValidationError(
                        globalErrors = listOf("Settlement file signature is invalid. Import rejected.")
                    )
                }
                is SignatureResult.Error -> {
                    return AppResult.ValidationError(
                        globalErrors = listOf("Signature verification error: ${result.message}")
                    )
                }
                is SignatureResult.NoSecretConfigured -> {
                    return AppResult.ValidationError(
                        globalErrors = listOf("Signature verification is required but no shared secret is configured")
                    )
                }
            }
        } else if (!signatureHex.isNullOrBlank() && fileContent != null) {
            // Signature provided but not required - still verify opportunistically
            val sharedSecret = policyRepository.getPolicyValue(
                PolicyType.IMPORT_MAPPING, "settlement_signature_secret", ""
            )
            if (sharedSecret.isNotBlank()) {
                signatureValid = when (signatureVerifier.verify(fileContent, signatureHex, sharedSecret)) {
                    is SignatureResult.Valid -> true
                    else -> false
                }
            }
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"
        val jobId = IdGenerator.newId()

        // Create import job
        val job = ImportJob(
            id = jobId, fileName = fileName, fileType = fileType, fileSizeBytes = fileSizeBytes,
            status = ImportJobStatus.VALIDATING, totalRows = rawRows.size,
            validRows = 0, errorRows = 0, errorDetails = null,
            importedBy = userId, createdAt = now, updatedAt = now
        )
        operationsRepository.createImportJob(job)

        // Validate and parse rows
        val batchId = IdGenerator.newId()
        val parsedRows = mutableListOf<SettlementImportRow>()
        val errors = mutableListOf<String>()
        var validCount = 0
        var errorCount = 0
        val seenExternalIds = mutableSetOf<String>()

        for ((index, row) in rawRows.withIndex()) {
            val rowErrors = mutableListOf<String>()
            val externalRowId = row["external_id"]?.trim()
            val amountStr = row["amount"]?.trim()
            val paymentRef = row["payment_reference"]?.trim()
            val tenderType = row["tender_type"]?.trim()
            val dateStr = row["transaction_date"]?.trim()

            // Validate amount
            val amount = try { BigDecimal(amountStr ?: "") } catch (_: Exception) {
                rowErrors.add("Invalid amount at row ${index + 1}"); BigDecimal.ZERO
            }

            // Validate date
            val txDate = try {
                dateStr?.let { Instant.parse(it) }
            } catch (_: Exception) {
                rowErrors.add("Invalid date at row ${index + 1}"); null
            }

            // Dedup check
            val isDuplicate = if (externalRowId != null) {
                if (seenExternalIds.contains(externalRowId)) {
                    rowErrors.add("Duplicate external_id: $externalRowId"); true
                } else {
                    seenExternalIds.add(externalRowId)
                    // Check against existing rows
                    val existing = operationsRepository.getSettlementRowByExternalId(externalRowId)
                    existing != null
                }
            } else false

            val isValid = rowErrors.isEmpty()
            if (isValid) validCount++ else errorCount++

            parsedRows.add(SettlementImportRow(
                id = IdGenerator.newId(), batchId = batchId, externalRowId = externalRowId,
                paymentReference = paymentRef, amount = amount, tenderType = tenderType,
                status = row["status"]?.trim(), transactionDate = txDate,
                rawData = row.toString(), isValid = isValid,
                validationErrors = if (rowErrors.isNotEmpty()) rowErrors.joinToString("; ") else null,
                isDuplicate = isDuplicate
            ))

            errors.addAll(rowErrors)
        }

        // If fatal errors (all invalid), reject
        if (validCount == 0 && rawRows.isNotEmpty()) {
            operationsRepository.updateImportJob(job.copy(
                status = ImportJobStatus.REJECTED, validRows = 0, errorRows = errorCount,
                errorDetails = errors.take(50).joinToString("\n"), updatedAt = TimeUtils.nowUtc()
            ))
            auditLog(AuditActionType.IMPORT_REJECTED, jobId, "All rows invalid", userId, now)
            return AppResult.ValidationError(globalErrors = listOf("All rows failed validation. Import rejected."))
        }

        // Create batch and rows
        operationsRepository.createSettlementBatch(SettlementImportBatch(
            id = batchId, importJobId = jobId, batchIdentifier = "BATCH-${jobId.take(8)}",
            signatureValid = signatureValid, rowCount = rawRows.size, createdAt = now
        ))
        operationsRepository.createSettlementRows(parsedRows)

        val updatedJob = job.copy(
            status = ImportJobStatus.READY_TO_APPLY, validRows = validCount, errorRows = errorCount,
            errorDetails = if (errors.isNotEmpty()) errors.take(50).joinToString("\n") else null,
            updatedAt = TimeUtils.nowUtc()
        )
        operationsRepository.updateImportJob(updatedJob)

        auditLog(AuditActionType.IMPORT_STARTED, jobId, "Validated: $validCount valid, $errorCount errors", userId, now)

        return AppResult.Success(updatedJob,
            warnings = if (errorCount > 0) listOf("$errorCount rows had validation errors") else emptyList()
        )
    }

    private suspend fun auditLog(action: AuditActionType, targetId: String, detail: String, userId: String, now: Instant) {
        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(), actorId = userId, actorUsername = null,
            actionType = action, targetEntityType = "ImportJob", targetEntityId = targetId,
            beforeSummary = null, afterSummary = detail, reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))
    }
}
