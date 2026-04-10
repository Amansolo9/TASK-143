package com.learnmart.app.domain.model

import java.time.Instant

enum class PolicyType {
    SYSTEM,
    ENROLLMENT,
    COMMERCE,
    TAX,
    FEE,
    RISK,
    BACKUP,
    IMPORT_MAPPING
}

data class Policy(
    val id: String,
    val type: PolicyType,
    val key: String,
    val value: String,
    val description: String,
    val version: Int,
    val isActive: Boolean,
    val effectiveFrom: Instant,
    val effectiveUntil: Instant?,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

object PolicyDefaults {
    // System
    const val SESSION_TIMEOUT_MINUTES = "15"
    const val LOCKOUT_ATTEMPTS = "5"
    const val LOCKOUT_WINDOW_MINUTES = "15"
    const val LOCKOUT_DURATION_MINUTES = "15"
    const val PASSWORD_MIN_LENGTH = "8"

    // Enrollment
    const val ENROLLMENT_REQUEST_EXPIRY_HOURS = "48"
    const val WAITLIST_OFFER_EXPIRY_HOURS = "24"
    const val WAITLIST_ENABLED_DEFAULT = "true"

    // Commerce
    const val MINIMUM_ORDER_TOTAL = "25.00"
    const val PACKAGING_FEE = "1.50"
    const val CHECKOUT_POLICY = "SAME_CLASS_ONLY" // or CROSS_CLASS_ALLOWED
    const val ORDER_UNPAID_CANCEL_MINUTES = "30"
    const val AWAITING_PICKUP_CLOSE_DAYS = "7"
    const val INVENTORY_LOCK_EXPIRY_MINUTES = "10"
    const val IDEMPOTENCY_WINDOW_MINUTES = "5"

    // Tax
    const val DEFAULT_TAX_RATE = "0.00"
    const val DEFAULT_SERVICE_FEE_RATE = "0.00"

    // Risk
    const val MAX_REFUNDS_PER_LEARNER_PER_DAY = "3"
    const val DEVICE_FINGERPRINT_ENABLED = "false"

    // Backup
    const val BACKUP_ENCRYPTION_REQUIRED = "true"

    // Import
    const val MAX_IMPORT_SIZE_BYTES = "26214400" // 25 MB
    const val SIGNATURE_VERIFICATION_REQUIRED = "false"

    // Assessment
    const val PLAGIARISM_HIGH_THRESHOLD = "0.85"
    const val PLAGIARISM_REVIEW_THRESHOLD = "0.70"
}
