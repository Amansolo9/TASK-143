package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class ValidateSessionUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(): Boolean {
        val isValid = sessionManager.isSessionValid()
        if (!isValid && sessionManager.getCurrentUserId() != null) {
            auditRepository.logEvent(
                AuditEvent(
                    id = IdGenerator.newId(),
                    actorId = sessionManager.getCurrentUserId(),
                    actorUsername = null,
                    actionType = AuditActionType.SESSION_EXPIRED,
                    targetEntityType = "Session",
                    targetEntityId = sessionManager.getCurrentSessionId(),
                    beforeSummary = null,
                    afterSummary = null,
                    reason = "Session timed out",
                    sessionId = sessionManager.getCurrentSessionId(),
                    outcome = AuditOutcome.SUCCESS,
                    timestamp = TimeUtils.nowUtc(),
                    metadata = null
                )
            )
        }
        return isValid
    }

    suspend fun refresh(): Boolean = sessionManager.refreshSession()
}
