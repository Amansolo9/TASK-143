package com.learnmart.app.domain.usecase.course

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ManageCourseUseCaseTest {
    private lateinit var courseRepository: CourseRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageCourseUseCase

    @Before
    fun setUp() {
        courseRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageCourseUseCase(courseRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `createCourse requires CATALOG_MANAGE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CATALOG_MANAGE) } returns false
        val result = useCase.createCourse("Course", "Desc", "C101")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `createCourse rejects blank title`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CATALOG_MANAGE) } returns true
        val result = useCase.createCourse("", "Desc", "C101")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createCourse rejects blank code`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CATALOG_MANAGE) } returns true
        val result = useCase.createCourse("Course", "Desc", "")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createCourse succeeds with valid input`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CATALOG_MANAGE) } returns true
        coEvery { courseRepository.createCourse(any()) } answers { firstArg() }
        val result = useCase.createCourse("Kotlin Fundamentals", "Learn Kotlin", "KT101")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `getCourseById returns NotFound for missing course`() = runTest {
        coEvery { courseRepository.getCourseById("missing") } returns null
        val result = useCase.getCourseById("missing")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `getCourseById returns course when found`() = runTest {
        val course = Course(
            id = "c1", title = "Test", description = "Desc", code = "T101",
            status = CourseStatus.DRAFT, currentVersionId = null,
            createdBy = "admin", createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
        )
        coEvery { courseRepository.getCourseById("c1") } returns course
        val result = useCase.getCourseById("c1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data.code).isEqualTo("T101")
    }
}
