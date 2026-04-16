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

class ManageClassUseCaseTest {
    private lateinit var courseRepository: CourseRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageClassUseCase

    @Before
    fun setUp() {
        courseRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageClassUseCase(courseRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `createClassOffering requires CLASS_MANAGE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CLASS_MANAGE) } returns false
        val result = useCase.createClassOffering(CreateClassOfferingRequest(
            "course-1", "Fall 2025", "Desc", 25, true,
            Instant.now(), Instant.now().plusSeconds(86400), "Room 101"
        ))
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `createClassOffering rejects blank title`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CLASS_MANAGE) } returns true
        val result = useCase.createClassOffering(CreateClassOfferingRequest(
            "course-1", "", "Desc", 25, true,
            Instant.now(), Instant.now().plusSeconds(86400), "Room 101"
        ))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createClassOffering rejects zero capacity`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CLASS_MANAGE) } returns true
        val result = useCase.createClassOffering(CreateClassOfferingRequest(
            "course-1", "Fall 2025", "Desc", 0, true,
            Instant.now(), Instant.now().plusSeconds(86400), "Room 101"
        ))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createClassOffering succeeds with valid input`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.CLASS_MANAGE) } returns true
        sessionManager.createSession("admin-1")
        coEvery { courseRepository.getCourseById("course-1") } returns Course(
            "course-1", "Test", "Desc", "T101", CourseStatus.PUBLISHED,
            null, "admin", Instant.now(), Instant.now(), 1
        )
        coEvery { courseRepository.createClassOffering(any()) } answers { firstArg() }
        val result = useCase.createClassOffering(CreateClassOfferingRequest(
            "course-1", "Fall 2025", "Fall semester", 25, true,
            Instant.now(), Instant.now().plusSeconds(86400 * 90L), "Room 101"
        ))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }
}
