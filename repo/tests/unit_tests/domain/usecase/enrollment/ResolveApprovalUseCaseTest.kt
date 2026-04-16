package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ResolveApprovalUseCaseTest {
    private lateinit var enrollmentRepository: EnrollmentRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ResolveApprovalUseCase

    private val now = Instant.now()

    @Before
    fun setUp() {
        enrollmentRepository = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ResolveApprovalUseCase(enrollmentRepository, courseRepository, policyRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `approve requires ENROLLMENT_REVIEW permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns false
        val result = useCase.approve("task-1", null)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `reject requires ENROLLMENT_REVIEW permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns false
        val result = useCase.reject("task-1", "reason")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `reject requires non-blank notes`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns true
        val result = useCase.reject("task-1", "")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `missing task returns not found`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns true
        coEvery { checkPermission.getCurrentUserRoles() } returns listOf(RoleType.REGISTRAR)
        sessionManager.createSession("registrar-1")
        coEvery { enrollmentRepository.getApprovalTaskById("missing") } returns null
        val result = useCase.approve("missing", null)
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `already resolved task returns conflict`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns true
        coEvery { checkPermission.getCurrentUserRoles() } returns listOf(RoleType.REGISTRAR)
        sessionManager.createSession("registrar-1")
        coEvery { enrollmentRepository.getApprovalTaskById("task-1") } returns EnrollmentApprovalTask(
            id = "task-1", enrollmentRequestId = "req-1", stepDefinitionId = "step-1",
            assignedToUserId = null, assignedToRoleType = RoleType.REGISTRAR,
            status = ApprovalTaskStatus.APPROVED, decidedBy = "someone", decidedAt = now,
            notes = null, createdAt = now, expiresAt = now
        )
        val result = useCase.approve("task-1", null)
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }

    @Test
    fun `approveCapacityException requires ENROLLMENT_OVERRIDE_CAPACITY`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY) } returns false
        val result = useCase.approveCapacityException("task-1", "reason")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }
}
