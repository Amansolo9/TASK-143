package com.learnmart.app.domain.usecase.auth

import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import javax.inject.Inject

class CheckPermissionUseCase @Inject constructor(
    private val roleRepository: RoleRepository,
    private val sessionManager: SessionManager
) {
    suspend fun hasPermission(permission: Permission): Boolean {
        val userId = sessionManager.getCurrentUserId() ?: return false
        return roleRepository.userHasPermission(userId, permission)
    }

    suspend fun hasAnyPermission(vararg permissions: Permission): Boolean {
        val userId = sessionManager.getCurrentUserId() ?: return false
        return permissions.any { roleRepository.userHasPermission(userId, it) }
    }

    suspend fun hasAllPermissions(vararg permissions: Permission): Boolean {
        val userId = sessionManager.getCurrentUserId() ?: return false
        return permissions.all { roleRepository.userHasPermission(userId, it) }
    }

    suspend fun getCurrentUserRoles(): List<RoleType> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return roleRepository.getRoleTypesForUser(userId)
    }

    suspend fun getCurrentUserPermissions(): Set<Permission> {
        val userId = sessionManager.getCurrentUserId() ?: return emptySet()
        return roleRepository.getPermissionsForUser(userId)
    }

    suspend fun <T> requirePermission(
        permission: Permission,
        block: suspend () -> AppResult<T>
    ): AppResult<T> {
        if (!hasPermission(permission)) {
            return AppResult.PermissionError("Requires ${permission.capability}")
        }
        return block()
    }
}
