package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AssessmentRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AutoGradingEngineTest {

    private lateinit var assessmentRepository: AssessmentRepository
    private lateinit var engine: AutoGradingEngine

    @Before
    fun setUp() {
        assessmentRepository = mockk(relaxed = true)
        engine = AutoGradingEngine(assessmentRepository)
    }

    @Test
    fun `grades correct objective answer as correct`() = runTest {
        val question = Question(
            id = "q1", questionBankId = "bank1", questionType = QuestionType.OBJECTIVE,
            difficulty = DifficultyLevel.EASY, questionText = "What is 2+2?",
            explanation = null, points = 10, tagIds = emptyList(),
            createdBy = "instructor", createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
        )
        val choices = listOf(
            QuestionChoice("c1", "q1", "3", false, 1),
            QuestionChoice("c2", "q1", "4", true, 2),
            QuestionChoice("c3", "q1", "5", false, 3)
        )
        val answer = SubmissionAnswer(
            id = "a1", submissionId = "sub1", questionId = "q1",
            answerText = null, selectedChoiceIds = listOf("c2"),
            score = null, maxScore = 10, isAutoGraded = false,
            feedback = null, submittedAt = Instant.now()
        )

        coEvery { assessmentRepository.getAnswersForSubmission("sub1") } returns listOf(answer)
        coEvery { assessmentRepository.getQuestionById("q1") } returns question
        coEvery { assessmentRepository.getChoicesForQuestion("q1") } returns choices

        val result = engine.gradeObjectiveAnswers("sub1")

        assertThat(result.objectiveScore).isEqualTo(10)
        assertThat(result.gradedAnswers).isEqualTo(1)
        assertThat(result.wrongAnswerQuestionIds).isEmpty()
    }

    @Test
    fun `grades incorrect objective answer as wrong`() = runTest {
        val question = Question(
            id = "q1", questionBankId = "bank1", questionType = QuestionType.OBJECTIVE,
            difficulty = DifficultyLevel.MEDIUM, questionText = "Capital of France?",
            explanation = null, points = 5, tagIds = emptyList(),
            createdBy = "instructor", createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
        )
        val choices = listOf(
            QuestionChoice("c1", "q1", "London", false, 1),
            QuestionChoice("c2", "q1", "Paris", true, 2)
        )
        val answer = SubmissionAnswer(
            id = "a1", submissionId = "sub1", questionId = "q1",
            answerText = null, selectedChoiceIds = listOf("c1"), // Wrong
            score = null, maxScore = 5, isAutoGraded = false,
            feedback = null, submittedAt = Instant.now()
        )

        coEvery { assessmentRepository.getAnswersForSubmission("sub1") } returns listOf(answer)
        coEvery { assessmentRepository.getQuestionById("q1") } returns question
        coEvery { assessmentRepository.getChoicesForQuestion("q1") } returns choices

        val result = engine.gradeObjectiveAnswers("sub1")

        assertThat(result.objectiveScore).isEqualTo(0)
        assertThat(result.wrongAnswerQuestionIds).containsExactly("q1")
    }

    @Test
    fun `detects subjective questions in mixed assessment`() = runTest {
        val objQuestion = Question(
            id = "q1", questionBankId = "bank1", questionType = QuestionType.OBJECTIVE,
            difficulty = DifficultyLevel.EASY, questionText = "MCQ",
            explanation = null, points = 5, tagIds = emptyList(),
            createdBy = "instructor", createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
        )
        val subjQuestion = Question(
            id = "q2", questionBankId = "bank1", questionType = QuestionType.SUBJECTIVE,
            difficulty = DifficultyLevel.HARD, questionText = "Essay",
            explanation = null, points = 20, tagIds = emptyList(),
            createdBy = "instructor", createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
        )

        val answers = listOf(
            SubmissionAnswer("a1", "sub1", "q1", null, listOf("c1"), null, 5, false, null, Instant.now()),
            SubmissionAnswer("a2", "sub1", "q2", "Essay text", emptyList(), null, 20, false, null, Instant.now())
        )

        coEvery { assessmentRepository.getAnswersForSubmission("sub1") } returns answers
        coEvery { assessmentRepository.getQuestionById("q1") } returns objQuestion
        coEvery { assessmentRepository.getQuestionById("q2") } returns subjQuestion
        coEvery { assessmentRepository.getChoicesForQuestion("q1") } returns listOf(
            QuestionChoice("c1", "q1", "Correct", true, 1)
        )

        val result = engine.gradeObjectiveAnswers("sub1")

        assertThat(result.hasSubjectiveQuestions).isTrue()
        assertThat(result.gradedAnswers).isEqualTo(1) // Only objective graded
    }

    @Test
    fun `submission state machine enforces transitions`() {
        // IN_PROGRESS -> SUBMITTED allowed
        assertThat(SubmissionStatus.IN_PROGRESS.canTransitionTo(SubmissionStatus.SUBMITTED)).isTrue()
        // SUBMITTED -> AUTO_GRADED allowed
        assertThat(SubmissionStatus.SUBMITTED.canTransitionTo(SubmissionStatus.AUTO_GRADED)).isTrue()
        // FINALIZED -> REOPENED_BY_INSTRUCTOR allowed
        assertThat(SubmissionStatus.FINALIZED.canTransitionTo(SubmissionStatus.REOPENED_BY_INSTRUCTOR)).isTrue()
        // MISSED is terminal
        assertThat(SubmissionStatus.MISSED.isTerminal()).isTrue()
        // ABANDONED is terminal
        assertThat(SubmissionStatus.ABANDONED.isTerminal()).isTrue()
        // AUTO_GRADED -> SUBMITTED not allowed
        assertThat(SubmissionStatus.AUTO_GRADED.canTransitionTo(SubmissionStatus.SUBMITTED)).isFalse()
    }

    @Test
    fun `queues subjective answers for grading`() = runTest {
        val subjQuestion = Question(
            id = "q1", questionBankId = "bank1", questionType = QuestionType.SUBJECTIVE,
            difficulty = DifficultyLevel.MEDIUM, questionText = "Explain",
            explanation = null, points = 15, tagIds = emptyList(),
            createdBy = "instructor", createdAt = Instant.now(), updatedAt = Instant.now(), version = 1
        )
        val answer = SubmissionAnswer(
            "a1", "sub1", "q1", "My answer", emptyList(), null, 15, false, null, Instant.now()
        )

        coEvery { assessmentRepository.getAnswersForSubmission("sub1") } returns listOf(answer)
        coEvery { assessmentRepository.getQuestionById("q1") } returns subjQuestion

        engine.queueSubjectiveAnswers("sub1", "assess1", "class1")

        coVerify { assessmentRepository.createGradeQueueItem(match {
            it.submissionId == "sub1" && it.questionId == "q1" && it.status == GradeQueueStatus.PENDING
        }) }
    }
}
