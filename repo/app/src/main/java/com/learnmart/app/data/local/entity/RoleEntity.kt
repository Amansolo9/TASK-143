package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "roles")
data class RoleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "type")
    val type: String, // RoleType enum name
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "role_permissions",
    indices = [
        Index(value = ["role_id", "permission"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["role_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RolePermissionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "role_id")
    val roleId: String,
    @ColumnInfo(name = "permission")
    val permission: String, // Permission enum capability string
    @ColumnInfo(name = "granted_at")
    val grantedAt: Long
)

@Entity(
    tableName = "user_role_assignments",
    indices = [
        Index(value = ["user_id", "role_id"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["role_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["role_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserRoleAssignmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "role_id")
    val roleId: String,
    @ColumnInfo(name = "role_type")
    val roleType: String,
    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long,
    @ColumnInfo(name = "assigned_by")
    val assignedBy: String
)
