package com.learnmart.app.domain.usecase.audit

import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.StateTransitionLog
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.util.AppResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ViewAuditLogUseCase @Inject constructor(
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase
) {
    suspend fun getEventsPaged(limit: Int, offset: Int): AppResult<List<AuditEvent>> {
        if (!checkPermission.hasPermission(Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError("Requires audit.view permission")
        }
        return AppResult.Success(auditRepository.getEventsPaged(limit, offset))
    }

    suspend fun getEventsByType(actionType: String, limit: Int, offset: Int): AppResult<List<AuditEvent>> {
        if (!checkPermission.hasPermission(Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError()
        }
        return AppResult.Success(auditRepository.getEventsByType(actionType, limit, offset))
    }

    suspend fun getEventsForEntity(entityType: String, entityId: String): AppResult<List<AuditEvent>> {
        if (!checkPermission.hasPermission(Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError()
        }
        return AppResult.Success(auditRepository.getEventsForEntity(entityType, entityId))
    }

    suspend fun getTransitionsForEntity(entityType: String, entityId: String): AppResult<List<StateTransitionLog>> {
        if (!checkPermission.hasPermission(Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError()
        }
        return AppResult.Success(auditRepository.getTransitionsForEntity(entityType, entityId))
    }

    suspend fun getRecentEvents(limit: Int): AppResult<Flow<List<AuditEvent>>> {
        if (!checkPermission.hasPermission(Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError("Requires audit.view permission")
        }
        return AppResult.Success(auditRepository.getRecentEvents(limit))
    }

    suspend fun countAll(): AppResult<Int> {
        if (!checkPermission.hasPermission(Permission.AUDIT_VIEW)) {
            return AppResult.PermissionError("Requires audit.view permission")
        }
        return AppResult.Success(auditRepository.countAll())
    }
}
