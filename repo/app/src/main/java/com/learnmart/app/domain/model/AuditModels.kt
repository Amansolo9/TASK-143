package com.learnmart.app.domain.model

import java.time.Instant

enum class AuditActionType {
    // Auth
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    SESSION_EXPIRED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,

    // User management
    USER_CREATED,
    USER_UPDATED,
    USER_DISABLED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    // Policy
    POLICY_CREATED,
    POLICY_UPDATED,

    // Catalog
    COURSE_CREATED,
    COURSE_UPDATED,
    COURSE_PUBLISHED,
    COURSE_UNPUBLISHED,
    COURSE_ARCHIVED,

    // Class
    CLASS_CREATED,
    CLASS_UPDATED,
    CLASS_STATE_CHANGED,
    STAFF_ASSIGNED,
    STAFF_UNASSIGNED,
    CAPACITY_OVERRIDE,

    // Enrollment
    ENROLLMENT_REQUESTED,
    ENROLLMENT_APPROVED,
    ENROLLMENT_REJECTED,
    ENROLLMENT_WAITLISTED,
    ENROLLMENT_OFFERED,
    ENROLLMENT_EXPIRED,
    ENROLLMENT_WITHDRAWN,
    ENROLLMENT_COMPLETED,
    ENROLLMENT_CANCELLED,

    // Commerce
    ORDER_PLACED,
    ORDER_CANCELLED,
    ORDER_AUTO_CANCELLED,
    ORDER_FULFILLED,
    ORDER_DELIVERED,
    ORDER_CLOSED,

    // Payment
    PAYMENT_RECORDED,
    PAYMENT_ALLOCATED,
    PAYMENT_VOIDED,

    // Refund
    REFUND_ISSUED,
    REFUND_OVERRIDE,

    // Reconciliation
    IMPORT_STARTED,
    IMPORT_COMPLETED,
    IMPORT_REJECTED,
    EXPORT_COMPLETED,
    RECONCILIATION_RUN,
    DISCREPANCY_CREATED,
    DISCREPANCY_RESOLVED,

    // Assessment
    ASSIGNMENT_CREATED,
    SUBMISSION_RECEIVED,
    GRADE_FINALIZED,
    SUBMISSION_REOPENED,

    // Blacklist
    BLACKLIST_ADDED,
    BLACKLIST_REMOVED,

    // Backup/Restore
    BACKUP_STARTED,
    BACKUP_COMPLETED,
    RESTORE_STARTED,
    RESTORE_COMPLETED,

    // Inventory
    INVENTORY_LOCK_ACQUIRED,
    INVENTORY_LOCK_RELEASED,
    INVENTORY_LOCK_EXPIRED,

    // System
    SYSTEM_STARTUP,
    PRIVILEGED_OVERRIDE
}

data class AuditEvent(
    val id: String,
    val actorId: String?,
    val actorUsername: String?,
    val actionType: AuditActionType,
    val targetEntityType: String?,
    val targetEntityId: String?,
    val beforeSummary: String?,
    val afterSummary: String?,
    val reason: String?,
    val sessionId: String?,
    val outcome: AuditOutcome,
    val timestamp: Instant,
    val metadata: String?
)

enum class AuditOutcome {
    SUCCESS,
    FAILURE,
    DENIED,
    ERROR
}

data class StateTransitionLog(
    val id: String,
    val entityType: String,
    val entityId: String,
    val fromState: String,
    val toState: String,
    val triggeredBy: String,
    val reason: String?,
    val timestamp: Instant
)
