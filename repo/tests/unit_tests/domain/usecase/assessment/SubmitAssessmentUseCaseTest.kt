package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SubmitAssessmentUseCaseTest {
    private lateinit var assessmentRepository: AssessmentRepository
    private lateinit var autoGradingEngine: AutoGradingEngine
    private lateinit var similarityEngine: SimilarityEngine
    private lateinit var auditRepository: AuditRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: SubmitAssessmentUseCase

    private val now = Instant.now()
    private val assignment = Assignment(
        id = "assess-1", classOfferingId = "class-1", title = "Quiz 1",
        description = "Test quiz", assessmentType = AssessmentType.QUIZ,
        questionBankId = null, questionIds = listOf("q1"), totalPoints = 10,
        releaseStart = now.minusSeconds(3600), releaseEnd = now.plusSeconds(3600),
        timeLimitMinutes = 30, allowLateSubmission = false, allowResubmission = false,
        createdBy = "instructor", createdAt = now, updatedAt = now, version = 1
    )

    @Before
    fun setUp() {
        assessmentRepository = mockk(relaxed = true)
        autoGradingEngine = mockk(relaxed = true)
        similarityEngine = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = SubmitAssessmentUseCase(assessmentRepository, autoGradingEngine, similarityEngine, auditRepository, sessionManager)
    }

    @Test
    fun `unauthenticated user gets permission error`() = runTest {
        val result = useCase.startSubmission("assess-1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `missing assessment returns not found`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { assessmentRepository.getAssignmentById("missing") } returns null
        val result = useCase.startSubmission("missing")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `assessment not yet released returns validation error`() = runTest {
        sessionManager.createSession("learner-1")
        val futureAssignment = assignment.copy(releaseStart = now.plusSeconds(7200))
        coEvery { assessmentRepository.getAssignmentById("assess-1") } returns futureAssignment
        val result = useCase.startSubmission("assess-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `closed window without late submission returns validation error`() = runTest {
        sessionManager.createSession("learner-1")
        val pastAssignment = assignment.copy(
            releaseStart = now.minusSeconds(7200),
            releaseEnd = now.minusSeconds(3600),
            allowLateSubmission = false
        )
        coEvery { assessmentRepository.getAssignmentById("assess-1") } returns pastAssignment
        val result = useCase.startSubmission("assess-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `startSubmission succeeds within release window`() = runTest {
        sessionManager.createSession("learner-1")
        coEvery { assessmentRepository.getAssignmentById("assess-1") } returns assignment
        coEvery { assessmentRepository.getSubmissionByAssessmentAndLearner(any(), any()) } returns null
        coEvery { assessmentRepository.createSubmission(any()) } answers { firstArg() }

        val result = useCase.startSubmission("assess-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val submission = (result as AppResult.Success).data
        assertThat(submission.status).isEqualTo(SubmissionStatus.IN_PROGRESS)
    }

    @Test
    fun `existing in-progress submission is resumed`() = runTest {
        sessionManager.createSession("learner-1")
        val existing = Submission(
            id = "sub-1", assessmentId = "assess-1", learnerId = "learner-1",
            status = SubmissionStatus.IN_PROGRESS, startedAt = now, submittedAt = null,
            totalScore = null, maxScore = 10, gradePercentage = null,
            gradedBy = null, gradedAt = null, finalizedAt = null,
            versionNumber = 1, createdAt = now, updatedAt = now, version = 1
        )
        coEvery { assessmentRepository.getAssignmentById("assess-1") } returns assignment
        coEvery { assessmentRepository.getSubmissionByAssessmentAndLearner("assess-1", "learner-1") } returns existing

        val result = useCase.startSubmission("assess-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data.id).isEqualTo("sub-1")
    }

    @Test
    fun `already submitted without resubmission returns conflict`() = runTest {
        sessionManager.createSession("learner-1")
        val submitted = Submission(
            id = "sub-1", assessmentId = "assess-1", learnerId = "learner-1",
            status = SubmissionStatus.SUBMITTED, startedAt = now, submittedAt = now,
            totalScore = null, maxScore = 10, gradePercentage = null,
            gradedBy = null, gradedAt = null, finalizedAt = null,
            versionNumber = 1, createdAt = now, updatedAt = now, version = 1
        )
        coEvery { assessmentRepository.getAssignmentById("assess-1") } returns assignment
        coEvery { assessmentRepository.getSubmissionByAssessmentAndLearner("assess-1", "learner-1") } returns submitted

        val result = useCase.startSubmission("assess-1")
        assertThat(result).isInstanceOf(AppResult.ConflictError::class.java)
    }
}
