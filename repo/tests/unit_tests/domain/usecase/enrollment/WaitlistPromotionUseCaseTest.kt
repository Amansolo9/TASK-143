package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class WaitlistPromotionUseCaseTest {
    private lateinit var enrollmentRepository: EnrollmentRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var useCase: WaitlistPromotionUseCase

    private val openClass = ClassOffering(
        "class-1", "course-1", "Fall", "Desc", ClassOfferingStatus.OPEN,
        25, 24, true, Instant.now(), Instant.now().plusSeconds(86400L * 90),
        "Room 101", "admin", Instant.now(), Instant.now(), 1
    )

    @Before
    fun setUp() {
        enrollmentRepository = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        coEvery { policyRepository.getPolicyLongValue(any(), any(), any()) } returns 24L
        useCase = WaitlistPromotionUseCase(enrollmentRepository, courseRepository, policyRepository, auditRepository)
    }

    @Test
    fun `missing class returns not found`() = runTest {
        coEvery { courseRepository.getClassOfferingById("missing") } returns null
        val result = useCase.promoteNextIfAvailable("missing")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `class with waitlist disabled returns null`() = runTest {
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass.copy(waitlistEnabled = false)
        val result = useCase.promoteNextIfAvailable("class-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).isNull()
    }

    @Test
    fun `class still at capacity returns null`() = runTest {
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass.copy(enrolledCount = 25)
        coEvery { enrollmentRepository.countActiveEnrollmentsForClass("class-1") } returns 25
        val result = useCase.promoteNextIfAvailable("class-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).isNull()
    }

    @Test
    fun `no waitlist entries returns null`() = runTest {
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass
        coEvery { enrollmentRepository.countActiveEnrollmentsForClass("class-1") } returns 20
        coEvery { enrollmentRepository.getNextWaitlistEntry("class-1") } returns null
        val result = useCase.promoteNextIfAvailable("class-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).isNull()
    }

    @Test
    fun `promotes next waitlist entry when seat available`() = runTest {
        coEvery { courseRepository.getClassOfferingById("class-1") } returns openClass
        coEvery { enrollmentRepository.countActiveEnrollmentsForClass("class-1") } returns 20
        val entry = WaitlistEntry(
            "wl-1", "learner-1", "class-1", "req-1", 1, 0,
            Instant.now(), null, null, WaitlistStatus.ACTIVE
        )
        coEvery { enrollmentRepository.getNextWaitlistEntry("class-1") } returns entry
        val result = useCase.promoteNextIfAvailable("class-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data).isNotNull()
    }
}
