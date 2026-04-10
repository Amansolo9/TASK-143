package com.learnmart.app.domain.model

import java.time.Instant

// --- Enrollment Request Lifecycle State Machine ---
enum class EnrollmentRequestStatus {
    DRAFT,
    SUBMITTED,
    PENDING_APPROVAL,
    WAITLISTED,
    OFFERED,
    APPROVED,
    ENROLLED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    DECLINED,
    WITHDRAWN,
    COMPLETED;

    fun allowedTransitions(): Set<EnrollmentRequestStatus> = when (this) {
        DRAFT -> setOf(SUBMITTED)
        SUBMITTED -> setOf(PENDING_APPROVAL, REJECTED, WAITLISTED, EXPIRED)
        PENDING_APPROVAL -> setOf(APPROVED, REJECTED, WAITLISTED, EXPIRED)
        WAITLISTED -> setOf(OFFERED, CANCELLED, EXPIRED)
        OFFERED -> setOf(APPROVED, DECLINED, EXPIRED)
        APPROVED -> setOf(ENROLLED, CANCELLED)
        ENROLLED -> setOf(WITHDRAWN, COMPLETED)
        REJECTED -> emptySet()
        EXPIRED -> emptySet()
        CANCELLED -> emptySet()
        DECLINED -> emptySet()
        WITHDRAWN -> emptySet()
        COMPLETED -> emptySet()
    }

    fun canTransitionTo(target: EnrollmentRequestStatus): Boolean = target in allowedTransitions()

    fun isTerminal(): Boolean = allowedTransitions().isEmpty()

    fun isActive(): Boolean = this in setOf(DRAFT, SUBMITTED, PENDING_APPROVAL, WAITLISTED, OFFERED, APPROVED, ENROLLED)
}

data class EnrollmentRequest(
    val id: String,
    val learnerId: String,
    val classOfferingId: String,
    val status: EnrollmentRequestStatus,
    val priorityTier: Int,
    val submittedAt: Instant?,
    val expiresAt: Instant?,
    val approvalFlowId: String?,
    val eligibilitySnapshotId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class EnrollmentEligibilitySnapshot(
    val id: String,
    val enrollmentRequestId: String,
    val learnerId: String,
    val classOfferingId: String,
    val isEligible: Boolean,
    val eligibilityFlags: String, // JSON string of flag name -> boolean
    val evaluatedAt: Instant
)

enum class ApprovalFlowType {
    SERIAL,
    PARALLEL
}

data class ApprovalFlowDefinition(
    val id: String,
    val classOfferingId: String?,
    val name: String,
    val flowType: ApprovalFlowType,
    val isDefault: Boolean,
    val createdAt: Instant
)

data class ApprovalStepDefinition(
    val id: String,
    val flowId: String,
    val stepOrder: Int,
    val approverRoleType: RoleType,
    val isRequired: Boolean,
    val createdAt: Instant
)

enum class ApprovalTaskStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    SKIPPED
}

data class EnrollmentApprovalTask(
    val id: String,
    val enrollmentRequestId: String,
    val stepDefinitionId: String,
    val assignedToUserId: String?,
    val assignedToRoleType: RoleType,
    val status: ApprovalTaskStatus,
    val decidedBy: String?,
    val decidedAt: Instant?,
    val notes: String?,
    val createdAt: Instant,
    val expiresAt: Instant
)

data class EnrollmentDecisionEvent(
    val id: String,
    val enrollmentRequestId: String,
    val approvalTaskId: String?,
    val decision: String, // APPROVED, REJECTED, EXPIRED, etc.
    val decidedBy: String?,
    val reason: String?,
    val timestamp: Instant
)

data class WaitlistEntry(
    val id: String,
    val learnerId: String,
    val classOfferingId: String,
    val enrollmentRequestId: String,
    val position: Int,
    val priorityTier: Int,
    val addedAt: Instant,
    val offeredAt: Instant?,
    val offerExpiresAt: Instant?,
    val status: WaitlistStatus
)

enum class WaitlistStatus {
    ACTIVE,
    OFFERED,
    ACCEPTED,
    EXPIRED,
    CANCELLED
}

data class EnrollmentRecord(
    val id: String,
    val learnerId: String,
    val classOfferingId: String,
    val enrollmentRequestId: String,
    val enrolledAt: Instant,
    val completedAt: Instant?,
    val withdrawnAt: Instant?,
    val status: EnrollmentRecordStatus
)

enum class EnrollmentRecordStatus {
    ACTIVE,
    COMPLETED,
    WITHDRAWN
}

data class EnrollmentException(
    val id: String,
    val enrollmentRequestId: String,
    val classOfferingId: String,
    val exceptionType: String, // CAPACITY_OVERRIDE, ELIGIBILITY_WAIVER, etc.
    val reason: String,
    val approvedBy: String,
    val approvedAt: Instant
)
