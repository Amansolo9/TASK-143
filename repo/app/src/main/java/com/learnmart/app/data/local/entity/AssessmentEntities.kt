package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ──────────────────────────────────────────────
// 1. QuestionBankEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "question_banks",
    indices = [
        Index(value = ["class_offering_id"])
    ]
)
data class QuestionBankEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

// ──────────────────────────────────────────────
// 2. KnowledgeTagEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "knowledge_tags",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class KnowledgeTagEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String
)

// ──────────────────────────────────────────────
// 3. QuestionEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_bank_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["question_bank_id"])
    ]
)
data class QuestionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "question_bank_id")
    val questionBankId: String,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    @ColumnInfo(name = "difficulty")
    val difficulty: String,
    @ColumnInfo(name = "question_text")
    val questionText: String,
    @ColumnInfo(name = "explanation")
    val explanation: String? = null,
    @ColumnInfo(name = "points")
    val points: Int,
    @ColumnInfo(name = "tag_ids")
    val tagIds: String, // JSON array
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

// ──────────────────────────────────────────────
// 4. QuestionChoiceEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "question_choices",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["question_id"])
    ]
)
data class QuestionChoiceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "choice_text")
    val choiceText: String,
    @ColumnInfo(name = "is_correct")
    val isCorrect: Boolean,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int
)

// ──────────────────────────────────────────────
// 5. AssignmentEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "assignments",
    indices = [
        Index(value = ["class_offering_id"])
    ]
)
data class AssignmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "assessment_type")
    val assessmentType: String,
    @ColumnInfo(name = "question_bank_id")
    val questionBankId: String? = null,
    @ColumnInfo(name = "question_ids")
    val questionIds: String, // JSON array
    @ColumnInfo(name = "total_points")
    val totalPoints: Int,
    @ColumnInfo(name = "release_start")
    val releaseStart: Long,
    @ColumnInfo(name = "release_end")
    val releaseEnd: Long,
    @ColumnInfo(name = "time_limit_minutes")
    val timeLimitMinutes: Int? = null,
    @ColumnInfo(name = "allow_late_submission")
    val allowLateSubmission: Boolean,
    @ColumnInfo(name = "allow_resubmission")
    val allowResubmission: Boolean,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

// ──────────────────────────────────────────────
// 6. AssessmentReleaseWindowEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "assessment_release_windows",
    indices = [
        Index(value = ["assessment_id"])
    ]
)
data class AssessmentReleaseWindowEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "assessment_id")
    val assessmentId: String,
    @ColumnInfo(name = "release_start")
    val releaseStart: Long,
    @ColumnInfo(name = "release_end")
    val releaseEnd: Long,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)

// ──────────────────────────────────────────────
// 7. SubmissionEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "submissions",
    indices = [
        Index(value = ["assessment_id", "learner_id", "submitted_at"]),
        Index(value = ["learner_id"])
    ]
)
data class SubmissionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "assessment_id")
    val assessmentId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "submitted_at")
    val submittedAt: Long? = null,
    @ColumnInfo(name = "total_score")
    val totalScore: Int? = null,
    @ColumnInfo(name = "max_score")
    val maxScore: Int? = null,
    @ColumnInfo(name = "grade_percentage")
    val gradePercentage: Double? = null,
    @ColumnInfo(name = "graded_by")
    val gradedBy: String? = null,
    @ColumnInfo(name = "graded_at")
    val gradedAt: Long? = null,
    @ColumnInfo(name = "finalized_at")
    val finalizedAt: Long? = null,
    @ColumnInfo(name = "version_number")
    val versionNumber: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

// ──────────────────────────────────────────────
// 8. SubmissionAnswerEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "submission_answers",
    indices = [
        Index(value = ["submission_id"])
    ]
)
data class SubmissionAnswerEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "submission_id")
    val submissionId: String,
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "answer_text")
    val answerText: String? = null,
    @ColumnInfo(name = "selected_choice_ids")
    val selectedChoiceIds: String, // JSON array
    @ColumnInfo(name = "score")
    val score: Int? = null,
    @ColumnInfo(name = "max_score")
    val maxScore: Int,
    @ColumnInfo(name = "is_auto_graded")
    val isAutoGraded: Boolean,
    @ColumnInfo(name = "feedback")
    val feedback: String? = null,
    @ColumnInfo(name = "submitted_at")
    val submittedAt: Long
)

