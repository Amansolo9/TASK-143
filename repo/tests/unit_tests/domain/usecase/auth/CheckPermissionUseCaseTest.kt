package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class CheckPermissionUseCaseTest {
    private lateinit var roleRepository: RoleRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: CheckPermissionUseCase

    @Before
    fun setUp() {
        roleRepository = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = CheckPermissionUseCase(roleRepository, sessionManager)
    }

    @Test
    fun `hasPermission returns false without session`() = runTest {
        val result = useCase.hasPermission(Permission.CATALOG_MANAGE)
        assertThat(result).isFalse()
    }

    @Test
    fun `hasPermission returns true when role has permission`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { roleRepository.userHasPermission("user-1", Permission.CATALOG_MANAGE) } returns true
        val result = useCase.hasPermission(Permission.CATALOG_MANAGE)
        assertThat(result).isTrue()
    }

    @Test
    fun `hasAnyPermission returns true when at least one matches`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { roleRepository.userHasPermission("user-1", Permission.CATALOG_MANAGE) } returns false
        coEvery { roleRepository.userHasPermission("user-1", Permission.AUDIT_VIEW) } returns true
        val result = useCase.hasAnyPermission(Permission.CATALOG_MANAGE, Permission.AUDIT_VIEW)
        assertThat(result).isTrue()
    }

    @Test
    fun `hasAllPermissions returns false when one is missing`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { roleRepository.userHasPermission("user-1", Permission.CATALOG_MANAGE) } returns true
        coEvery { roleRepository.userHasPermission("user-1", Permission.AUDIT_VIEW) } returns false
        val result = useCase.hasAllPermissions(Permission.CATALOG_MANAGE, Permission.AUDIT_VIEW)
        assertThat(result).isFalse()
    }

    @Test
    fun `getCurrentUserRoles returns empty without session`() = runTest {
        val roles = useCase.getCurrentUserRoles()
        assertThat(roles).isEmpty()
    }

    @Test
    fun `getCurrentUserRoles returns roles when session exists`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { roleRepository.getRoleTypesForUser("user-1") } returns listOf(RoleType.ADMINISTRATOR)
        val roles = useCase.getCurrentUserRoles()
        assertThat(roles).containsExactly(RoleType.ADMINISTRATOR)
    }

    @Test
    fun `requirePermission denies when permission missing`() = runTest {
        val result = useCase.requirePermission(Permission.USER_MANAGE) {
            AppResult.Success("should not reach")
        }
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `requirePermission executes block when permission present`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { roleRepository.userHasPermission("user-1", Permission.USER_MANAGE) } returns true
        val result = useCase.requirePermission(Permission.USER_MANAGE) {
            AppResult.Success("allowed")
        }
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).isEqualTo("allowed")
    }
}
