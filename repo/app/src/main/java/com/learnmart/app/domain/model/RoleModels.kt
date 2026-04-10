package com.learnmart.app.domain.model

import java.time.Instant

enum class RoleType {
    ADMINISTRATOR,
    REGISTRAR,
    INSTRUCTOR,
    TEACHING_ASSISTANT,
    LEARNER,
    FINANCE_CLERK
}

enum class Permission(val capability: String) {
    CATALOG_MANAGE("catalog.manage"),
    CATALOG_PUBLISH("catalog.publish"),
    CLASS_MANAGE("class.manage"),
    CLASS_STAFF_ASSIGN("class.staff.assign"),
    ENROLLMENT_REQUEST("enrollment.request"),
    ENROLLMENT_REVIEW("enrollment.review"),
    ENROLLMENT_OVERRIDE_CAPACITY("enrollment.override_capacity"),
    ORDER_CREATE("order.create"),
    ORDER_FULFILL("order.fulfill"),
    PAYMENT_RECORD("payment.record"),
    PAYMENT_RECONCILE("payment.reconcile"),
    REFUND_ISSUE("refund.issue"),
    REFUND_OVERRIDE_LIMIT("refund.override_limit"),
    ASSESSMENT_CREATE("assessment.create"),
    ASSESSMENT_GRADE("assessment.grade"),
    ASSESSMENT_REOPEN("assessment.reopen"),
    BACKUP_RUN("backup.run"),
    RESTORE_RUN("restore.run"),
    IMPORT_MANAGE("import.manage"),
    EXPORT_MANAGE("export.manage"),
    POLICY_MANAGE("policy.manage"),
    AUDIT_VIEW("audit.view"),
    RISK_MANAGE("risk.manage"),
    USER_MANAGE("user.manage");

    companion object {
        fun fromCapability(cap: String): Permission? =
            entries.find { it.capability == cap }
    }
}

data class Role(
    val id: String,
    val type: RoleType,
    val name: String,
    val description: String,
    val isSystem: Boolean,
    val createdAt: Instant
)

data class UserRoleAssignment(
    val id: String,
    val userId: String,
    val roleId: String,
    val roleType: RoleType,
    val assignedAt: Instant,
    val assignedBy: String
)

data class RolePermissionGrant(
    val id: String,
    val roleId: String,
    val permission: Permission,
    val grantedAt: Instant
)

data class BlacklistFlag(
    val id: String,
    val userId: String,
    val reason: String,
    val flaggedBy: String,
    val flaggedAt: Instant,
    val isActive: Boolean,
    val resolvedAt: Instant?,
    val resolvedBy: String?,
    val resolutionNote: String?
)

object DefaultRolePermissions {
    val ADMINISTRATOR_PERMISSIONS = Permission.entries.toSet()

    val REGISTRAR_PERMISSIONS = setOf(
        Permission.CATALOG_MANAGE,
        Permission.CLASS_MANAGE,
        Permission.CLASS_STAFF_ASSIGN,
        Permission.ENROLLMENT_REVIEW,
        Permission.ENROLLMENT_OVERRIDE_CAPACITY,
        Permission.AUDIT_VIEW
    )

    val INSTRUCTOR_PERMISSIONS = setOf(
        Permission.ASSESSMENT_CREATE,
        Permission.ASSESSMENT_GRADE,
        Permission.ASSESSMENT_REOPEN
    )

    val TEACHING_ASSISTANT_PERMISSIONS = setOf(
        Permission.ASSESSMENT_GRADE
    )

    val LEARNER_PERMISSIONS = setOf(
        Permission.ENROLLMENT_REQUEST,
        Permission.ORDER_CREATE
    )

    val FINANCE_CLERK_PERMISSIONS = setOf(
        Permission.PAYMENT_RECORD,
        Permission.PAYMENT_RECONCILE,
        Permission.REFUND_ISSUE,
        Permission.IMPORT_MANAGE,
        Permission.EXPORT_MANAGE
    )

    fun permissionsForRole(roleType: RoleType): Set<Permission> = when (roleType) {
        RoleType.ADMINISTRATOR -> ADMINISTRATOR_PERMISSIONS
        RoleType.REGISTRAR -> REGISTRAR_PERMISSIONS
        RoleType.INSTRUCTOR -> INSTRUCTOR_PERMISSIONS
        RoleType.TEACHING_ASSISTANT -> TEACHING_ASSISTANT_PERMISSIONS
        RoleType.LEARNER -> LEARNER_PERMISSIONS
        RoleType.FINANCE_CLERK -> FINANCE_CLERK_PERMISSIONS
    }
}
