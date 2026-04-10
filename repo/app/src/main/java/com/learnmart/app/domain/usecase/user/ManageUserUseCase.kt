package com.learnmart.app.domain.usecase.user

import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.User
import com.learnmart.app.domain.model.UserStatus
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    suspend fun getAllActiveUsers(): AppResult<Flow<List<User>>> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError("Requires user.manage permission")
        }
        return AppResult.Success(userRepository.getAllActiveUsers())
    }

    suspend fun getUserById(id: String): AppResult<User> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError("Requires user.manage permission")
        }
        val user = userRepository.getUserById(id)
            ?: return AppResult.NotFoundError("USER_NOT_FOUND")
        return AppResult.Success(user)
    }

    suspend fun unlockUser(userId: String): AppResult<User> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError()
        }

        val user = userRepository.getUserById(userId)
            ?: return AppResult.NotFoundError()

        if (user.status != UserStatus.LOCKED) {
            return AppResult.ValidationError(globalErrors = listOf("User is not locked"))
        }

        userRepository.updateLoginAttempts(userId, 0, null, user.version)
        userRepository.updateStatus(userId, UserStatus.ACTIVE.name, user.version)

        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = sessionManager.getCurrentUserId(),
                actorUsername = null,
                actionType = AuditActionType.ACCOUNT_UNLOCKED,
                targetEntityType = "User",
                targetEntityId = userId,
                beforeSummary = "status=LOCKED",
                afterSummary = "status=ACTIVE",
                reason = "Manual unlock by administrator",
                sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )

        return AppResult.Success(userRepository.getUserById(userId)!!)
    }

    suspend fun disableUser(userId: String, reason: String): AppResult<User> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError()
        }

        val user = userRepository.getUserById(userId)
            ?: return AppResult.NotFoundError()

        userRepository.updateStatus(userId, UserStatus.DISABLED.name, user.version)

        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = sessionManager.getCurrentUserId(),
                actorUsername = null,
                actionType = AuditActionType.USER_DISABLED,
                targetEntityType = "User",
                targetEntityId = userId,
                beforeSummary = "status=${user.status}",
                afterSummary = "status=DISABLED",
                reason = reason,
                sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )

        return AppResult.Success(userRepository.getUserById(userId)!!)
    }

    suspend fun assignRole(userId: String, roleType: RoleType): AppResult<Unit> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError()
        }

        val user = userRepository.getUserById(userId)
            ?: return AppResult.NotFoundError()

        roleRepository.assignRole(userId, roleType, sessionManager.getCurrentUserId() ?: "SYSTEM")

        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = sessionManager.getCurrentUserId(),
                actorUsername = null,
                actionType = AuditActionType.ROLE_ASSIGNED,
                targetEntityType = "User",
                targetEntityId = userId,
                beforeSummary = null,
                afterSummary = "role=$roleType",
                reason = null,
                sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )

        return AppResult.Success(Unit)
    }

    suspend fun getRolesForUser(userId: String): AppResult<List<RoleType>> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError("Requires user.manage permission")
        }
        return AppResult.Success(roleRepository.getRoleTypesForUser(userId))
    }

    suspend fun searchUsers(query: String): AppResult<List<User>> {
        if (!checkPermission.hasPermission(Permission.USER_MANAGE)) {
            return AppResult.PermissionError("Requires user.manage permission")
        }
        return AppResult.Success(userRepository.searchUsers(query))
    }
}
