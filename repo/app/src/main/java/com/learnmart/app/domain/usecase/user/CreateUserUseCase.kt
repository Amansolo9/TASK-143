package com.learnmart.app.domain.usecase.user

import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.CredentialType
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.User
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.CredentialManager
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

data class CreateUserRequest(
    val username: String,
    val displayName: String,
    val credential: String,
    val credentialType: CredentialType,
    val roleType: RoleType
)

class CreateUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val credentialManager: CredentialManager,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(request: CreateUserRequest): AppResult<User> {
        // Check permission
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError("Requires user.manage permission")
        }

        // Validate input
        val errors = mutableMapOf<String, String>()
        if (request.username.isBlank()) errors["username"] = "Username is required"
        if (request.displayName.isBlank()) errors["displayName"] = "Display name is required"
        if (request.credential.isBlank()) errors["credential"] = "Credential is required"

        // Validate credential strength
        val credErrors = when (request.credentialType) {
            CredentialType.PASSWORD -> credentialManager.validatePasswordStrength(request.credential)
            CredentialType.PIN -> credentialManager.validatePinStrength(request.credential)
        }
        if (credErrors.isNotEmpty()) errors["credential"] = credErrors.first()

        if (errors.isNotEmpty()) {
            return AppResult.ValidationError(fieldErrors = errors)
        }

        // Check uniqueness
        val existing = userRepository.getUserByUsername(request.username)
        if (existing != null) {
            return AppResult.ConflictError("DUPLICATE_USERNAME", "Username already exists")
        }

        // Create user
        val user = userRepository.createUser(
            username = request.username,
            displayName = request.displayName,
            credential = request.credential,
            credentialType = request.credentialType.name
        )

        // Assign role
        roleRepository.assignRole(user.id, request.roleType, sessionManager.getCurrentUserId() ?: "SYSTEM")

        // Audit
        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = sessionManager.getCurrentUserId(),
                actorUsername = null,
                actionType = AuditActionType.USER_CREATED,
                targetEntityType = "User",
                targetEntityId = user.id,
                beforeSummary = null,
                afterSummary = "username=${user.username}, role=${request.roleType}",
                reason = null,
                sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )

        return AppResult.Success(user)
    }
}
