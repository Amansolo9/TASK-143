package com.learnmart.app.security

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.data.local.entity.SessionEntity
import com.learnmart.app.domain.model.SessionRecord
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao
) {
    private val _currentSession = MutableStateFlow<SessionRecord?>(null)
    val currentSession: StateFlow<SessionRecord?> = _currentSession.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private var sessionTimeoutMinutes: Long = 15

    fun setSessionTimeout(minutes: Long) {
        sessionTimeoutMinutes = minutes
    }

    suspend fun createSession(userId: String): SessionRecord {
        // Terminate any existing active sessions for this user
        sessionDao.terminateAllForUser(userId)

        val now = TimeUtils.nowUtc()
        val expiresAt = TimeUtils.minutesFromNow(sessionTimeoutMinutes)
        val session = SessionRecord(
            id = IdGenerator.newId(),
            userId = userId,
            startedAt = now,
            lastActivityAt = now,
            expiresAt = expiresAt,
            isActive = true,
            terminatedReason = null
        )

        sessionDao.insert(session.toEntity())
        _currentSession.value = session
        _currentUserId.value = userId
        return session
    }

    suspend fun refreshSession(): Boolean {
        val session = _currentSession.value ?: return false
        val now = TimeUtils.nowUtc()

        if (now.isAfter(session.expiresAt)) {
            terminateSession("SESSION_EXPIRED")
            return false
        }

        val newExpiry = TimeUtils.minutesFromNow(sessionTimeoutMinutes)
        sessionDao.refreshSession(
            sessionId = session.id,
            lastActivityAt = now.toEpochMilli(),
            expiresAt = newExpiry.toEpochMilli()
        )
        _currentSession.value = session.copy(
            lastActivityAt = now,
            expiresAt = newExpiry
        )
        return true
    }

    suspend fun isSessionValid(): Boolean {
        val session = _currentSession.value ?: return false
        if (!session.isActive) return false
        if (TimeUtils.nowUtc().isAfter(session.expiresAt)) {
            terminateSession("SESSION_EXPIRED")
            return false
        }
        return true
    }

    suspend fun terminateSession(reason: String) {
        val session = _currentSession.value ?: return
        sessionDao.terminateSession(session.id, reason)
        _currentSession.value = null
        _currentUserId.value = null
    }

    suspend fun expireOldSessions() {
        sessionDao.expireOldSessions(TimeUtils.nowUtc().toEpochMilli())
    }

    fun getCurrentSessionId(): String? = _currentSession.value?.id

    fun getCurrentUserId(): String? = _currentUserId.value

    suspend fun restoreSession(userId: String): Boolean {
        val entity = sessionDao.getActiveSessionForUser(userId) ?: return false
        val session = entity.toDomain()
        if (TimeUtils.nowUtc().isAfter(session.expiresAt)) {
            sessionDao.terminateSession(entity.id, "SESSION_EXPIRED")
            return false
        }
        _currentSession.value = session
        _currentUserId.value = userId
        return true
    }

    private fun SessionRecord.toEntity() = SessionEntity(
        id = id,
        userId = userId,
        startedAt = startedAt.toEpochMilli(),
        lastActivityAt = lastActivityAt.toEpochMilli(),
        expiresAt = expiresAt.toEpochMilli(),
        isActive = isActive,
        terminatedReason = terminatedReason
    )

    private fun SessionEntity.toDomain() = SessionRecord(
        id = id,
        userId = userId,
        startedAt = Instant.ofEpochMilli(startedAt),
        lastActivityAt = Instant.ofEpochMilli(lastActivityAt),
        expiresAt = Instant.ofEpochMilli(expiresAt),
        isActive = isActive,
        terminatedReason = terminatedReason
    )
}
