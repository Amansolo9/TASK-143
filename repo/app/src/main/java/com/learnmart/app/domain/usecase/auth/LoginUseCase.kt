package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.LoginRequest
import com.learnmart.app.domain.model.LoginResult
import com.learnmart.app.domain.model.PolicyDefaults
import com.learnmart.app.domain.model.PolicyType
import com.learnmart.app.domain.model.UserStatus
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(request: LoginRequest): AppResult<LoginResult> {
        // Validate input
        if (request.username.isBlank()) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("username" to "Username is required")
            )
        }
        if (request.credential.isBlank()) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("credential" to "Credential is required")
            )
        }

        // Find user
        val user = userRepository.getUserByUsername(request.username)
            ?: run {
                logLoginEvent(null, null, AuditOutcome.FAILURE, "User not found: ${request.username}")
                return AppResult.ValidationError(
                    globalErrors = listOf("Invalid username or credential")
                )
            }

        // Check if disabled/archived
        if (user.status == UserStatus.DISABLED || user.status == UserStatus.ARCHIVED) {
            logLoginEvent(user.id, user.username, AuditOutcome.DENIED, "Account is ${user.status}")
            return AppResult.ValidationError(
                globalErrors = listOf("Account is not active")
            )
        }

        // Check lockout
        val lockoutAttempts = policyRepository.getPolicyIntValue(
            PolicyType.SYSTEM, "lockout_attempts",
            PolicyDefaults.LOCKOUT_ATTEMPTS.toInt()
        )
        val lockoutWindowMinutes = policyRepository.getPolicyLongValue(
            PolicyType.SYSTEM, "lockout_window_minutes",
            PolicyDefaults.LOCKOUT_WINDOW_MINUTES.toLong()
        )
        val lockoutDurationMinutes = policyRepository.getPolicyLongValue(
            PolicyType.SYSTEM, "lockout_duration_minutes",
            PolicyDefaults.LOCKOUT_DURATION_MINUTES.toLong()
        )

        if (user.status == UserStatus.LOCKED) {
            val lockedUntil = user.lockedUntil
            if (lockedUntil != null && !TimeUtils.isExpired(lockedUntil)) {
                logLoginEvent(user.id, user.username, AuditOutcome.DENIED, "Account is locked")
                return AppResult.ValidationError(
                    globalErrors = listOf("Account is locked. Try again later.")
                )
            }
            // Lock expired, reset status
            userRepository.updateStatus(user.id, UserStatus.ACTIVE.name, user.version)
        }

        // Verify credential
        val isValid = userRepository.verifyCredential(user.id, request.credential)
        if (!isValid) {
            val newAttempts = user.failedLoginAttempts + 1
            if (newAttempts >= lockoutAttempts) {
                // Lock the account
                val lockUntil = TimeUtils.minutesFromNow(lockoutDurationMinutes)
                userRepository.updateLoginAttempts(user.id, newAttempts, lockUntil, user.version)
                userRepository.updateStatus(user.id, UserStatus.LOCKED.name, user.version)

                logLoginEvent(user.id, user.username, AuditOutcome.FAILURE,
                    "Account locked after $newAttempts failed attempts")

                auditRepository.logEvent(
                    AuditEvent(
                        id = IdGenerator.newId(),
                        actorId = user.id,
                        actorUsername = user.username,
                        actionType = AuditActionType.ACCOUNT_LOCKED,
                        targetEntityType = "User",
                        targetEntityId = user.id,
                        beforeSummary = "status=ACTIVE",
                        afterSummary = "status=LOCKED",
                        reason = "Exceeded max login attempts ($lockoutAttempts)",
                        sessionId = null,
                        outcome = AuditOutcome.SUCCESS,
                        timestamp = TimeUtils.nowUtc(),
                        metadata = null
                    )
                )

                return AppResult.ValidationError(
                    globalErrors = listOf("Account locked due to too many failed attempts")
                )
            } else {
                userRepository.updateLoginAttempts(user.id, newAttempts, null, user.version)
                logLoginEvent(user.id, user.username, AuditOutcome.FAILURE,
                    "Invalid credential (attempt $newAttempts/$lockoutAttempts)")

                return AppResult.ValidationError(
                    globalErrors = listOf("Invalid username or credential")
                )
            }
        }

        // Successful login
        userRepository.recordSuccessfulLogin(user.id)

        // Configure session timeout from policy
        val sessionTimeoutMinutes = policyRepository.getPolicyLongValue(
            PolicyType.SYSTEM, "session_timeout_minutes",
            PolicyDefaults.SESSION_TIMEOUT_MINUTES.toLong()
        )
        sessionManager.setSessionTimeout(sessionTimeoutMinutes)

        val session = sessionManager.createSession(user.id)
        val roles = roleRepository.getRoleTypesForUser(user.id)

        logLoginEvent(user.id, user.username, AuditOutcome.SUCCESS, null, session.id)

        val updatedUser = userRepository.getUserById(user.id)!!

        return AppResult.Success(
            LoginResult(
                user = updatedUser,
                session = session,
                roles = roles
            )
        )
    }

    private suspend fun logLoginEvent(
        userId: String?,
        username: String?,
        outcome: AuditOutcome,
        reason: String?,
        sessionId: String? = null
    ) {
        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = userId,
                actorUsername = username,
                actionType = if (outcome == AuditOutcome.SUCCESS) AuditActionType.LOGIN_SUCCESS else AuditActionType.LOGIN_FAILURE,
                targetEntityType = "User",
                targetEntityId = userId,
                beforeSummary = null,
                afterSummary = null,
                reason = reason,
                sessionId = sessionId,
                outcome = outcome,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )
    }
}
