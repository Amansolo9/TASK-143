package com.learnmart.app.domain.usecase.audit

import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ViewAuditLogUseCaseTest {
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var useCase: ViewAuditLogUseCase

    @Before
    fun setUp() {
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        useCase = ViewAuditLogUseCase(auditRepository, checkPermission)
    }

    @Test
    fun `getEventsPaged requires AUDIT_VIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns false
        val result = useCase.getEventsPaged(50, 0)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getEventsPaged succeeds with permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns true
        coEvery { auditRepository.getEventsPaged(50, 0) } returns emptyList()
        val result = useCase.getEventsPaged(50, 0)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `getRecentEvents requires AUDIT_VIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns false
        val result = useCase.getRecentEvents(10)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getRecentEvents succeeds with permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns true
        coEvery { auditRepository.getRecentEvents(10) } returns flowOf(emptyList())
        val result = useCase.getRecentEvents(10)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `countAll requires AUDIT_VIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns false
        val result = useCase.countAll()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getEventsByType requires AUDIT_VIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns false
        val result = useCase.getEventsByType("LOGIN_SUCCESS", 50, 0)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getEventsForEntity requires AUDIT_VIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.AUDIT_VIEW) } returns false
        val result = useCase.getEventsForEntity("User", "u1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }
}
