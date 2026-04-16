package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ManageAssessmentUseCaseTest {
    private lateinit var assessmentRepository: AssessmentRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageAssessmentUseCase

    @Before
    fun setUp() {
        assessmentRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageAssessmentUseCase(assessmentRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `createAssignment requires ASSESSMENT_CREATE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ASSESSMENT_CREATE) } returns false
        val result = useCase.createAssignment(CreateAssignmentRequest(
            "class-1", "Quiz", "Desc", AssessmentType.QUIZ, listOf("q1"), 10,
            Instant.now(), Instant.now().plusSeconds(3600), null, false, false
        ))
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `createAssignment rejects blank title`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ASSESSMENT_CREATE) } returns true
        val result = useCase.createAssignment(CreateAssignmentRequest(
            "class-1", "", "Desc", AssessmentType.QUIZ, listOf("q1"), 10,
            Instant.now(), Instant.now().plusSeconds(3600), null, false, false
        ))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createAssignment rejects end before start`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ASSESSMENT_CREATE) } returns true
        val now = Instant.now()
        val result = useCase.createAssignment(CreateAssignmentRequest(
            "class-1", "Quiz", "Desc", AssessmentType.QUIZ, listOf("q1"), 10,
            now, now.minusSeconds(3600), null, false, false
        ))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `createAssignment succeeds with valid input`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ASSESSMENT_CREATE) } returns true
        sessionManager.createSession("instructor-1")
        coEvery { assessmentRepository.createAssignment(any()) } answers { firstArg() }
        val now = Instant.now()
        val result = useCase.createAssignment(CreateAssignmentRequest(
            "class-1", "Final Quiz", "End of term", AssessmentType.QUIZ, listOf("q1"), 100,
            now, now.plusSeconds(86400), 60, false, false
        ))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `gradeSubjectiveAnswer requires ASSESSMENT_GRADE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ASSESSMENT_GRADE) } returns false
        val result = useCase.gradeSubjectiveAnswer("qi-1", 10, "Good")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `reopenSubmission requires ASSESSMENT_REOPEN permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.ASSESSMENT_REOPEN) } returns false
        val result = useCase.reopenSubmission("sub-1", "reason")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }
}
