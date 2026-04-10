package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.Role
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.UserRoleAssignment
import kotlinx.coroutines.flow.Flow

interface RoleRepository {
    fun getAllRoles(): Flow<List<Role>>
    suspend fun getRoleById(id: String): Role?
    suspend fun getRoleByType(type: RoleType): Role?
    suspend fun getPermissionsForUser(userId: String): Set<Permission>
    suspend fun getRoleTypesForUser(userId: String): List<RoleType>
    suspend fun getRoleAssignmentsForUser(userId: String): List<UserRoleAssignment>
    suspend fun assignRole(userId: String, roleType: RoleType, assignedBy: String)
    suspend fun removeRole(userId: String, roleId: String)
    suspend fun userHasPermission(userId: String, permission: Permission): Boolean
}
