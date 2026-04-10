package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.learnmart.app.data.local.entity.RoleEntity
import com.learnmart.app.data.local.entity.RolePermissionEntity
import com.learnmart.app.data.local.entity.UserRoleAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRole(role: RoleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoles(roles: List<RoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermission(permission: RolePermissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<RolePermissionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUserRoleAssignment(assignment: UserRoleAssignmentEntity)

    @Query("SELECT * FROM roles")
    fun getAllRoles(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE id = :id")
    suspend fun getRoleById(id: String): RoleEntity?

    @Query("SELECT * FROM roles WHERE type = :type")
    suspend fun getRoleByType(type: String): RoleEntity?

    @Query("SELECT * FROM role_permissions WHERE role_id = :roleId")
    suspend fun getPermissionsForRole(roleId: String): List<RolePermissionEntity>

    @Query("""
        SELECT rp.permission FROM role_permissions rp
        INNER JOIN user_role_assignments ura ON rp.role_id = ura.role_id
        WHERE ura.user_id = :userId
    """)
    suspend fun getPermissionsForUser(userId: String): List<String>

    @Query("SELECT * FROM user_role_assignments WHERE user_id = :userId")
    suspend fun getRoleAssignmentsForUser(userId: String): List<UserRoleAssignmentEntity>

    @Query("SELECT ura.role_type FROM user_role_assignments ura WHERE ura.user_id = :userId")
    suspend fun getRoleTypesForUser(userId: String): List<String>

    @Query("DELETE FROM user_role_assignments WHERE user_id = :userId AND role_id = :roleId")
    suspend fun removeUserRoleAssignment(userId: String, roleId: String)

    @Query("SELECT COUNT(*) FROM user_role_assignments WHERE user_id = :userId AND role_id = :roleId")
    suspend fun hasRoleAssignment(userId: String, roleId: String): Int

    @Query("""
        SELECT COUNT(*) FROM role_permissions rp
        INNER JOIN user_role_assignments ura ON rp.role_id = ura.role_id
        WHERE ura.user_id = :userId AND rp.permission = :permission
    """)
    suspend fun userHasPermission(userId: String, permission: String): Int
}
