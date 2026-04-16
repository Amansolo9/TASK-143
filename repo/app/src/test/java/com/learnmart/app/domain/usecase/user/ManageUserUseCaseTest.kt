package com.learnmart.app.domain.usecase.user

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ManageUserUseCaseTest {
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageUserUseCase

    private val testUser = User("u1", "testuser", "Test", CredentialType.PASSWORD, UserStatus.ACTIVE, 0, null, null, Instant.now(), Instant.now(), 1)

    @Before
    fun setUp() {
        userRepository = mockk(relaxed = true)
        roleRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageUserUseCase(userRepository, roleRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `getAllActiveUsers requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.getAllActiveUsers()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getUserById requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.getUserById("u1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `unlockUser requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.unlockUser("u1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `unlockUser rejects non-locked user`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        coEvery { userRepository.getUserById("u1") } returns testUser // status=ACTIVE
        val result = useCase.unlockUser("u1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `disableUser requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.disableUser("u1", "reason")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `assignRole requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.assignRole("u1", RoleType.LEARNER)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `searchUsers requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.searchUsers("test")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getRolesForUser requires USER_MANAGE`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns false
        val result = useCase.getRolesForUser("u1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `assignRole succeeds with permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.USER_MANAGE) } returns true
        sessionManager.createSession("admin-1")
        coEvery { userRepository.getUserById("u1") } returns testUser
        val result = useCase.assignRole("u1", RoleType.INSTRUCTOR)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { roleRepository.assignRole("u1", RoleType.INSTRUCTOR, any()) }
    }
}
