package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

data class CreateAssignmentRequest(
    val classOfferingId: String,
    val title: String,
    val description: String,
    val assessmentType: AssessmentType,
    val questionIds: List<String>,
    val totalPoints: Int,
    val releaseStart: java.time.Instant,
    val releaseEnd: java.time.Instant,
    val timeLimitMinutes: Int?,
    val allowLateSubmission: Boolean,
    val allowResubmission: Boolean
)

class ManageAssessmentUseCase @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    suspend fun createAssignment(request: CreateAssignmentRequest): AppResult<Assignment> {
        if (!checkPermission.hasPermission(Permission.ASSESSMENT_CREATE)) {
            return AppResult.PermissionError("Requires assessment.create")
        }

        val errors = mutableMapOf<String, String>()
        if (request.title.isBlank()) errors["title"] = "Title is required"
        if (request.releaseEnd.isBefore(request.releaseStart)) {
            errors["releaseEnd"] = "Release end must be after start"
        }
        if (request.totalPoints < 1) errors["totalPoints"] = "Total points must be >= 1"
        if (errors.isNotEmpty()) return AppResult.ValidationError(fieldErrors = errors)

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"

        val assignment = Assignment(
            id = IdGenerator.newId(),
            classOfferingId = request.classOfferingId,
            title = request.title.trim(),
            description = request.description.trim(),
            assessmentType = request.assessmentType,
            questionBankId = null,
            questionIds = request.questionIds,
            totalPoints = request.totalPoints,
            releaseStart = request.releaseStart,
            releaseEnd = request.releaseEnd,
            timeLimitMinutes = request.timeLimitMinutes,
            allowLateSubmission = request.allowLateSubmission,
            allowResubmission = request.allowResubmission,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        assessmentRepository.createAssignment(assignment)

        // Create release window
        assessmentRepository.createReleaseWindow(AssessmentReleaseWindow(
            id = IdGenerator.newId(),
            assessmentId = assignment.id,
            releaseStart = request.releaseStart,
            releaseEnd = request.releaseEnd,
            isActive = true
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = userId, actorUsername = null,
            actionType = AuditActionType.ASSIGNMENT_CREATED,
            targetEntityType = "Assignment", targetEntityId = assignment.id,
            beforeSummary = null,
            afterSummary = "title=${assignment.title}, type=${assignment.assessmentType}, points=${assignment.totalPoints}",
            reason = null, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        return AppResult.Success(assignment)
    }

    suspend fun getAssignmentById(id: String): AppResult<Assignment> {
        val assignment = assessmentRepository.getAssignmentById(id)
            ?: return AppResult.NotFoundError("ASSIGNMENT_NOT_FOUND")
        return AppResult.Success(assignment)
    }

    suspend fun getAssignmentsForClass(classOfferingId: String): List<Assignment> =
        assessmentRepository.getAssignmentsByClassOffering(classOfferingId)

    suspend fun getMySubmissions(): List<Submission> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return assessmentRepository.getSubmissionsByLearner(userId)
    }

    suspend fun getSubmissionsForAssessment(assessmentId: String, limit: Int = 50, offset: Int = 0): List<Submission> =
        assessmentRepository.getSubmissionsByAssessment(assessmentId, limit, offset)

    // --- Question Bank ---
    suspend fun createQuestionBank(classOfferingId: String, name: String, description: String): AppResult<QuestionBank> {
        if (!checkPermission.hasPermission(Permission.ASSESSMENT_CREATE)) {
            return AppResult.PermissionError()
        }
        val now = TimeUtils.nowUtc()
        val bank = QuestionBank(
            id = IdGenerator.newId(), classOfferingId = classOfferingId,
            name = name, description = description,
            createdBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            createdAt = now, updatedAt = now
        )
        assessmentRepository.createQuestionBank(bank)
        return AppResult.Success(bank)
    }

    suspend fun createQuestion(
        questionBankId: String, questionType: QuestionType, difficulty: DifficultyLevel,
        questionText: String, explanation: String?, points: Int, tagIds: List<String>,
        choices: List<Pair<String, Boolean>> // (choiceText, isCorrect)
    ): AppResult<Question> {
        if (!checkPermission.hasPermission(Permission.ASSESSMENT_CREATE)) {
            return AppResult.PermissionError()
        }
        if (questionText.isBlank()) return AppResult.ValidationError(fieldErrors = mapOf("questionText" to "Required"))
        if (points < 1) return AppResult.ValidationError(fieldErrors = mapOf("points" to "Must be >= 1"))
        if (questionType == QuestionType.OBJECTIVE && choices.isEmpty()) {
            return AppResult.ValidationError(globalErrors = listOf("Objective questions require at least one choice"))
        }

        val now = TimeUtils.nowUtc()
        val question = Question(
            id = IdGenerator.newId(), questionBankId = questionBankId,
            questionType = questionType, difficulty = difficulty,
            questionText = questionText, explanation = explanation,
            points = points, tagIds = tagIds,
            createdBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            createdAt = now, updatedAt = now, version = 1
        )
        assessmentRepository.createQuestion(question)

        if (choices.isNotEmpty()) {
            val choiceEntities = choices.mapIndexed { index, (text, correct) ->
                QuestionChoice(
                    id = IdGenerator.newId(), questionId = question.id,
                    choiceText = text, isCorrect = correct, displayOrder = index + 1
                )
            }
            assessmentRepository.createChoices(choiceEntities)
        }

        return AppResult.Success(question)
    }

    suspend fun getQuestionsForBank(bankId: String): List<Question> =
        assessmentRepository.getQuestionsByBankId(bankId)

    suspend fun getChoicesForQuestion(questionId: String): List<QuestionChoice> =
        assessmentRepository.getChoicesForQuestion(questionId)

    // --- Grading Queue ---
    suspend fun getPendingGradingQueue(classOfferingId: String): List<SubjectiveGradeQueueItem> =
        assessmentRepository.getPendingQueueByClassOffering(classOfferingId)

    suspend fun getMyGradingQueue(): List<SubjectiveGradeQueueItem> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return assessmentRepository.getPendingQueueByGrader(userId)
    }