// ──────────────────────────────────────────────
// 9. ObjectiveGradeResultEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "objective_grade_results",
    indices = [
        Index(value = ["submission_answer_id"])
    ]
)
data class ObjectiveGradeResultEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "submission_answer_id")
    val submissionAnswerId: String,
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "is_correct")
    val isCorrect: Boolean,
    @ColumnInfo(name = "score")
    val score: Int,
    @ColumnInfo(name = "max_score")
    val maxScore: Int,
    @ColumnInfo(name = "correct_choice_ids")
    val correctChoiceIds: String,
    @ColumnInfo(name = "selected_choice_ids")
    val selectedChoiceIds: String,
    @ColumnInfo(name = "graded_at")
    val gradedAt: Long
)

// ──────────────────────────────────────────────
// 10. SubjectiveGradeQueueItemEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "subjective_grade_queue",
    indices = [
        Index(value = ["class_offering_id", "status"]),
        Index(value = ["assigned_grader_id"])
    ]
)
data class SubjectiveGradeQueueItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "submission_id")
    val submissionId: String,
    @ColumnInfo(name = "submission_answer_id")
    val submissionAnswerId: String,
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "assessment_id")
    val assessmentId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "assigned_grader_id")
    val assignedGraderId: String? = null,
    @ColumnInfo(name = "assigned_role_type")
    val assignedRoleType: String? = null,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

// ──────────────────────────────────────────────
// 11. GradeDecisionEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "grade_decisions",
    indices = [
        Index(value = ["submission_answer_id"])
    ]
)
data class GradeDecisionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "submission_answer_id")
    val submissionAnswerId: String,
    @ColumnInfo(name = "graded_by")
    val gradedBy: String,
    @ColumnInfo(name = "score")
    val score: Int,
    @ColumnInfo(name = "max_score")
    val maxScore: Int,
    @ColumnInfo(name = "feedback")
    val feedback: String? = null,
    @ColumnInfo(name = "graded_at")
    val gradedAt: Long
)

// ──────────────────────────────────────────────
// 12. WrongAnswerExplanationLinkEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "wrong_answer_explanations",
    indices = [
        Index(value = ["question_id"])
    ]
)
data class WrongAnswerExplanationLinkEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "question_id")
    val questionId: String,
    @ColumnInfo(name = "explanation_text")
    val explanationText: String,
    @ColumnInfo(name = "reference_url")
    val referenceUrl: String? = null
)

// ──────────────────────────────────────────────
// 13. SimilarityFingerprintEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "similarity_fingerprints",
    indices = [
        Index(value = ["assessment_id", "submission_id"])
    ]
)
data class SimilarityFingerprintEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "submission_id")
    val submissionId: String,
    @ColumnInfo(name = "assessment_id")
    val assessmentId: String,
    @ColumnInfo(name = "fingerprint_data")
    val fingerprintData: String,
    @ColumnInfo(name = "generated_at")
    val generatedAt: Long
)

// ──────────────────────────────────────────────
// 14. SimilarityMatchResultEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "similarity_match_results",
    indices = [
        Index(value = ["assessment_id"])
    ]
)
data class SimilarityMatchResultEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "submission_id_1")
    val submissionId1: String,
    @ColumnInfo(name = "submission_id_2")
    val submissionId2: String,
    @ColumnInfo(name = "assessment_id")
    val assessmentId: String,
    @ColumnInfo(name = "similarity_score")
    val similarityScore: Double,
    @ColumnInfo(name = "flag")
    val flag: String,
    @ColumnInfo(name = "reviewed_by")
    val reviewedBy: String? = null,
    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long? = null,
    @ColumnInfo(name = "review_notes")
    val reviewNotes: String? = null,
    @ColumnInfo(name = "detected_at")
    val detectedAt: Long
)
