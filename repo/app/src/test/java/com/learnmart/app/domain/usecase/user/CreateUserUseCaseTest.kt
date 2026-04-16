package com.learnmart.app.domain.usecase.user

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.CredentialManager
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class CreateUserUseCaseTest {
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var credentialManager: CredentialManager
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: CreateUserUseCase

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        roleRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        credentialManager = CredentialManager()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = CreateUserUseCase(userRepository, roleRepository, auditRepository, checkPermission, credentialManager, sessionManager)
    }

    @Test
    fun `requires USER_MANAGE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase(CreateUserRequest("user", "User", "password123", CredentialType.PASSWORD, RoleType.LEARNER))
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `blank username rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        val result = useCase(CreateUserRequest("", "User", "password123", CredentialType.PASSWORD, RoleType.LEARNER))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `short password rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        val result = useCase(CreateUserRequest("user", "User", "short", CredentialType.PASSWORD, RoleType.LEARNER))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `duplicate username rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        coEvery { userRepository.getUserByUsername("existing") } returns User(
            "u1", "existing", "Existing", CredentialType.PASSWORD, UserStatus.ACTIVE,
            0, null, null, Instant.now(), Instant.now(), 1
        )
        val result = useCase(CreateUserRequest("existing", "User", "password123", CredentialType.PASSWORD, RoleType.LEARNER))
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }

    @Test
    fun `successful creation returns user and assigns role`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        coEvery { userRepository.getUserByUsername("newuser") } returns null
        coEvery { userRepository.createUser(any(), any(), any(), any()) } returns User(
            "u-new", "newuser", "New User", CredentialType.PASSWORD, UserStatus.ACTIVE,
            0, null, null, Instant.now(), Instant.now(), 1
        )
        val result = useCase(CreateUserRequest("newuser", "New User", "password123", CredentialType.PASSWORD, RoleType.LEARNER))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { roleRepository.assignRole("u-new", RoleType.LEARNER, any()) }
    }

    @Test
    fun `PIN credential validates digits only`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        val result = useCase(CreateUserRequest("user", "User", "abcd", CredentialType.PIN, RoleType.LEARNER))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }
}