    suspend fun gradeSubjectiveAnswer(
        queueItemId: String,
        score: Int,
        feedback: String?
    ): AppResult<GradeDecision> {
        if (!checkPermission.hasPermission(Permission.ASSESSMENT_GRADE)) {
            return AppResult.PermissionError("Requires assessment.grade")
        }

        val queueItem = assessmentRepository.getGradeQueueItemById(queueItemId)
            ?: return AppResult.NotFoundError("QUEUE_ITEM_NOT_FOUND")

        if (queueItem.status != GradeQueueStatus.PENDING && queueItem.status != GradeQueueStatus.IN_REVIEW) {
            return AppResult.ValidationError(globalErrors = listOf("Queue item is not pending"))
        }

        val question = assessmentRepository.getQuestionById(queueItem.questionId)
        if (question != null && score > question.points) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("score" to "Score cannot exceed max points (${question.points})")
            )
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"

        val decision = GradeDecision(
            id = IdGenerator.newId(),
            submissionAnswerId = queueItem.submissionAnswerId,
            gradedBy = userId,
            score = score,
            maxScore = question?.points ?: score,
            feedback = feedback,
            gradedAt = now
        )
        assessmentRepository.createGradeDecision(decision)

        // Update queue item
        assessmentRepository.updateGradeQueueItem(queueItem.copy(
            status = GradeQueueStatus.GRADED,
            assignedGraderId = userId,
            updatedAt = now
        ))

        // Check if all subjective items are graded
        checkAndFinalizeSubmission(queueItem.submissionId, queueItem.assessmentId)

        return AppResult.Success(decision)
    }

    suspend fun reopenSubmission(submissionId: String, reason: String): AppResult<Submission> {
        if (!checkPermission.hasPermission(Permission.ASSESSMENT_REOPEN)) {
            return AppResult.PermissionError("Requires assessment.reopen")
        }

        val submission = assessmentRepository.getSubmissionById(submissionId)
            ?: return AppResult.NotFoundError("SUBMISSION_NOT_FOUND")

        if (submission.status != SubmissionStatus.FINALIZED) {
            return AppResult.ValidationError(globalErrors = listOf("Only finalized submissions can be reopened"))
        }

        assessmentRepository.updateSubmissionStatus(submissionId, SubmissionStatus.REOPENED_BY_INSTRUCTOR, submission.version)

        val now = TimeUtils.nowUtc()
        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(), actorUsername = null,
            actionType = AuditActionType.SUBMISSION_REOPENED,
            targetEntityType = "Submission", targetEntityId = submissionId,
            beforeSummary = "status=FINALIZED", afterSummary = "status=REOPENED_BY_INSTRUCTOR",
            reason = reason, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(), entityType = "Submission", entityId = submissionId,
            fromState = "FINALIZED", toState = "REOPENED_BY_INSTRUCTOR",
            triggeredBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = reason, timestamp = now
        ))

        return AppResult.Success(assessmentRepository.getSubmissionById(submissionId)!!)
    }

    private suspend fun checkAndFinalizeSubmission(submissionId: String, assessmentId: String) {
        // Use the correct query: get all queue items for this specific submission
        val allQueueItems = assessmentRepository.getQueueItemsForSubmission(submissionId)

        // If there are still pending or in-review items, don't finalize
        if (allQueueItems.any { it.status == GradeQueueStatus.PENDING || it.status == GradeQueueStatus.IN_REVIEW }) {
            return
        }

        // All subjective items are graded - finalize the submission
        val submission = assessmentRepository.getSubmissionById(submissionId) ?: return
        if (submission.status != SubmissionStatus.QUEUED_FOR_MANUAL_REVIEW &&
            submission.status != SubmissionStatus.GRADED) {
            return
        }

        // Calculate total score from both objective and subjective results
        val answers = assessmentRepository.getAnswersForSubmission(submissionId)
        var totalScore = 0
        for (answer in answers) {
            val objResult = assessmentRepository.getObjectiveGradeResult(answer.id)
            val gradeDecision = assessmentRepository.getGradeDecision(answer.id)
            totalScore += objResult?.score ?: gradeDecision?.score ?: 0
        }

        val maxScore = submission.maxScore ?: 0
        val percentage = if (maxScore > 0) (totalScore.toDouble() / maxScore * 100) else 0.0
        val now = TimeUtils.nowUtc()

        // Transition through GRADED to FINALIZED in one step
        assessmentRepository.updateSubmission(submission.copy(
            status = SubmissionStatus.FINALIZED,
            totalScore = totalScore,
            gradePercentage = percentage,
            gradedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            gradedAt = now,
            finalizedAt = now,
            updatedAt = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(), actorUsername = null,
            actionType = AuditActionType.GRADE_FINALIZED,
            targetEntityType = "Submission", targetEntityId = submissionId,
            beforeSummary = "status=${submission.status}",
            afterSummary = "status=FINALIZED, score=$totalScore/$maxScore, percentage=${"%.1f".format(percentage)}%",
            reason = null, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(), entityType = "Submission", entityId = submissionId,
            fromState = submission.status.name, toState = "FINALIZED",
            triggeredBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = "All grading complete", timestamp = now
        ))
    }
}
