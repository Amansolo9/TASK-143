package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class AutoGradingEngine @Inject constructor(
    private val assessmentRepository: AssessmentRepository
) {
    /**
     * Auto-grades all objective answers in a submission.
     * Returns total score from objective questions.
     */
    suspend fun gradeObjectiveAnswers(
        submissionId: String
    ): AutoGradeResult {
        val answers = assessmentRepository.getAnswersForSubmission(submissionId)
        val results = mutableListOf<ObjectiveGradeResult>()
        var totalScore = 0
        var totalMaxScore = 0
        var hasSubjective = false

        for (answer in answers) {
            val question = assessmentRepository.getQuestionById(answer.questionId) ?: continue
            val choices = assessmentRepository.getChoicesForQuestion(answer.questionId)

            when (question.questionType) {
                QuestionType.OBJECTIVE -> {
                    val correctChoiceIds = choices.filter { it.isCorrect }.map { it.id }.sorted()
                    val selectedIds = answer.selectedChoiceIds.sorted()
                    val isCorrect = correctChoiceIds == selectedIds
                    val score = if (isCorrect) question.points else 0

                    val result = ObjectiveGradeResult(
                        id = IdGenerator.newId(),
                        submissionAnswerId = answer.id,
                        questionId = question.id,
                        isCorrect = isCorrect,
                        score = score,
                        maxScore = question.points,
                        correctChoiceIds = correctChoiceIds,
                        selectedChoiceIds = selectedIds,
                        gradedAt = TimeUtils.nowUtc()
                    )
                    results.add(result)
                    totalScore += score
                    totalMaxScore += question.points
                }
                QuestionType.SUBJECTIVE -> {
                    hasSubjective = true
                    totalMaxScore += question.points
                }
            }
        }

        if (results.isNotEmpty()) {
            assessmentRepository.createObjectiveGradeResults(results)
        }

        return AutoGradeResult(
            submissionId = submissionId,
            objectiveScore = totalScore,
            objectiveMaxScore = totalMaxScore - (if (hasSubjective) answers.filter {
                val q = assessmentRepository.getQuestionById(it.questionId)
                q?.questionType == QuestionType.SUBJECTIVE
            }.sumOf {
                assessmentRepository.getQuestionById(it.questionId)?.points ?: 0
            } else 0),
            totalMaxScore = totalMaxScore,
            hasSubjectiveQuestions = hasSubjective,
            gradedAnswers = results.size,
            wrongAnswerQuestionIds = results.filter { !it.isCorrect }.map { it.questionId }
        )
    }

    /**
     * Create grading queue items for subjective questions.
     */
    suspend fun queueSubjectiveAnswers(
        submissionId: String,
        assessmentId: String,
        classOfferingId: String
    ) {
        val answers = assessmentRepository.getAnswersForSubmission(submissionId)
        val now = TimeUtils.nowUtc()

        for (answer in answers) {
            val question = assessmentRepository.getQuestionById(answer.questionId) ?: continue
            if (question.questionType == QuestionType.SUBJECTIVE) {
                assessmentRepository.createGradeQueueItem(
                    SubjectiveGradeQueueItem(
                        id = IdGenerator.newId(),
                        submissionId = submissionId,
                        submissionAnswerId = answer.id,
                        questionId = question.id,
                        assessmentId = assessmentId,
                        classOfferingId = classOfferingId,
                        assignedGraderId = null,
                        assignedRoleType = RoleType.INSTRUCTOR,
                        status = GradeQueueStatus.PENDING,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    /**
     * Link wrong answer explanations for incorrectly answered questions.
     */
    suspend fun getWrongAnswerExplanations(
        wrongQuestionIds: List<String>
    ): Map<String, List<WrongAnswerExplanationLink>> {
        return wrongQuestionIds.associateWith { qId ->
            assessmentRepository.getExplanationsForQuestion(qId)
        }.filter { it.value.isNotEmpty() }
    }
}

data class AutoGradeResult(
    val submissionId: String,
    val objectiveScore: Int,
    val objectiveMaxScore: Int,
    val totalMaxScore: Int,
    val hasSubjectiveQuestions: Boolean,
    val gradedAnswers: Int,
    val wrongAnswerQuestionIds: List<String>
)
