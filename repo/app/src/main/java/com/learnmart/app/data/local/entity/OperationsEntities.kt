package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_jobs",
    indices = [Index(value = ["status"])]
)
data class ImportJobEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_type")
    val fileType: String,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "total_rows")
    val totalRows: Int,
    @ColumnInfo(name = "valid_rows")
    val validRows: Int,
    @ColumnInfo(name = "error_rows")
    val errorRows: Int,
    @ColumnInfo(name = "error_details")
    val errorDetails: String? = null,
    @ColumnInfo(name = "imported_by")
    val importedBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "settlement_import_batches",
    indices = [Index(value = ["import_job_id"])]
)
data class SettlementImportBatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "import_job_id")
    val importJobId: String,
    @ColumnInfo(name = "batch_identifier")
    val batchIdentifier: String,
    @ColumnInfo(name = "signature_valid")
    val signatureValid: Boolean? = null,
    @ColumnInfo(name = "row_count")
    val rowCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "settlement_import_rows",
    indices = [
        Index(value = ["batch_id"]),
        Index(value = ["external_row_id"])
    ]
)
data class SettlementImportRowEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "external_row_id")
    val externalRowId: String? = null,
    @ColumnInfo(name = "payment_reference")
    val paymentReference: String? = null,
    @ColumnInfo(name = "amount")
    val amount: String,
    @ColumnInfo(name = "tender_type")
    val tenderType: String? = null,
    @ColumnInfo(name = "status")
    val status: String? = null,
    @ColumnInfo(name = "transaction_date")
    val transactionDate: Long? = null,
    @ColumnInfo(name = "raw_data")
    val rawData: String,
    @ColumnInfo(name = "is_valid")
    val isValid: Boolean,
    @ColumnInfo(name = "validation_errors")
    val validationErrors: String? = null,
    @ColumnInfo(name = "is_duplicate")
    val isDuplicate: Boolean
)

@Entity(
    tableName = "reconciliation_runs",
    indices = [Index(value = ["batch_id"])]
)
data class ReconciliationRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "matched_count")
    val matchedCount: Int,
    @ColumnInfo(name = "unmatched_count")
    val unmatchedCount: Int,
    @ColumnInfo(name = "duplicate_count")
    val duplicateCount: Int,
    @ColumnInfo(name = "discrepancy_count")
    val discrepancyCount: Int,
    @ColumnInfo(name = "run_by")
    val runBy: String,
    @ColumnInfo(name = "run_at")
    val runAt: Long,
    @ColumnInfo(name = "status")
    val status: String
)

@Entity(
    tableName = "reconciliation_matches",
    indices = [Index(value = ["reconciliation_run_id"])]
)
data class ReconciliationMatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "reconciliation_run_id")
    val reconciliationRunId: String,
    @ColumnInfo(name = "settlement_row_id")
    val settlementRowId: String,
    @ColumnInfo(name = "payment_record_id")
    val paymentRecordId: String,
    @ColumnInfo(name = "match_type")
    val matchType: String,
    @ColumnInfo(name = "matched_at")
    val matchedAt: Long
)

@Entity(
    tableName = "discrepancy_cases",
    indices = [Index(value = ["status"])]
)
data class DiscrepancyCaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "reconciliation_run_id")
    val reconciliationRunId: String? = null,
    @ColumnInfo(name = "settlement_row_id")
    val settlementRowId: String? = null,
    @ColumnInfo(name = "payment_record_id")
    val paymentRecordId: String? = null,
    @ColumnInfo(name = "discrepancy_type")
    val discrepancyType: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "notes")
    val notes: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "resolved_by")
    val resolvedBy: String? = null,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "resolution_note")
    val resolutionNote: String? = null,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(tableName = "export_jobs")
data class ExportJobEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "export_type")
    val exportType: String,
    @ColumnInfo(name = "format")
    val format: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "record_count")
    val recordCount: Int,
    @ColumnInfo(name = "exported_by")
    val exportedBy: String,
    @ColumnInfo(name = "exported_at")
    val exportedAt: Long,
    @ColumnInfo(name = "checksum")
    val checksum: String? = null
)

@Entity(
    tableName = "backup_archives",
    indices = [Index(value = ["status"])]
)
data class BackupArchiveEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "app_version")
    val appVersion: String,
    @ColumnInfo(name = "backup_timestamp")
    val backupTimestamp: Long,
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "checksum_manifest")
    val checksumManifest: String? = null,
    @ColumnInfo(name = "encryption_method")
    val encryptionMethod: String,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "restore_runs",
    indices = [Index(value = ["backup_archive_id"])]
)
data class RestoreRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "backup_archive_id")
    val backupArchiveId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "restored_by")
    val restoredBy: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)

@Entity(
    tableName = "maintenance_job_runs",
    indices = [Index(value = ["job_type"])]
)
data class MaintenanceJobRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "job_type")
    val jobType: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "items_processed")
    val itemsProcessed: Int,
    @ColumnInfo(name = "error_count")
    val errorCount: Int,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null
)

@Entity(
    tableName = "data_integrity_issues",
    indices = [Index(value = ["entity_type", "entity_id"])]
)
data class DataIntegrityIssueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "issue_type")
    val issueType: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "severity")
    val severity: String,
    @ColumnInfo(name = "detected_at")
    val detectedAt: Long,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "resolved_by")
    val resolvedBy: String? = null
)

@Entity(tableName = "settlement_payment_updates")
data class SettlementPaymentUpdateEntity(
    @PrimaryKey
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "settlement_row_id")
    val settlementRowId: String,
    @ColumnInfo(name = "payment_id")
    val paymentId: String,
    @ColumnInfo(name = "applied_at")
    val appliedAt: Long
)
