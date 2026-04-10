package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.*
import java.math.BigDecimal

interface OperationsRepository {
    suspend fun createImportJob(job: ImportJob): ImportJob
    suspend fun updateImportJob(job: ImportJob)
    suspend fun getImportJobById(id: String): ImportJob?
    suspend fun getAllImportJobs(): List<ImportJob>

    suspend fun createSettlementBatch(batch: SettlementImportBatch)
    suspend fun getSettlementBatchById(id: String): SettlementImportBatch?
    suspend fun createSettlementRows(rows: List<SettlementImportRow>)
    suspend fun getSettlementRowsByBatch(batchId: String): List<SettlementImportRow>
    suspend fun getSettlementRowByExternalId(externalRowId: String): SettlementImportRow?

    suspend fun createReconciliationRun(run: ReconciliationRun): ReconciliationRun
    suspend fun getReconciliationRunById(id: String): ReconciliationRun?
    suspend fun createReconciliationMatches(matches: List<ReconciliationMatch>)
    suspend fun getMatchesByRunId(runId: String): List<ReconciliationMatch>

    suspend fun createDiscrepancyCase(case_: DiscrepancyCase): DiscrepancyCase
    suspend fun updateDiscrepancyCase(case_: DiscrepancyCase)
    suspend fun getDiscrepancyCaseById(id: String): DiscrepancyCase?
    suspend fun getDiscrepancyCasesByStatus(status: DiscrepancyCaseStatus): List<DiscrepancyCase>
    suspend fun getAllDiscrepancyCases(limit: Int, offset: Int): List<DiscrepancyCase>

    suspend fun createExportJob(job: ExportJob): ExportJob
    suspend fun getExportJobById(id: String): ExportJob?
    suspend fun getAllExportJobs(limit: Int, offset: Int): List<ExportJob>

    suspend fun createBackupArchive(archive: BackupArchive): BackupArchive
    suspend fun updateBackupArchive(archive: BackupArchive)
    suspend fun getBackupArchiveById(id: String): BackupArchive?
    suspend fun getAllBackupArchives(): List<BackupArchive>

    suspend fun createRestoreRun(run: RestoreRun): RestoreRun
    suspend fun updateRestoreRun(run: RestoreRun)
    suspend fun getRestoreRunById(id: String): RestoreRun?

    suspend fun createMaintenanceJobRun(run: MaintenanceJobRun): MaintenanceJobRun
    suspend fun updateMaintenanceJobRun(run: MaintenanceJobRun)

    suspend fun createDataIntegrityIssue(issue: DataIntegrityIssue)
    suspend fun getUnresolvedIntegrityIssues(): List<DataIntegrityIssue>

    // Settlement payment-status update idempotency
    suspend fun hasAppliedSettlementUpdate(idempotencyKey: String): Boolean
    suspend fun saveAppliedSettlementUpdate(idempotencyKey: String, settlementRowId: String, paymentId: String, appliedAt: java.time.Instant)
}
