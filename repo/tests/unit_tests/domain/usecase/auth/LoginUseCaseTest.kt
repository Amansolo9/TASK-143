package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class LoginUseCaseTest {
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: LoginUseCase

    private val testUser = User(
        id = "user-1", username = "admin", displayName = "Admin",
        credentialType = CredentialType.PASSWORD, status = UserStatus.ACTIVE,
        failedLoginAttempts = 0, lockedUntil = null, lastLoginAt = null,
        createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
    )

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        roleRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))

        coEvery { policyRepository.getPolicyIntValue(any(), any(), any()) } returns 5
        coEvery { policyRepository.getPolicyLongValue(any(), any(), any()) } returns 15L

        useCase = LoginUseCase(userRepository, roleRepository, policyRepository, auditRepository, sessionManager)
    }

    @Test
    fun `blank username returns validation error`() = runTest {
        val result = useCase(LoginRequest("", "password"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `blank credential returns validation error`() = runTest {
        val result = useCase(LoginRequest("admin", ""))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `unknown username returns validation error`() = runTest {
        coEvery { userRepository.getUserByUsername("unknown") } returns null
        val result = useCase(LoginRequest("unknown", "password"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `disabled user returns validation error`() = runTest {
        coEvery { userRepository.getUserByUsername("admin") } returns testUser.copy(status = UserStatus.DISABLED)
        val result = useCase(LoginRequest("admin", "password"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `locked user with active lock returns validation error`() = runTest {
        val lockedUser = testUser.copy(
            status = UserStatus.LOCKED,
            lockedUntil = Instant.now().plusSeconds(3600)
        )
        coEvery { userRepository.getUserByUsername("admin") } returns lockedUser
        val result = useCase(LoginRequest("admin", "password"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `wrong password increments failed attempts`() = runTest {
        coEvery { userRepository.getUserByUsername("admin") } returns testUser
        coEvery { userRepository.verifyCredential("user-1", "wrong") } returns false

        val result = useCase(LoginRequest("admin", "wrong"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
        coVerify { userRepository.updateLoginAttempts("user-1", 1, any(), 1) }
    }

    @Test
    fun `successful login returns LoginResult`() = runTest {
        coEvery { userRepository.getUserByUsername("admin") } returns testUser
        coEvery { userRepository.verifyCredential("user-1", "admin1234") } returns true
        coEvery { userRepository.getUserById("user-1") } returns testUser
        coEvery { roleRepository.getRoleTypesForUser("user-1") } returns listOf(RoleType.ADMINISTRATOR)

        val result = useCase(LoginRequest("admin", "admin1234"))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val data = (result as AppResult.Success).data
        assertThat(data.user.username).isEqualTo("admin")
        assertThat(data.roles).contains(RoleType.ADMINISTRATOR)
    }

    @Test
    fun `successful login records audit event`() = runTest {
        coEvery { userRepository.getUserByUsername("admin") } returns testUser
        coEvery { userRepository.verifyCredential("user-1", "admin1234") } returns true
        coEvery { userRepository.getUserById("user-1") } returns testUser
        coEvery { roleRepository.getRoleTypesForUser("user-1") } returns listOf(RoleType.ADMINISTRATOR)

        useCase(LoginRequest("admin", "admin1234"))
        coVerify { auditRepository.logEvent(match { it.actionType == AuditActionType.LOGIN_SUCCESS }) }
    }

    @Test
    fun `failed login records audit event`() = runTest {
        coEvery { userRepository.getUserByUsername("admin") } returns testUser
        coEvery { userRepository.verifyCredential("user-1", "wrong") } returns false

        useCase(LoginRequest("admin", "wrong"))
        coVerify { auditRepository.logEvent(match { it.actionType == AuditActionType.LOGIN_FAILURE }) }
    }
}
