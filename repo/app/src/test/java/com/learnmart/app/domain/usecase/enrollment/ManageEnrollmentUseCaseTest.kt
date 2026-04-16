package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ManageEnrollmentUseCaseTest {
    private lateinit var enrollmentRepository: EnrollmentRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var waitlistPromotion: WaitlistPromotionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageEnrollmentUseCase

    @Before
    fun setUp() {
        enrollmentRepository = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        waitlistPromotion = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageEnrollmentUseCase(enrollmentRepository, courseRepository, auditRepository, checkPermission, waitlistPromotion, sessionManager)
    }

    @Test
    fun `getPendingRequests requires ENROLLMENT_REVIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns false
        val result = useCase.getPendingRequests()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getPendingRequests succeeds with permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns true
        coEvery { enrollmentRepository.getPendingRequests() } returns flowOf(emptyList())
        val result = useCase.getPendingRequests()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `getRequestsForClass requires ENROLLMENT_REVIEW`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns false
        val result = useCase.getRequestsForClass("class-1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getRequestsForLearner allows own requests`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns false
        coEvery { enrollmentRepository.getRequestsForLearner("learner-1") } returns emptyList()
        val result = useCase.getRequestsForLearner("learner-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `getRequestsForLearner denies other learner without review permission`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW) } returns false
        val result = useCase.getRequestsForLearner("other-learner")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getMyRequests returns empty without session`() = runTest {
        val result = useCase.getMyRequests()
        assertThat(result).isEmpty()
    }

    @Test
    fun `withdrawEnrollment requires reason`() = runTest {
        coEvery { checkPermission.hasAnyPermission(any(), any()) } returns true
        val result = useCase.withdrawEnrollment("rec-1", "")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }
}
