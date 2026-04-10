package com.learnmart.app.data.local

import com.learnmart.app.data.local.dao.PolicyDao
import com.learnmart.app.data.local.dao.RoleDao
import com.learnmart.app.data.local.dao.UserDao
import com.learnmart.app.data.local.entity.PolicyEntity
import com.learnmart.app.data.local.entity.RoleEntity
import com.learnmart.app.data.local.entity.RolePermissionEntity
import com.learnmart.app.data.local.entity.UserEntity
import com.learnmart.app.data.local.entity.UserRoleAssignmentEntity
import com.learnmart.app.domain.model.DefaultRolePermissions
import com.learnmart.app.domain.model.PolicyDefaults
import com.learnmart.app.domain.model.PolicyType
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.security.CredentialManager
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedDataBootstrapper @Inject constructor(
    private val userDao: UserDao,
    private val roleDao: RoleDao,
    private val policyDao: PolicyDao,
    private val credentialManager: CredentialManager
) {
    suspend fun seedIfEmpty() {
        val existingUsers = userDao.countActive()
        if (existingUsers > 0) return

        val now = TimeUtils.nowUtc().toEpochMilli()

        // 1. Seed roles
        val roles = RoleType.entries.map { roleType ->
            RoleEntity(
                id = IdGenerator.newId(),
                type = roleType.name,
                name = roleType.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                description = descriptionFor(roleType),
                isSystem = true,
                createdAt = now
            )
        }
        roleDao.insertRoles(roles)

        // 2. Seed role permissions
        val permissions = roles.flatMap { role ->
            val roleType = RoleType.valueOf(role.type)
            DefaultRolePermissions.permissionsForRole(roleType).map { perm ->
                RolePermissionEntity(
                    id = IdGenerator.newId(),
                    roleId = role.id,
                    permission = perm.capability,
                    grantedAt = now
                )
            }
        }
        roleDao.insertPermissions(permissions)

        // 3. Seed admin user (password: "admin1234")
        val adminSalt = credentialManager.generateSalt()
        val adminHash = credentialManager.hashCredential("admin1234", adminSalt)
        val adminId = IdGenerator.newId()
        val adminUser = UserEntity(
            id = adminId,
            username = "admin",
            displayName = "System Administrator",
            credentialHash = credentialManager.hashToHex(adminHash),
            credentialSalt = credentialManager.saltToHex(adminSalt),
            credentialType = "PASSWORD",
            status = "ACTIVE",
            failedLoginAttempts = 0,
            lockedUntil = null,
            lastLoginAt = null,
            createdAt = now,
            updatedAt = now,
            version = 1
        )
        userDao.insert(adminUser)

        // Assign admin role
        val adminRole = roles.first { it.type == RoleType.ADMINISTRATOR.name }
        roleDao.insertUserRoleAssignment(
            UserRoleAssignmentEntity(
                id = IdGenerator.newId(),
                userId = adminId,
                roleId = adminRole.id,
                roleType = RoleType.ADMINISTRATOR.name,
                assignedAt = now,
                assignedBy = "SYSTEM"
            )
        )

        // 4. Seed demo users
        seedDemoUser("registrar", "Demo Registrar", "pass1234", RoleType.REGISTRAR, roles, now)
        seedDemoUser("instructor", "Demo Instructor", "pass1234", RoleType.INSTRUCTOR, roles, now)
        seedDemoUser("ta", "Demo Teaching Assistant", "pass1234", RoleType.TEACHING_ASSISTANT, roles, now)
        seedDemoUser("learner", "Demo Learner", "pass1234", RoleType.LEARNER, roles, now)
        seedDemoUser("finance", "Demo Finance Clerk", "pass1234", RoleType.FINANCE_CLERK, roles, now)

        // 5. Seed default policies
        seedDefaultPolicies(now)
    }

    private suspend fun seedDemoUser(
        username: String,
        displayName: String,
        password: String,
        roleType: RoleType,
        roles: List<RoleEntity>,
        now: Long
    ) {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential(password, salt)
        val userId = IdGenerator.newId()

        userDao.insert(
            UserEntity(
                id = userId,
                username = username,
                displayName = displayName,
                credentialHash = credentialManager.hashToHex(hash),
                credentialSalt = credentialManager.saltToHex(salt),
                credentialType = "PASSWORD",
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
                version = 1
            )
        )

        val role = roles.first { it.type == roleType.name }
        roleDao.insertUserRoleAssignment(
            UserRoleAssignmentEntity(
                id = IdGenerator.newId(),
                userId = userId,
                roleId = role.id,
                roleType = roleType.name,
                assignedAt = now,
                assignedBy = "SYSTEM"
            )
        )
    }

    private suspend fun seedDefaultPolicies(now: Long) {
        val policies = listOf(
            PolicySeed(PolicyType.SYSTEM, "session_timeout_minutes", PolicyDefaults.SESSION_TIMEOUT_MINUTES, "Idle session timeout in minutes"),
            PolicySeed(PolicyType.SYSTEM, "lockout_attempts", PolicyDefaults.LOCKOUT_ATTEMPTS, "Max failed login attempts before lockout"),
            PolicySeed(PolicyType.SYSTEM, "lockout_window_minutes", PolicyDefaults.LOCKOUT_WINDOW_MINUTES, "Window for counting failed attempts"),
            PolicySeed(PolicyType.SYSTEM, "lockout_duration_minutes", PolicyDefaults.LOCKOUT_DURATION_MINUTES, "Duration of account lockout"),
            PolicySeed(PolicyType.SYSTEM, "password_min_length", PolicyDefaults.PASSWORD_MIN_LENGTH, "Minimum password length"),
            PolicySeed(PolicyType.ENROLLMENT, "request_expiry_hours", PolicyDefaults.ENROLLMENT_REQUEST_EXPIRY_HOURS, "Hours before pending enrollment expires"),
            PolicySeed(PolicyType.ENROLLMENT, "waitlist_offer_expiry_hours", PolicyDefaults.WAITLIST_OFFER_EXPIRY_HOURS, "Hours before waitlist offer expires"),
            PolicySeed(PolicyType.ENROLLMENT, "waitlist_enabled_default", PolicyDefaults.WAITLIST_ENABLED_DEFAULT, "Whether waitlist is enabled by default"),
            PolicySeed(PolicyType.COMMERCE, "minimum_order_total", PolicyDefaults.MINIMUM_ORDER_TOTAL, "Minimum order total in USD"),
            PolicySeed(PolicyType.COMMERCE, "packaging_fee", PolicyDefaults.PACKAGING_FEE, "Default packaging fee in USD"),
            PolicySeed(PolicyType.COMMERCE, "checkout_policy", PolicyDefaults.CHECKOUT_POLICY, "SAME_CLASS_ONLY or CROSS_CLASS_ALLOWED"),
            PolicySeed(PolicyType.COMMERCE, "order_unpaid_cancel_minutes", PolicyDefaults.ORDER_UNPAID_CANCEL_MINUTES, "Minutes before unpaid order auto-cancels"),
            PolicySeed(PolicyType.COMMERCE, "awaiting_pickup_close_days", PolicyDefaults.AWAITING_PICKUP_CLOSE_DAYS, "Days before awaiting-pickup order auto-closes"),
            PolicySeed(PolicyType.COMMERCE, "inventory_lock_expiry_minutes", PolicyDefaults.INVENTORY_LOCK_EXPIRY_MINUTES, "Minutes before inventory lock expires"),
            PolicySeed(PolicyType.COMMERCE, "idempotency_window_minutes", PolicyDefaults.IDEMPOTENCY_WINDOW_MINUTES, "Idempotency token validity window"),
            PolicySeed(PolicyType.TAX, "default_tax_rate", PolicyDefaults.DEFAULT_TAX_RATE, "Default sales tax rate"),
            PolicySeed(PolicyType.TAX, "default_service_fee_rate", PolicyDefaults.DEFAULT_SERVICE_FEE_RATE, "Default service fee rate"),
            PolicySeed(PolicyType.RISK, "max_refunds_per_learner_per_day", PolicyDefaults.MAX_REFUNDS_PER_LEARNER_PER_DAY, "Max refunds per learner per calendar day"),
            PolicySeed(PolicyType.RISK, "device_fingerprint_enabled", PolicyDefaults.DEVICE_FINGERPRINT_ENABLED, "Enable device fingerprint for risk tagging"),
            PolicySeed(PolicyType.BACKUP, "encryption_required", PolicyDefaults.BACKUP_ENCRYPTION_REQUIRED, "Require AES-256 encryption for backups"),
            PolicySeed(PolicyType.IMPORT_MAPPING, "max_import_size_bytes", PolicyDefaults.MAX_IMPORT_SIZE_BYTES, "Maximum import file size in bytes"),
            PolicySeed(PolicyType.IMPORT_MAPPING, "signature_verification_required", PolicyDefaults.SIGNATURE_VERIFICATION_REQUIRED, "Require signature verification for imports")
        )

        policies.forEach { seed ->
            policyDao.insert(
                PolicyEntity(
                    id = IdGenerator.newId(),
                    type = seed.type.name,
                    key = seed.key,
                    value = seed.value,
                    description = seed.description,
                    version = 1,
                    isActive = true,
                    effectiveFrom = now,
                    effectiveUntil = null,
                    createdBy = "SYSTEM",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    private fun descriptionFor(roleType: RoleType): String = when (roleType) {
        RoleType.ADMINISTRATOR -> "Device owner / super admin with full system access"
        RoleType.REGISTRAR -> "Enrollment operations: approve/reject requests, manage capacity/waitlists"
        RoleType.INSTRUCTOR -> "Teaching lead: class instruction, assignment review, grading"
        RoleType.TEACHING_ASSISTANT -> "Teaching support: assist grading within assigned scope"
        RoleType.LEARNER -> "Student: browse catalog, request enrollment, shop, submit work"
        RoleType.FINANCE_CLERK -> "Financial operator: record payments, issue refunds, reconciliation"
    }

    private data class PolicySeed(
        val type: PolicyType,
        val key: String,
        val value: String,
        val description: String
    )
}
