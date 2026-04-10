package com.learnmart.app.domain.model

import java.math.BigDecimal
import java.time.Instant

// --- Import Job Lifecycle ---
enum class ImportJobStatus {
    CREATED, VALIDATING, REJECTED, READY_TO_APPLY, APPLYING, APPLIED, FAILED, RETRYABLE;
    fun allowedTransitions(): Set<ImportJobStatus> = when (this) {
        CREATED -> setOf(VALIDATING)
        VALIDATING -> setOf(REJECTED, READY_TO_APPLY)
        READY_TO_APPLY -> setOf(APPLYING)
        APPLYING -> setOf(APPLIED, FAILED)
        REJECTED -> emptySet()
        FAILED -> setOf(RETRYABLE)
        RETRYABLE -> setOf(APPLYING)
        APPLIED -> emptySet()
    }
    fun canTransitionTo(t: ImportJobStatus) = t in allowedTransitions()
}

// --- Backup Lifecycle ---
enum class BackupStatus {
    REQUESTED, RUNNING, SUCCEEDED, FAILED, RETRYABLE, VERIFIED;
    fun allowedTransitions(): Set<BackupStatus> = when (this) {
        REQUESTED -> setOf(RUNNING)
        RUNNING -> setOf(SUCCEEDED, FAILED)
        FAILED -> setOf(RETRYABLE)
        RETRYABLE -> setOf(RUNNING)
        SUCCEEDED -> setOf(VERIFIED)
        VERIFIED -> emptySet()
    }
    fun canTransitionTo(t: BackupStatus) = t in allowedTransitions()
}

data class ImportJob(
    val id: String, val fileName: String, val fileType: String, val fileSizeBytes: Long,
    val status: ImportJobStatus, val totalRows: Int, val validRows: Int, val errorRows: Int,
    val errorDetails: String?, val importedBy: String, val createdAt: Instant, val updatedAt: Instant
)

data class SettlementImportBatch(
    val id: String, val importJobId: String, val batchIdentifier: String,
    val signatureValid: Boolean?, val rowCount: Int, val createdAt: Instant
)

data class SettlementImportRow(
    val id: String, val batchId: String, val externalRowId: String?,
    val paymentReference: String?, val amount: BigDecimal, val tenderType: String?,
    val status: String?, val transactionDate: Instant?,
    val rawData: String, val isValid: Boolean, val validationErrors: String?, val isDuplicate: Boolean
)

data class ReconciliationRun(
    val id: String, val batchId: String, val matchedCount: Int, val unmatchedCount: Int,
    val duplicateCount: Int, val discrepancyCount: Int,
    val runBy: String, val runAt: Instant, val status: String
)

data class ReconciliationMatch(
    val id: String, val reconciliationRunId: String, val settlementRowId: String,
    val paymentRecordId: String, val matchType: String, val matchedAt: Instant
)

enum class DiscrepancyCaseStatus { OPEN, INVESTIGATING, RESOLVED, CLOSED }

data class DiscrepancyCase(
    val id: String, val reconciliationRunId: String?, val settlementRowId: String?,
    val paymentRecordId: String?, val discrepancyType: String, val description: String,
    val notes: String, val status: DiscrepancyCaseStatus,
    val resolvedBy: String?, val resolvedAt: Instant?,
    val resolutionNote: String?, val createdBy: String, val createdAt: Instant, val updatedAt: Instant
)

data class ExportJob(
    val id: String, val exportType: String, val format: String, val fileName: String,
    val filePath: String?, val status: String, val recordCount: Int,
    val exportedBy: String, val exportedAt: Instant, val checksum: String?
)

data class BackupArchive(
    val id: String, val status: BackupStatus, val schemaVersion: Int, val appVersion: String,
    val backupTimestamp: Instant, val filePath: String?, val fileSizeBytes: Long?,
    val checksumManifest: String?, val encryptionMethod: String,
    val createdBy: String, val createdAt: Instant, val updatedAt: Instant
)

data class RestoreRun(
    val id: String, val backupArchiveId: String, val status: String,
    val restoredBy: String, val startedAt: Instant, val completedAt: Instant?,
    val errorMessage: String?
)

data class MaintenanceJobRun(
    val id: String, val jobType: String, val status: String,
    val itemsProcessed: Int, val errorCount: Int, val startedAt: Instant,
    val completedAt: Instant?, val notes: String?
)

data class DataIntegrityIssue(
    val id: String, val entityType: String, val entityId: String,
    val issueType: String, val description: String, val severity: String,
    val detectedAt: Instant, val resolvedAt: Instant?, val resolvedBy: String?
)

data class SettlementPaymentUpdateRecord(
    val idempotencyKey: String,
    val settlementRowId: String,
    val paymentId: String,
    val appliedAt: Instant
)
