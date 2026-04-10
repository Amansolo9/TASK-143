package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.RoleDao
import com.learnmart.app.data.local.entity.RoleEntity
import com.learnmart.app.data.local.entity.UserRoleAssignmentEntity
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.Role
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.UserRoleAssignment
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoleRepositoryImpl @Inject constructor(
    private val roleDao: RoleDao
) : RoleRepository {

    override fun getAllRoles(): Flow<List<Role>> =
        roleDao.getAllRoles().map { list -> list.map { it.toDomain() } }

    override suspend fun getRoleById(id: String): Role? =
        roleDao.getRoleById(id)?.toDomain()

    override suspend fun getRoleByType(type: RoleType): Role? =
        roleDao.getRoleByType(type.name)?.toDomain()

    override suspend fun getPermissionsForUser(userId: String): Set<Permission> =
        roleDao.getPermissionsForUser(userId)
            .mapNotNull { Permission.fromCapability(it) }
            .toSet()

    override suspend fun getRoleTypesForUser(userId: String): List<RoleType> =
        roleDao.getRoleTypesForUser(userId).map { RoleType.valueOf(it) }

    override suspend fun getRoleAssignmentsForUser(userId: String): List<UserRoleAssignment> =
        roleDao.getRoleAssignmentsForUser(userId).map { it.toDomain() }

    override suspend fun assignRole(userId: String, roleType: RoleType, assignedBy: String) {
        val role = roleDao.getRoleByType(roleType.name)
            ?: throw IllegalStateException("Role type $roleType not found")

        if (roleDao.hasRoleAssignment(userId, role.id) > 0) return

        val assignment = UserRoleAssignmentEntity(
            id = IdGenerator.newId(),
            userId = userId,
            roleId = role.id,
            roleType = roleType.name,
            assignedAt = TimeUtils.nowUtc().toEpochMilli(),
            assignedBy = assignedBy
        )
        roleDao.insertUserRoleAssignment(assignment)
    }

    override suspend fun removeRole(userId: String, roleId: String) {
        roleDao.removeUserRoleAssignment(userId, roleId)
    }

    override suspend fun userHasPermission(userId: String, permission: Permission): Boolean =
        roleDao.userHasPermission(userId, permission.capability) > 0

    private fun RoleEntity.toDomain() = Role(
        id = id,
        type = RoleType.valueOf(type),
        name = name,
        description = description,
        isSystem = isSystem,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun UserRoleAssignmentEntity.toDomain() = UserRoleAssignment(
        id = id,
        userId = userId,
        roleId = roleId,
        roleType = RoleType.valueOf(roleType),
        assignedAt = Instant.ofEpochMilli(assignedAt),
        assignedBy = assignedBy
    )
}
