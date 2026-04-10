package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.*
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class ExportUseCase @Inject constructor(
    private val operationsRepository: OperationsRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    suspend fun createExport(
        exportType: String,
        format: String,
        data: String, // Serialized CSV or JSON
        recordCount: Int
    ): AppResult<ExportJob> {
        if (!checkPermission.hasPermission(Permission.EXPORT_MANAGE)) {
            return AppResult.PermissionError("Requires export.manage")
        }

        if (format !in listOf("csv", "json")) {
            return AppResult.ValidationError(globalErrors = listOf("Format must be csv or json"))
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"
        val fileName = "${exportType}_${now.toEpochMilli()}.$format"
        val checksum = java.security.MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray()).joinToString("") { "%02x".format(it) }

        val job = ExportJob(
            id = IdGenerator.newId(), exportType = exportType, format = format,
            fileName = fileName, filePath = "exports/$fileName",
            status = "COMPLETED", recordCount = recordCount,
            exportedBy = userId, exportedAt = now, checksum = checksum
        )
        operationsRepository.createExportJob(job)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(), actorId = userId, actorUsername = null,
            actionType = AuditActionType.EXPORT_COMPLETED,
            targetEntityType = "ExportJob", targetEntityId = job.id,
            beforeSummary = null, afterSummary = "type=$exportType, format=$format, records=$recordCount",
            reason = null, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        return AppResult.Success(job)
    }

    suspend fun getAllExports(limit: Int, offset: Int): List<ExportJob> =
        operationsRepository.getAllExportJobs(limit, offset)
}
