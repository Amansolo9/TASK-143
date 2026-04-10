package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val userId = sessionManager.getCurrentUserId()
        val sessionId = sessionManager.getCurrentSessionId()

        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = userId,
                actorUsername = null,
                actionType = AuditActionType.LOGOUT,
                targetEntityType = "Session",
                targetEntityId = sessionId,
                beforeSummary = null,
                afterSummary = null,
                reason = "User initiated logout",
                sessionId = sessionId,
                outcome = AuditOutcome.SUCCESS,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )

        sessionManager.terminateSession("LOGOUT")
        return AppResult.Success(Unit)
    }
}
