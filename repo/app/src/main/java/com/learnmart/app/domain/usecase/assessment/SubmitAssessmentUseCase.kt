package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

data class SubmitAnswerData(
    val questionId: String,
    val answerText: String?,
    val selectedChoiceIds: List<String>
)

class SubmitAssessmentUseCase @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val autoGradingEngine: AutoGradingEngine,
    private val similarityEngine: SimilarityEngine,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager
) {
    /**
     * Start a submission for an assessment.
     */
    suspend fun startSubmission(assessmentId: String): AppResult<Submission> {
        val learnerId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        val assignment = assessmentRepository.getAssignmentById(assessmentId)
            ?: return AppResult.NotFoundError("ASSESSMENT_NOT_FOUND")

        // Check release window
        val now = TimeUtils.nowUtc()
        if (now.isBefore(assignment.releaseStart)) {
            return AppResult.ValidationError(globalErrors = listOf("Assessment is not yet released"))
        }
        if (now.isAfter(assignment.releaseEnd) && !assignment.allowLateSubmission) {
            return AppResult.ValidationError(globalErrors = listOf("Assessment release window has closed"))
        }

        // Check existing submission
        val existing = assessmentRepository.getSubmissionByAssessmentAndLearner(assessmentId, learnerId)
        if (existing != null) {
            if (existing.status == SubmissionStatus.IN_PROGRESS || existing.status == SubmissionStatus.REOPENED_BY_INSTRUCTOR) {
                return AppResult.Success(existing) // Resume
            }
            if (!assignment.allowResubmission && existing.status != SubmissionStatus.REOPENED_BY_INSTRUCTOR) {
                return AppResult.ConflictError("ALREADY_SUBMITTED", "You have already submitted this assessment")
            }
        }

        val submission = Submission(
            id = IdGenerator.newId(),
            assessmentId = assessmentId,
            learnerId = learnerId,
            status = SubmissionStatus.IN_PROGRESS,
            startedAt = now,
            submittedAt = null,
            totalScore = null,
            maxScore = assignment.totalPoints,
            gradePercentage = null,
            gradedBy = null,
            gradedAt = null,
            finalizedAt = null,
            versionNumber = (existing?.versionNumber ?: 0) + 1,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        assessmentRepository.createSubmission(submission)
        return AppResult.Success(submission)
    }

    /**
     * Submit answers for an in-progress submission.
     */
    suspend fun submitAnswers(
        submissionId: String,
        answers: List<SubmitAnswerData>
    ): AppResult<Submission> {
        val learnerId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        val submission = assessmentRepository.getSubmissionById(submissionId)
            ?: return AppResult.NotFoundError("SUBMISSION_NOT_FOUND")

        if (submission.learnerId != learnerId) {
            return AppResult.PermissionError("Not your submission")
        }

        if (submission.status != SubmissionStatus.IN_PROGRESS &&
            submission.status != SubmissionStatus.REOPENED_BY_INSTRUCTOR) {
            return AppResult.ValidationError(
                globalErrors = listOf("Submission is not in progress (status: ${submission.status})")
            )
        }

        val assignment = assessmentRepository.getAssignmentById(submission.assessmentId)
            ?: return AppResult.NotFoundError("ASSESSMENT_NOT_FOUND")

        // Validate answers against questions
        val now = TimeUtils.nowUtc()
        val isLate = now.isAfter(assignment.releaseEnd)

        // Save answers
        val submissionAnswers = answers.map { answer ->
            val question = assessmentRepository.getQuestionById(answer.questionId)
            SubmissionAnswer(
                id = IdGenerator.newId(),
                submissionId = submissionId,
                questionId = answer.questionId,
                answerText = answer.answerText,
                selectedChoiceIds = answer.selectedChoiceIds,
                score = null,
                maxScore = question?.points ?: 0,
                isAutoGraded = false,
                feedback = null,
                submittedAt = now
            )
        }
        assessmentRepository.createAnswers(submissionAnswers)

        // Update submission status
        val newStatus = if (isLate) SubmissionStatus.LATE_SUBMITTED else SubmissionStatus.SUBMITTED
        assessmentRepository.updateSubmission(submission.copy(
            status = newStatus,
            submittedAt = now,
            updatedAt = now,
            version = submission.version + 1
        ))

        // Auto-grade objective questions
        val gradeResult = autoGradingEngine.gradeObjectiveAnswers(submissionId)

        // Determine next status
        val finalStatus = if (gradeResult.hasSubjectiveQuestions) {
            // Queue subjective questions for manual grading
            autoGradingEngine.queueSubjectiveAnswers(
                submissionId, submission.assessmentId,
                assignment.classOfferingId
            )
            SubmissionStatus.QUEUED_FOR_MANUAL_REVIEW
        } else {
            // All objective - auto-grade is complete
            SubmissionStatus.AUTO_GRADED
        }

        val totalScore = gradeResult.objectiveScore
        val maxScore = assignment.totalPoints
        val percentage = if (maxScore > 0) (totalScore.toDouble() / maxScore * 100) else 0.0

        assessmentRepository.updateSubmission(submission.copy(
            status = finalStatus,
            totalScore = totalScore,
            maxScore = maxScore,
            gradePercentage = if (!gradeResult.hasSubjectiveQuestions) percentage else null,
            gradedBy = if (!gradeResult.hasSubjectiveQuestions) "AUTO" else null,
            gradedAt = if (!gradeResult.hasSubjectiveQuestions) now else null,
            updatedAt = now
        ))

        // If fully auto-graded, finalize
        if (!gradeResult.hasSubjectiveQuestions) {
            assessmentRepository.updateSubmission(submission.copy(
                status = SubmissionStatus.FINALIZED,
                totalScore = totalScore,
                maxScore = maxScore,
                gradePercentage = percentage,
                gradedBy = "AUTO",
                gradedAt = now,
                finalizedAt = now,
                updatedAt = now
            ))

            // Generate similarity fingerprint (for finalized submissions)
            try {
                similarityEngine.generateFingerprint(submissionId, submission.assessmentId)
                similarityEngine.compareAgainstPeers(submissionId, submission.assessmentId)
            } catch (_: Exception) {
                // Similarity is advisory - don't fail the submission
            }
        }

        // Audit
        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = learnerId, actorUsername = null,
            actionType = AuditActionType.SUBMISSION_RECEIVED,
            targetEntityType = "Submission", targetEntityId = submissionId,
            beforeSummary = null,
            afterSummary = "status=$finalStatus, score=$totalScore/$maxScore, late=$isLate",
            reason = null, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(), entityType = "Submission", entityId = submissionId,
            fromState = "IN_PROGRESS", toState = finalStatus.name,
            triggeredBy = learnerId, reason = null, timestamp = now
        ))

        return AppResult.Success(assessmentRepository.getSubmissionById(submissionId)!!)
    }
}
