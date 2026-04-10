package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.OperationsDao
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
import com.learnmart.app.domain.model.BackupArchive
import com.learnmart.app.domain.model.BackupStatus
import com.learnmart.app.domain.model.DataIntegrityIssue
import com.learnmart.app.domain.model.DiscrepancyCase
import com.learnmart.app.domain.model.DiscrepancyCaseStatus
import com.learnmart.app.domain.model.ExportJob
import com.learnmart.app.domain.model.ImportJob
import com.learnmart.app.domain.model.ImportJobStatus
import com.learnmart.app.domain.model.MaintenanceJobRun
import com.learnmart.app.domain.model.ReconciliationMatch
import com.learnmart.app.domain.model.ReconciliationRun
import com.learnmart.app.domain.model.RestoreRun
import com.learnmart.app.domain.model.SettlementImportBatch
import com.learnmart.app.domain.model.SettlementImportRow
import com.learnmart.app.domain.repository.OperationsRepository
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationsRepositoryImpl @Inject constructor(
    private val operationsDao: OperationsDao
) : OperationsRepository {

    // ── ImportJob ────────────────────────────────────────────────────────────────

    override suspend fun createImportJob(job: ImportJob): ImportJob {
        operationsDao.insertImportJob(job.toEntity())
        return job
    }

    override suspend fun updateImportJob(job: ImportJob) {
        operationsDao.updateImportJob(job.toEntity())
    }

    override suspend fun getImportJobById(id: String): ImportJob? =
        operationsDao.getImportJobById(id)?.toDomain()

    override suspend fun getAllImportJobs(): List<ImportJob> =
        operationsDao.getAllImportJobs().map { it.toDomain() }

    // ── SettlementImportBatch ───────────────────────────────────────────────────

    override suspend fun createSettlementBatch(batch: SettlementImportBatch) {
        operationsDao.insertSettlementImportBatch(batch.toEntity())
    }

    override suspend fun getSettlementBatchById(id: String): SettlementImportBatch? =
        operationsDao.getSettlementImportBatchById(id)?.toDomain()

    // ── SettlementImportRow ─────────────────────────────────────────────────────

    override suspend fun createSettlementRows(rows: List<SettlementImportRow>) {
        operationsDao.insertAllSettlementImportRows(rows.map { it.toEntity() })
    }

    override suspend fun getSettlementRowsByBatch(batchId: String): List<SettlementImportRow> =
        operationsDao.getSettlementImportRowsByBatchId(batchId).map { it.toDomain() }

    override suspend fun getSettlementRowByExternalId(externalRowId: String): SettlementImportRow? =
        operationsDao.getSettlementImportRowsByExternalRowId(externalRowId)
            .firstOrNull()?.toDomain()

    // ── ReconciliationRun ───────────────────────────────────────────────────────

    override suspend fun createReconciliationRun(run: ReconciliationRun): ReconciliationRun {
        operationsDao.insertReconciliationRun(run.toEntity())
        return run
    }

    override suspend fun getReconciliationRunById(id: String): ReconciliationRun? =
        operationsDao.getReconciliationRunById(id)?.toDomain()

    // ── ReconciliationMatch ─────────────────────────────────────────────────────

    override suspend fun createReconciliationMatches(matches: List<ReconciliationMatch>) {
        operationsDao.insertAllReconciliationMatches(matches.map { it.toEntity() })
    }

    override suspend fun getMatchesByRunId(runId: String): List<ReconciliationMatch> =
        operationsDao.getReconciliationMatchesByRunId(runId).map { it.toDomain() }

    // ── DiscrepancyCase ─────────────────────────────────────────────────────────

    override suspend fun createDiscrepancyCase(case_: DiscrepancyCase): DiscrepancyCase {
        operationsDao.insertDiscrepancyCase(case_.toEntity())
        return case_
    }

    override suspend fun updateDiscrepancyCase(case_: DiscrepancyCase) {
        operationsDao.updateDiscrepancyCase(case_.toEntity())
    }

    override suspend fun getDiscrepancyCaseById(id: String): DiscrepancyCase? =
        operationsDao.getDiscrepancyCaseById(id)?.toDomain()

    override suspend fun getDiscrepancyCasesByStatus(status: DiscrepancyCaseStatus): List<DiscrepancyCase> =
        operationsDao.getDiscrepancyCasesByStatus(status.name).map { it.toDomain() }

    override suspend fun getAllDiscrepancyCases(limit: Int, offset: Int): List<DiscrepancyCase> =
        operationsDao.getAllDiscrepancyCasesPaged(limit, offset)
            .map { it.toDomain() }

    // ── ExportJob ───────────────────────────────────────────────────────────────

    override suspend fun createExportJob(job: ExportJob): ExportJob {
        operationsDao.insertExportJob(job.toEntity())
        return job
    }

    override suspend fun getExportJobById(id: String): ExportJob? =
        operationsDao.getExportJobById(id)?.toDomain()

    override suspend fun getAllExportJobs(limit: Int, offset: Int): List<ExportJob> =
        operationsDao.getAllExportJobsPaged(limit, offset).map { it.toDomain() }

    // ── BackupArchive ───────────────────────────────────────────────────────────

    override suspend fun createBackupArchive(archive: BackupArchive): BackupArchive {
        operationsDao.insertBackupArchive(archive.toEntity())
        return archive
    }

    override suspend fun updateBackupArchive(archive: BackupArchive) {
        operationsDao.updateBackupArchive(archive.toEntity())
    }

    override suspend fun getBackupArchiveById(id: String): BackupArchive? =
        operationsDao.getBackupArchiveById(id)?.toDomain()

    override suspend fun getAllBackupArchives(): List<BackupArchive> =
        operationsDao.getAllBackupArchives().map { it.toDomain() }

    // ── RestoreRun ──────────────────────────────────────────────────────────────

    override suspend fun createRestoreRun(run: RestoreRun): RestoreRun {
        operationsDao.insertRestoreRun(run.toEntity())
        return run
    }

    override suspend fun updateRestoreRun(run: RestoreRun) {
        operationsDao.updateRestoreRun(run.toEntity())
    }

    override suspend fun getRestoreRunById(id: String): RestoreRun? =
        operationsDao.getRestoreRunById(id)?.toDomain()

    // ── MaintenanceJobRun ───────────────────────────────────────────────────────

    override suspend fun createMaintenanceJobRun(run: MaintenanceJobRun): MaintenanceJobRun {
        operationsDao.insertMaintenanceJobRun(run.toEntity())
        return run
    }

    override suspend fun updateMaintenanceJobRun(run: MaintenanceJobRun) {
        operationsDao.updateMaintenanceJobRun(run.toEntity())
    }

    // ── DataIntegrityIssue ──────────────────────────────────────────────────────

    override suspend fun createDataIntegrityIssue(issue: DataIntegrityIssue) {
        operationsDao.insertDataIntegrityIssue(issue.toEntity())
    }

    override suspend fun getUnresolvedIntegrityIssues(): List<DataIntegrityIssue> =
        operationsDao.getUnresolvedDataIntegrityIssues().map { it.toDomain() }

    // Settlement payment-status update idempotency
    private val appliedSettlementUpdates = mutableSetOf<String>()

    override suspend fun hasAppliedSettlementUpdate(idempotencyKey: String): Boolean {
        return operationsDao.countAppliedSettlementUpdates(idempotencyKey) > 0 ||
            appliedSettlementUpdates.contains(idempotencyKey)
    }

    override suspend fun saveAppliedSettlementUpdate(
        idempotencyKey: String,
        settlementRowId: String,
        paymentId: String,
        appliedAt: java.time.Instant
    ) {
        operationsDao.insertAppliedSettlementUpdate(
            com.learnmart.app.data.local.entity.SettlementPaymentUpdateEntity(
                idempotencyKey = idempotencyKey,
                settlementRowId = settlementRowId,
                paymentId = paymentId,
                appliedAt = appliedAt.toEpochMilli()
            )
        )
        appliedSettlementUpdates.add(idempotencyKey)
    }

    // ── Entity -> Domain Mappers ────────────────────────────────────────────────

    private fun ImportJobEntity.toDomain() = ImportJob(
        id = id,
        fileName = fileName,
        fileType = fileType,
        fileSizeBytes = fileSizeBytes,
        status = ImportJobStatus.valueOf(status),
        totalRows = totalRows,
        validRows = validRows,
        errorRows = errorRows,
        errorDetails = errorDetails,
        importedBy = importedBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun SettlementImportBatchEntity.toDomain() = SettlementImportBatch(
        id = id,
        importJobId = importJobId,
        batchIdentifier = batchIdentifier,
        signatureValid = signatureValid,
        rowCount = rowCount,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun SettlementImportRowEntity.toDomain() = SettlementImportRow(
        id = id,
        batchId = batchId,
        externalRowId = externalRowId,
        paymentReference = paymentReference,
        amount = BigDecimal(amount),
        tenderType = tenderType,
        status = status,
        transactionDate = transactionDate?.let { Instant.ofEpochMilli(it) },
        rawData = rawData,
        isValid = isValid,
        validationErrors = validationErrors,
        isDuplicate = isDuplicate
    )

    private fun ReconciliationRunEntity.toDomain() = ReconciliationRun(
        id = id,
        batchId = batchId,
        matchedCount = matchedCount,
        unmatchedCount = unmatchedCount,
        duplicateCount = duplicateCount,
        discrepancyCount = discrepancyCount,
        runBy = runBy,
        runAt = Instant.ofEpochMilli(runAt),
        status = status
    )

    private fun ReconciliationMatchEntity.toDomain() = ReconciliationMatch(
        id = id,
        reconciliationRunId = reconciliationRunId,
        settlementRowId = settlementRowId,
        paymentRecordId = paymentRecordId,
        matchType = matchType,
        matchedAt = Instant.ofEpochMilli(matchedAt)
    )

    private fun DiscrepancyCaseEntity.toDomain() = DiscrepancyCase(
        id = id,
        reconciliationRunId = reconciliationRunId,
        settlementRowId = settlementRowId,
        paymentRecordId = paymentRecordId,
        discrepancyType = discrepancyType,
        description = description,
        notes = notes,
        status = DiscrepancyCaseStatus.valueOf(status),
        resolvedBy = resolvedBy,
        resolvedAt = resolvedAt?.let { Instant.ofEpochMilli(it) },
        resolutionNote = resolutionNote,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun ExportJobEntity.toDomain() = ExportJob(
        id = id,
        exportType = exportType,
        format = format,
        fileName = fileName,
        filePath = filePath,
        status = status,
        recordCount = recordCount,
        exportedBy = exportedBy,
        exportedAt = Instant.ofEpochMilli(exportedAt),
        checksum = checksum
    )

    private fun BackupArchiveEntity.toDomain() = BackupArchive(
        id = id,
        status = BackupStatus.valueOf(status),
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        backupTimestamp = Instant.ofEpochMilli(backupTimestamp),
        filePath = filePath,
        fileSizeBytes = fileSizeBytes,
        checksumManifest = checksumManifest,
        encryptionMethod = encryptionMethod,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun RestoreRunEntity.toDomain() = RestoreRun(
        id = id,
        backupArchiveId = backupArchiveId,
        status = status,
        restoredBy = restoredBy,
        startedAt = Instant.ofEpochMilli(startedAt),
        completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
        errorMessage = errorMessage
    )

    private fun MaintenanceJobRunEntity.toDomain() = MaintenanceJobRun(
        id = id,
        jobType = jobType,
        status = status,
        itemsProcessed = itemsProcessed,
        errorCount = errorCount,
        startedAt = Instant.ofEpochMilli(startedAt),
        completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
        notes = notes
    )

    private fun DataIntegrityIssueEntity.toDomain() = DataIntegrityIssue(
        id = id,
        entityType = entityType,
        entityId = entityId,
        issueType = issueType,
        description = description,
        severity = severity,
        detectedAt = Instant.ofEpochMilli(detectedAt),
        resolvedAt = resolvedAt?.let { Instant.ofEpochMilli(it) },
        resolvedBy = resolvedBy
    )

    // ── Domain -> Entity Mappers ────────────────────────────────────────────────

    private fun ImportJob.toEntity() = ImportJobEntity(
        id = id,
        fileName = fileName,
        fileType = fileType,
        fileSizeBytes = fileSizeBytes,
        status = status.name,
        totalRows = totalRows,
        validRows = validRows,
        errorRows = errorRows,
        errorDetails = errorDetails,
        importedBy = importedBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun SettlementImportBatch.toEntity() = SettlementImportBatchEntity(
        id = id,
        importJobId = importJobId,
        batchIdentifier = batchIdentifier,
        signatureValid = signatureValid,
        rowCount = rowCount,
        createdAt = createdAt.toEpochMilli()
    )

    private fun SettlementImportRow.toEntity() = SettlementImportRowEntity(
        id = id,
        batchId = batchId,
        externalRowId = externalRowId,
        paymentReference = paymentReference,
        amount = amount.toPlainString(),
        tenderType = tenderType,
        status = status,
        transactionDate = transactionDate?.toEpochMilli(),
        rawData = rawData,
        isValid = isValid,
        validationErrors = validationErrors,
        isDuplicate = isDuplicate
    )

    private fun ReconciliationRun.toEntity() = ReconciliationRunEntity(
        id = id,
        batchId = batchId,
        matchedCount = matchedCount,
        unmatchedCount = unmatchedCount,
        duplicateCount = duplicateCount,
        discrepancyCount = discrepancyCount,
        runBy = runBy,
        runAt = runAt.toEpochMilli(),
        status = status
    )

    private fun ReconciliationMatch.toEntity() = ReconciliationMatchEntity(
        id = id,
        reconciliationRunId = reconciliationRunId,
        settlementRowId = settlementRowId,
        paymentRecordId = paymentRecordId,
        matchType = matchType,
        matchedAt = matchedAt.toEpochMilli()
    )

    private fun DiscrepancyCase.toEntity() = DiscrepancyCaseEntity(
        id = id,
        reconciliationRunId = reconciliationRunId,
        settlementRowId = settlementRowId,
        paymentRecordId = paymentRecordId,
        discrepancyType = discrepancyType,
        description = description,
        notes = notes,
        status = status.name,
        resolvedBy = resolvedBy,
        resolvedAt = resolvedAt?.toEpochMilli(),
        resolutionNote = resolutionNote,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun ExportJob.toEntity() = ExportJobEntity(
        id = id,
        exportType = exportType,
        format = format,
        fileName = fileName,
        filePath = filePath,
        status = status,
        recordCount = recordCount,
        exportedBy = exportedBy,
        exportedAt = exportedAt.toEpochMilli(),
        checksum = checksum
    )

    private fun BackupArchive.toEntity() = BackupArchiveEntity(
        id = id,
        status = status.name,
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        backupTimestamp = backupTimestamp.toEpochMilli(),
        filePath = filePath,
        fileSizeBytes = fileSizeBytes,
        checksumManifest = checksumManifest,
        encryptionMethod = encryptionMethod,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun RestoreRun.toEntity() = RestoreRunEntity(
        id = id,
        backupArchiveId = backupArchiveId,
        status = status,
        restoredBy = restoredBy,
        startedAt = startedAt.toEpochMilli(),
        completedAt = completedAt?.toEpochMilli(),
        errorMessage = errorMessage
    )

    private fun MaintenanceJobRun.toEntity() = MaintenanceJobRunEntity(
        id = id,
        jobType = jobType,
        status = status,
        itemsProcessed = itemsProcessed,
        errorCount = errorCount,
        startedAt = startedAt.toEpochMilli(),
        completedAt = completedAt?.toEpochMilli(),
        notes = notes
    )

    private fun DataIntegrityIssue.toEntity() = DataIntegrityIssueEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        issueType = issueType,
        description = description,
        severity = severity,
        detectedAt = detectedAt.toEpochMilli(),
        resolvedAt = resolvedAt?.toEpochMilli(),
        resolvedBy = resolvedBy
    )
}
