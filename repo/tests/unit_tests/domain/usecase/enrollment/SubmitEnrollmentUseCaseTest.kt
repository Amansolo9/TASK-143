package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.data.local.dao.BlacklistDao
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SubmitEnrollmentUseCaseTest {
    private lateinit var enrollmentRepository: EnrollmentRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var blacklistDao: BlacklistDao
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: SubmitEnrollmentUseCase

    private val openClass = ClassOffering(
        id = "class-1", courseId = "course-1", title = "Fall 2025",
        description = "Fall semester offering", status = ClassOfferingStatus.OPEN,
        hardCapacity = 25, enrolledCount = 10, waitlistEnabled = true,
        scheduleStart = Instant.now(), scheduleEnd = Instant.now().plusSeconds(86400L * 90),
        location = "Room 101", createdBy = "admin",
        createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
    )

    @Before
    fun setUp() {
        enrollmentRepository = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        blacklistDao = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        // Create a session so getCurrentUserId returns non-null
        useCase = SubmitEnrollmentUseCase(enrollmentRepository, courseRepository, policyRepository, auditRepository, blacklistDao, sessionManager)

        coEvery { policyRepository.getPolicyLongValue(any(), any(), any()) } returns 48L
        coEvery { policyRepository.getPolicyBoolValue(any(), any(), any()) } returns true
        coEvery { blacklistDao.isBlacklisted(any()) } returns 0
    }

    @Test
    fun `unauthenticated user gets permission error`() = runTest {
        // sessionManager has no session -> getCurrentUserId() returns null
        val result = useCase("class-1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `closed class returns validation error`() = runTest {
        // We need a userId - create session first
        sessionManager.createSession("learner-1")
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass.copy(status = ClassOfferingStatus.CLOSED)
        val result = useCase("class-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `missing class returns not found`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { courseRepository.getClassOfferingById("missing") } returns null
        val result = useCase("missing")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `duplicate active request returns conflict`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass
        coEvery { enrollmentRepository.countActivePendingRequests("learner-1", "class-1") } returns 1
        val result = useCase("class-1")
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }

    @Test
    fun `already enrolled returns conflict`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass
        coEvery { enrollmentRepository.countActivePendingRequests(any(), any()) } returns 0
        coEvery { enrollmentRepository.getActiveEnrollment("learner-1", "class-1") } returns mockk()
        val result = useCase("class-1")
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }

    @Test
    fun `blacklisted learner is rejected`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass
        coEvery { enrollmentRepository.countActivePendingRequests(any(), any()) } returns 0
        coEvery { enrollmentRepository.getActiveEnrollment(any(), any()) } returns null
        coEvery { blacklistDao.isBlacklisted("learner-1") } returns 1
        val result = useCase("class-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `over-capacity with exceptions enabled routes to capacity exception`() = runTest {
        sessionManager.createSession("learner-1")
        val fullClass = openClass.copy(enrolledCount = 25)
        coEvery { courseRepository.getClassOfferingById("class-1") } returns fullClass
        coEvery { enrollmentRepository.countActivePendingRequests(any(), any()) } returns 0
        coEvery { enrollmentRepository.getActiveEnrollment(any(), any()) } returns null
        coEvery { enrollmentRepository.countActiveEnrollmentsForClass("class-1") } returns 25
        coEvery { policyRepository.getPolicyBoolValue(PolicyType.ENROLLMENT, "allow_over_capacity_exceptions", true) } returns true

        val result = useCase("class-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val req = (result as AppResult.Success).data
        assertThat(req.status).isEqualTo(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION)
    }
}
