package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.util.AppResult
import javax.inject.Inject

/**
 * Permission-gated access to operations data.
 * All operations screen data must flow through this use case, not directly from repositories.
 */
class ManageOperationsUseCase @Inject constructor(
    private val operationsRepository: OperationsRepository,
    private val checkPermission: CheckPermissionUseCase
) {
    suspend fun getImportJobs(): AppResult<List<ImportJob>> {
        if (!checkPermission.hasAnyPermission(Permission.IMPORT_MANAGE, Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError("Requires import.manage or audit.view")
        }
        return AppResult.Success(operationsRepository.getAllImportJobs())
    }

    suspend fun getDiscrepancyCases(limit: Int = 50, offset: Int = 0): AppResult<List<DiscrepancyCase>> {
        if (!checkPermission.hasAnyPermission(Permission.PAYMENT_RECONCILE, Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError("Requires payment.reconcile or audit.view")
        }
        return AppResult.Success(operationsRepository.getAllDiscrepancyCases(limit, offset))
    }

    suspend fun getBackupArchives(): AppResult<List<BackupArchive>> {
        if (!checkPermission.hasPermission(Permission.BACKUP_RUN)) {
            return AppResult.PermissionError("Requires backup.run")
        }
        return AppResult.Success(operationsRepository.getAllBackupArchives())
    }

    suspend fun getExportJobs(limit: Int = 50, offset: Int = 0): AppResult<List<ExportJob>> {
        if (!checkPermission.hasAnyPermission(Permission.EXPORT_MANAGE, Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError("Requires export.manage or audit.view")
        }
        return AppResult.Success(operationsRepository.getAllExportJobs(limit, offset))
    }
}
