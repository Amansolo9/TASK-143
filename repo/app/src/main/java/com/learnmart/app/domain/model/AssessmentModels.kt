package com.learnmart.app.domain.model

import java.time.Instant

// --- Submission Lifecycle State Machine ---
enum class SubmissionStatus {
    NOT_RELEASED,
    AVAILABLE,
    IN_PROGRESS,
    SUBMITTED,
    LATE_SUBMITTED,
    AUTO_GRADED,
    QUEUED_FOR_MANUAL_REVIEW,
    GRADED,
    FINALIZED,
    REOPENED_BY_INSTRUCTOR,
    MISSED,
    ABANDONED;

    fun allowedTransitions(): Set<SubmissionStatus> = when (this) {
        NOT_RELEASED -> setOf(AVAILABLE)
        AVAILABLE -> setOf(IN_PROGRESS, MISSED)
        IN_PROGRESS -> setOf(SUBMITTED, ABANDONED, LATE_SUBMITTED)
        SUBMITTED -> setOf(AUTO_GRADED, QUEUED_FOR_MANUAL_REVIEW)
        LATE_SUBMITTED -> setOf(AUTO_GRADED, QUEUED_FOR_MANUAL_REVIEW)
        AUTO_GRADED -> setOf(FINALIZED, QUEUED_FOR_MANUAL_REVIEW)
        QUEUED_FOR_MANUAL_REVIEW -> setOf(GRADED)
        GRADED -> setOf(FINALIZED)
        FINALIZED -> setOf(REOPENED_BY_INSTRUCTOR)
        REOPENED_BY_INSTRUCTOR -> setOf(IN_PROGRESS)
        MISSED -> emptySet()
        ABANDONED -> emptySet()
    }

    fun canTransitionTo(target: SubmissionStatus): Boolean = target in allowedTransitions()
    fun isTerminal(): Boolean = this in setOf(MISSED, ABANDONED)
}

enum class QuestionType { OBJECTIVE, SUBJECTIVE }
enum class DifficultyLevel { EASY, MEDIUM, HARD }

data class QuestionBank(
    val id: String,
    val classOfferingId: String,
    val name: String,
    val description: String,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class KnowledgeTag(
    val id: String,
    val name: String,
    val description: String
)

data class Question(
    val id: String,
    val questionBankId: String,
    val questionType: QuestionType,
    val difficulty: DifficultyLevel,
    val questionText: String,
    val explanation: String?,
    val points: Int,
    val tagIds: List<String>,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class QuestionChoice(
    val id: String,
    val questionId: String,
    val choiceText: String,
    val isCorrect: Boolean,
    val displayOrder: Int
)

enum class AssessmentType { ASSIGNMENT, QUIZ }

data class Assignment(
    val id: String,
    val classOfferingId: String,
    val title: String,
    val description: String,
    val assessmentType: AssessmentType,
    val questionBankId: String?,
    val questionIds: List<String>,
    val totalPoints: Int,
    val releaseStart: Instant,
    val releaseEnd: Instant,
    val timeLimitMinutes: Int?,
    val allowLateSubmission: Boolean,
    val allowResubmission: Boolean,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class AssessmentReleaseWindow(
    val id: String,
    val assessmentId: String,
    val releaseStart: Instant,
    val releaseEnd: Instant,
    val isActive: Boolean
)

data class Submission(
    val id: String,
    val assessmentId: String,
    val learnerId: String,
    val status: SubmissionStatus,
    val startedAt: Instant?,
    val submittedAt: Instant?,
    val totalScore: Int?,
    val maxScore: Int?,
    val gradePercentage: Double?,
    val gradedBy: String?,
    val gradedAt: Instant?,
    val finalizedAt: Instant?,
    val versionNumber: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class SubmissionAnswer(
    val id: String,
    val submissionId: String,
    val questionId: String,
    val answerText: String?,
    val selectedChoiceIds: List<String>,
    val score: Int?,
    val maxScore: Int,
    val isAutoGraded: Boolean,
    val feedback: String?,
    val submittedAt: Instant
)

data class ObjectiveGradeResult(
    val id: String,
    val submissionAnswerId: String,
    val questionId: String,
    val isCorrect: Boolean,
    val score: Int,
    val maxScore: Int,
    val correctChoiceIds: List<String>,
    val selectedChoiceIds: List<String>,
    val gradedAt: Instant
)

enum class GradeQueueStatus { PENDING, IN_REVIEW, GRADED, SKIPPED }

data class SubjectiveGradeQueueItem(
    val id: String,
    val submissionId: String,
    val submissionAnswerId: String,
    val questionId: String,
    val assessmentId: String,
    val classOfferingId: String,
    val assignedGraderId: String?,
    val assignedRoleType: RoleType?,
    val status: GradeQueueStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class GradeDecision(
    val id: String,
    val submissionAnswerId: String,
    val gradedBy: String,
    val score: Int,
    val maxScore: Int,
    val feedback: String?,
    val gradedAt: Instant
)

data class WrongAnswerExplanationLink(
    val id: String,
    val questionId: String,
    val explanationText: String,
    val referenceUrl: String?
)

// --- Similarity/Plagiarism ---
data class SimilarityFingerprint(
    val id: String,
    val submissionId: String,
    val assessmentId: String,
    val fingerprintData: String, // JSON of n-gram hashes
    val generatedAt: Instant
)

enum class SimilarityFlag { HIGH_SIMILARITY, REVIEW_NEEDED, CLEAR }

data class SimilarityMatchResult(
    val id: String,
    val submissionId1: String,
    val submissionId2: String,
    val assessmentId: String,
    val similarityScore: Double,
    val flag: SimilarityFlag,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val reviewNotes: String?,
    val detectedAt: Instant
)
