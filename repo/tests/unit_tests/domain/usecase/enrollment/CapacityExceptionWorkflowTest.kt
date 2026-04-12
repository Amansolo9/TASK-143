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

class CapacityExceptionWorkflowTest {
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
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns true
        coEvery { checkPermission.getCurrentUserRoles() } returns listOf(RoleType.REGISTRAR)
        useCase = ResolveApprovalUseCase(
            enrollmentRepository, courseRepository, policyRepository,
            auditRepository, checkPermission, sessionManager
        )
    }

    @Test
    fun `PENDING_CAPACITY_EXCEPTION status exists in enum`() {
        val status = EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION
        assertThat(status.name).isEqualTo("PENDING_CAPACITY_EXCEPTION")
    }

    @Test
    fun `PENDING_CAPACITY_EXCEPTION can transition to APPROVED`() {
        assertThat(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION.canTransitionTo(
            EnrollmentRequestStatus.APPROVED
        )).isTrue()
    }

    @Test
    fun `PENDING_CAPACITY_EXCEPTION can transition to REJECTED`() {
        assertThat(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION.canTransitionTo(
            EnrollmentRequestStatus.REJECTED
        )).isTrue()
    }

    @Test
    fun `PENDING_CAPACITY_EXCEPTION can transition to WAITLISTED`() {
        assertThat(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION.canTransitionTo(
            EnrollmentRequestStatus.WAITLISTED
        )).isTrue()
    }

    @Test
    fun `PENDING_CAPACITY_EXCEPTION is active`() {
        assertThat(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION.isActive()).isTrue()
    }

    @Test
    fun `SUBMITTED can transition to PENDING_CAPACITY_EXCEPTION`() {
        assertThat(EnrollmentRequestStatus.SUBMITTED.canTransitionTo(
            EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION
        )).isTrue()
    }

    @Test
    fun `approveCapacityException requires ENROLLMENT_OVERRIDE_CAPACITY permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY) } returns false
        val result = useCase.approveCapacityException("task-1", "Reason")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `approveCapacityException requires non-blank reason`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY) } returns true
        val result = useCase.approveCapacityException("task-1", "")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `approveCapacityException rejects if request not PENDING_CAPACITY_EXCEPTION`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY) } returns true
        coEvery { enrollmentRepository.getApprovalTaskById("task-1") } returns EnrollmentApprovalTask(
            id = "task-1", enrollmentRequestId = "req-1", stepDefinitionId = "CAPACITY_EXCEPTION",
            assignedToUserId = null, assignedToRoleType = RoleType.REGISTRAR,
            status = ApprovalTaskStatus.PENDING, decidedBy = null, decidedAt = null,
            notes = null, createdAt = now, expiresAt = now
        )
        coEvery { enrollmentRepository.getRequestById("req-1") } returns EnrollmentRequest(
            id = "req-1", learnerId = "learner-1", classOfferingId = "class-1",
            status = EnrollmentRequestStatus.PENDING_APPROVAL, // Wrong status
            priorityTier = 0, submittedAt = now, expiresAt = now, approvalFlowId = null,
            eligibilitySnapshotId = "snap-1", createdAt = now, updatedAt = now, version = 1
        )

        val result = useCase.approveCapacityException("task-1", "Reason")
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }

    @Test
    fun `approveCapacityException enrolls learner on success`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY) } returns true
        coEvery { enrollmentRepository.getApprovalTaskById("task-1") } returns EnrollmentApprovalTask(
            id = "task-1", enrollmentRequestId = "req-1", stepDefinitionId = "CAPACITY_EXCEPTION",
            assignedToUserId = null, assignedToRoleType = RoleType.REGISTRAR,
            status = ApprovalTaskStatus.PENDING, decidedBy = null, decidedAt = null,
            notes = null, createdAt = now, expiresAt = now
        )
        coEvery { enrollmentRepository.getRequestById("req-1") } returns EnrollmentRequest(
            id = "req-1", learnerId = "learner-1", classOfferingId = "class-1",
            status = EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION,
            priorityTier = 0, submittedAt = now, expiresAt = now, approvalFlowId = null,
            eligibilitySnapshotId = "snap-1", createdAt = now, updatedAt = now, version = 1
        )

        val result = useCase.approveCapacityException("task-1", "Special case approved by dean")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val enrolled = (result as AppResult.Success).data
        assertThat(enrolled.status).isEqualTo(EnrollmentRequestStatus.ENROLLED)

        // Verify enrollment record was created
        coVerify { enrollmentRepository.createEnrollmentRecord(any()) }
        coVerify { courseRepository.adjustEnrolledCount("class-1", 1) }
    }
}
