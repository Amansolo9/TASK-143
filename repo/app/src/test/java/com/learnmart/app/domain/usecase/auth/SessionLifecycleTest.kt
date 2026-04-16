package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SessionLifecycleTest {
    private lateinit var auditRepository: AuditRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var validateSessionUseCase: ValidateSessionUseCase

    @Before
    fun setUp() {
        auditRepository = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        logoutUseCase = LogoutUseCase(sessionManager, auditRepository)
        validateSessionUseCase = ValidateSessionUseCase(sessionManager, auditRepository)
    }

    @Test
    fun `logout returns success`() = runTest {
        val result = logoutUseCase()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `logout logs audit event`() = runTest {
        logoutUseCase()
        coVerify { auditRepository.logEvent(match { it.actionType == AuditActionType.LOGOUT }) }
    }

    @Test
    fun `validate session without active session returns false`() = runTest {
        val isValid = validateSessionUseCase()
        assertThat(isValid).isFalse()
    }

    @Test
    fun `validate session does not log expired event when no user`() = runTest {
        // No session = getCurrentUserId() returns null, so no audit event
        validateSessionUseCase()
        coVerify(exactly = 0) { auditRepository.logEvent(any()) }
    }
}
