package com.learnmart.app.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.PolicyType
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures and persists a stable local-only device fingerprint for risk tagging.
 *
 * The fingerprint is a SHA-256 hash of device-local attributes (manufacturer,
 * model, board, Android ID) and is stored in SharedPreferences. It is never
 * transmitted off-device. Usage is gated behind the `device_fingerprint_enabled`
 * risk policy flag.
 *
 * Risk events (checkout, refund, login) can call [captureIfEnabled] to record
 * the fingerprint alongside the event's audit log.
 */
@Singleton
class DeviceFingerprintProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val PREFS_NAME = "learnmart_device_fp"
        private const val PREF_FINGERPRINT = "device_fingerprint"
        private const val PREF_CAPTURED_AT = "fingerprint_captured_at"
    }

    /**
     * Returns the current device fingerprint, computing it on first call.
     * Returns null if the policy flag is disabled.
     */
    suspend fun getFingerprint(): String? {
        if (!isEnabled()) return null
        return getOrComputeFingerprint()
    }

    /**
     * If device fingerprinting is enabled, capture and persist the fingerprint
     * and log it as audit metadata for the given risk event.
     */
    suspend fun captureIfEnabled(
        riskEventType: String,
        targetEntityType: String,
        targetEntityId: String
    ): String? {
        if (!isEnabled()) return null

        val fingerprint = getOrComputeFingerprint()
        val now = TimeUtils.nowUtc()

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.SYSTEM_STARTUP, // Generic system event
            targetEntityType = targetEntityType,
            targetEntityId = targetEntityId,
            beforeSummary = null,
            afterSummary = "device_fingerprint=$fingerprint, risk_event=$riskEventType",
            reason = "Device fingerprint captured for risk tagging",
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = """{"device_fingerprint":"$fingerprint","risk_event":"$riskEventType"}"""
        ))

        return fingerprint
    }

    /**
     * Check if device fingerprinting is enabled via policy.
     */
    suspend fun isEnabled(): Boolean {
        return policyRepository.getPolicyBoolValue(
            PolicyType.RISK, "device_fingerprint_enabled", false
        )
    }

    /**
     * Get stored fingerprint or compute and persist a new one.
     */
    private fun getOrComputeFingerprint(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_FINGERPRINT, null)
        if (existing != null) return existing

        val fingerprint = computeFingerprint()
        prefs.edit()
            .putString(PREF_FINGERPRINT, fingerprint)
            .putLong(PREF_CAPTURED_AT, System.currentTimeMillis())
            .apply()
        return fingerprint
    }

    /**
     * Compute a stable device fingerprint from local-only attributes.
     * Uses SHA-256 hash of concatenated device properties.
     */
    private fun computeFingerprint(): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) {
            ""
        }

        val components = listOf(
            Build.MANUFACTURER,
            Build.MODEL,
            Build.BOARD,
            Build.HARDWARE,
            Build.DISPLAY,
            Build.PRODUCT,
            Build.BRAND,
            Build.FINGERPRINT,
            androidId
        )

        val raw = components.joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns the timestamp when the fingerprint was first captured on this device.
     */
    fun getCapturedAt(): Instant? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ts = prefs.getLong(PREF_CAPTURED_AT, 0L)
        return if (ts > 0) Instant.ofEpochMilli(ts) else null
    }
}
