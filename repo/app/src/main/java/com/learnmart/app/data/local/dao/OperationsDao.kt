package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.BackupArchiveEntity
import com.learnmart.app.data.local.entity.DataIntegrityIssueEntity
import com.learnmart.app.data.local.entity.DiscrepancyCaseEntity
import com.learnmart.app.data.local.entity.ExportJobEntity
import com.learnmart.app.data.local.entity.ImportJobEntity
import com.learnmart.app.data.local.entity.MaintenanceJobRunEntity
import com.learnmart.app.data.local.entity.ReconciliationMatchEntity
import com.learnmart.app.data.local.entity.ReconciliationRunEntity
import com.learnmart.app.data.local.entity.RestoreRunEntity
import com.learnmart.app.data.local.entity.SettlementImportBatchEntity
import com.learnmart.app.data.local.entity.SettlementImportRowEntity

@Dao
interface OperationsDao {

    // ── ImportJob ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportJob(importJob: ImportJobEntity)

    @Update
    suspend fun updateImportJob(importJob: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    suspend fun getImportJobById(id: String): ImportJobEntity?

    @Query("SELECT * FROM import_jobs ORDER BY created_at DESC")
    suspend fun getAllImportJobs(): List<ImportJobEntity>

    @Query("SELECT * FROM import_jobs WHERE status = :status")
    suspend fun getImportJobsByStatus(status: String): List<ImportJobEntity>

    // ── SettlementImportBatch ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementImportBatch(batch: SettlementImportBatchEntity)

    @Query("SELECT * FROM settlement_import_batches WHERE id = :id")
    suspend fun getSettlementImportBatchById(id: String): SettlementImportBatchEntity?

    @Query("SELECT * FROM settlement_import_batches WHERE import_job_id = :importJobId")
    suspend fun getSettlementImportBatchesByImportJobId(importJobId: String): List<SettlementImportBatchEntity>

    // ── SettlementImportRow ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementImportRow(row: SettlementImportRowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSettlementImportRows(rows: List<SettlementImportRowEntity>)

    @Query("SELECT * FROM settlement_import_rows WHERE batch_id = :batchId")
    suspend fun getSettlementImportRowsByBatchId(batchId: String): List<SettlementImportRowEntity>

    @Query("SELECT * FROM settlement_import_rows WHERE external_row_id = :externalRowId")
    suspend fun getSettlementImportRowsByExternalRowId(externalRowId: String): List<SettlementImportRowEntity>

    @Query("SELECT COUNT(*) FROM settlement_import_rows WHERE batch_id = :batchId")
    suspend fun countSettlementImportRowsByBatchId(batchId: String): Int

    // ── ReconciliationRun ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationRun(run: ReconciliationRunEntity)

    @Query("SELECT * FROM reconciliation_runs WHERE id = :id")
    suspend fun getReconciliationRunById(id: String): ReconciliationRunEntity?

    @Query("SELECT * FROM reconciliation_runs WHERE batch_id = :batchId")
    suspend fun getReconciliationRunsByBatchId(batchId: String): List<ReconciliationRunEntity>

    // ── ReconciliationMatch ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconciliationMatch(match: ReconciliationMatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReconciliationMatches(matches: List<ReconciliationMatchEntity>)

    @Query("SELECT * FROM reconciliation_matches WHERE reconciliation_run_id = :runId")
    suspend fun getReconciliationMatchesByRunId(runId: String): List<ReconciliationMatchEntity>

    // ── DiscrepancyCase ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscrepancyCase(discrepancyCase: DiscrepancyCaseEntity)

    @Update
    suspend fun updateDiscrepancyCase(discrepancyCase: DiscrepancyCaseEntity)

    @Query("SELECT * FROM discrepancy_cases WHERE id = :id")
    suspend fun getDiscrepancyCaseById(id: String): DiscrepancyCaseEntity?

    @Query("SELECT * FROM discrepancy_cases WHERE status = :status")
    suspend fun getDiscrepancyCasesByStatus(status: String): List<DiscrepancyCaseEntity>

    @Query("SELECT * FROM discrepancy_cases ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllDiscrepancyCasesPaged(limit: Int, offset: Int): List<DiscrepancyCaseEntity>

    @Query("SELECT * FROM discrepancy_cases WHERE reconciliation_run_id = :reconciliationRunId")
    suspend fun getDiscrepancyCasesByReconciliationRunId(reconciliationRunId: String): List<DiscrepancyCaseEntity>

    // ── ExportJob ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportJob(exportJob: ExportJobEntity)

    @Query("SELECT * FROM export_jobs WHERE id = :id")
    suspend fun getExportJobById(id: String): ExportJobEntity?

    @Query("SELECT * FROM export_jobs ORDER BY exported_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllExportJobsPaged(limit: Int, offset: Int): List<ExportJobEntity>

    // ── BackupArchive ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupArchive(archive: BackupArchiveEntity)

    @Update
    suspend fun updateBackupArchive(archive: BackupArchiveEntity)

    @Query("SELECT * FROM backup_archives WHERE id = :id")
    suspend fun getBackupArchiveById(id: String): BackupArchiveEntity?

    @Query("SELECT * FROM backup_archives ORDER BY created_at DESC")
    suspend fun getAllBackupArchives(): List<BackupArchiveEntity>

    @Query("SELECT * FROM backup_archives WHERE status = :status")
    suspend fun getBackupArchivesByStatus(status: String): List<BackupArchiveEntity>

    // ── RestoreRun ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestoreRun(restoreRun: RestoreRunEntity)

    @Update
    suspend fun updateRestoreRun(restoreRun: RestoreRunEntity)

    @Query("SELECT * FROM restore_runs WHERE id = :id")
    suspend fun getRestoreRunById(id: String): RestoreRunEntity?

    @Query("SELECT * FROM restore_runs WHERE backup_archive_id = :backupArchiveId")
    suspend fun getRestoreRunsByBackupArchiveId(backupArchiveId: String): List<RestoreRunEntity>

    // ── MaintenanceJobRun ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceJobRun(jobRun: MaintenanceJobRunEntity)

    @Update
    suspend fun updateMaintenanceJobRun(jobRun: MaintenanceJobRunEntity)

    @Query("SELECT * FROM maintenance_job_runs WHERE id = :id")
    suspend fun getMaintenanceJobRunById(id: String): MaintenanceJobRunEntity?

    @Query("SELECT * FROM maintenance_job_runs ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllMaintenanceJobRunsPaged(limit: Int, offset: Int): List<MaintenanceJobRunEntity>

    // ── DataIntegrityIssue ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataIntegrityIssue(issue: DataIntegrityIssueEntity)

    @Update
    suspend fun updateDataIntegrityIssue(issue: DataIntegrityIssueEntity)

    @Query("SELECT * FROM data_integrity_issues WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun getDataIntegrityIssuesByEntityTypeAndId(entityType: String, entityId: String): List<DataIntegrityIssueEntity>

    @Query("SELECT * FROM data_integrity_issues WHERE resolved_at IS NULL")
    suspend fun getUnresolvedDataIntegrityIssues(): List<DataIntegrityIssueEntity>

    // Settlement payment-status update idempotency tracking
    @Query("SELECT COUNT(*) FROM settlement_payment_updates WHERE idempotency_key = :idempotencyKey")
    suspend fun countAppliedSettlementUpdates(idempotencyKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppliedSettlementUpdate(entity: com.learnmart.app.data.local.entity.SettlementPaymentUpdateEntity)
}
