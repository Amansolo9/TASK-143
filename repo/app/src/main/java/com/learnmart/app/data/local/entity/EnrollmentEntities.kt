package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ──────────────────────────────────────────────
// 1. EnrollmentRequestEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "enrollment_requests",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["learner_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClassOfferingEntity::class,
            parentColumns = ["id"],
            childColumns = ["class_offering_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["class_offering_id", "status", "created_at"]),
        Index(value = ["learner_id"])
    ]
)
data class EnrollmentRequestEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "priority_tier", defaultValue = "0")
    val priorityTier: Int = 0,
    @ColumnInfo(name = "submitted_at")
    val submittedAt: Long? = null,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
    @ColumnInfo(name = "approval_flow_id")
    val approvalFlowId: String? = null,
    @ColumnInfo(name = "eligibility_snapshot_id")
    val eligibilitySnapshotId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

// ──────────────────────────────────────────────
// 2. EnrollmentEligibilitySnapshotEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "enrollment_eligibility_snapshots",
    indices = [
        Index(value = ["enrollment_request_id"])
    ]
)
data class EnrollmentEligibilitySnapshotEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "enrollment_request_id")
    val enrollmentRequestId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "is_eligible")
    val isEligible: Boolean,
    @ColumnInfo(name = "eligibility_flags")
    val eligibilityFlags: String, // JSON
    @ColumnInfo(name = "evaluated_at")
    val evaluatedAt: Long
)

// ──────────────────────────────────────────────
// 3. ApprovalFlowDefinitionEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "approval_flow_definitions"
)
data class ApprovalFlowDefinitionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String? = null,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "flow_type")
    val flowType: String,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

// ──────────────────────────────────────────────
// 4. ApprovalStepDefinitionEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "approval_step_definitions",
    foreignKeys = [
        ForeignKey(
            entity = ApprovalFlowDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["flow_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["flow_id", "step_order"], unique = true)
    ]
)
data class ApprovalStepDefinitionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "flow_id")
    val flowId: String,
    @ColumnInfo(name = "step_order")
    val stepOrder: Int,
    @ColumnInfo(name = "approver_role_type")
    val approverRoleType: String,
    @ColumnInfo(name = "is_required")
    val isRequired: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

// ──────────────────────────────────────────────
// 5. EnrollmentApprovalTaskEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "enrollment_approval_tasks",
    indices = [
        Index(value = ["enrollment_request_id"]),
        Index(value = ["assigned_to_role_type", "status"])
    ]
)
data class EnrollmentApprovalTaskEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "enrollment_request_id")
    val enrollmentRequestId: String,
    @ColumnInfo(name = "step_definition_id")
    val stepDefinitionId: String,
    @ColumnInfo(name = "assigned_to_user_id")
    val assignedToUserId: String? = null,
    @ColumnInfo(name = "assigned_to_role_type")
    val assignedToRoleType: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "decided_by")
    val decidedBy: String? = null,
    @ColumnInfo(name = "decided_at")
    val decidedAt: Long? = null,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)

// ──────────────────────────────────────────────
// 6. EnrollmentDecisionEventEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "enrollment_decision_events",
    indices = [
        Index(value = ["enrollment_request_id"])
    ]
)
data class EnrollmentDecisionEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "enrollment_request_id")
    val enrollmentRequestId: String,
    @ColumnInfo(name = "approval_task_id")
    val approvalTaskId: String? = null,
    @ColumnInfo(name = "decision")
    val decision: String,
    @ColumnInfo(name = "decided_by")
    val decidedBy: String? = null,
    @ColumnInfo(name = "reason")
    val reason: String? = null,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)

// ──────────────────────────────────────────────
// 7. WaitlistEntryEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "waitlist_entries",
    indices = [
        Index(value = ["class_offering_id", "learner_id", "status"], unique = true),
        Index(value = ["class_offering_id", "priority_tier", "added_at"])
    ]
)
data class WaitlistEntryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "enrollment_request_id")
    val enrollmentRequestId: String,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "priority_tier")
    val priorityTier: Int,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
    @ColumnInfo(name = "offered_at")
    val offeredAt: Long? = null,
    @ColumnInfo(name = "offer_expires_at")
    val offerExpiresAt: Long? = null,
    @ColumnInfo(name = "status")
    val status: String
)

// ──────────────────────────────────────────────
// 8. EnrollmentRecordEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "enrollment_records",
    indices = [
        Index(value = ["learner_id", "class_offering_id"], unique = true),
        Index(value = ["class_offering_id"])
    ]
)
data class EnrollmentRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "enrollment_request_id")
    val enrollmentRequestId: String,
    @ColumnInfo(name = "enrolled_at")
    val enrolledAt: Long,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "withdrawn_at")
    val withdrawnAt: Long? = null,
    @ColumnInfo(name = "status")
    val status: String
)

// ──────────────────────────────────────────────
// 9. EnrollmentExceptionEntity
// ──────────────────────────────────────────────

@Entity(
    tableName = "enrollment_exceptions",
    indices = [
        Index(value = ["enrollment_request_id"])
    ]
)
data class EnrollmentExceptionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "enrollment_request_id")
    val enrollmentRequestId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String,
    @ColumnInfo(name = "exception_type")
    val exceptionType: String,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "approved_by")
    val approvedBy: String,
    @ColumnInfo(name = "approved_at")
    val approvedAt: Long
)
