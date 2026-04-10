package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "credential_hash")
    val credentialHash: String,
    @ColumnInfo(name = "credential_salt")
    val credentialSalt: String,
    @ColumnInfo(name = "credential_type")
    val credentialType: String, // PIN or PASSWORD
    @ColumnInfo(name = "status")
    val status: String, // ACTIVE, LOCKED, DISABLED, ARCHIVED
    @ColumnInfo(name = "failed_login_attempts")
    val failedLoginAttempts: Int = 0,
    @ColumnInfo(name = "locked_until")
    val lockedUntil: Long? = null, // epoch millis
    @ColumnInfo(name = "last_login_at")
    val lastLoginAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int = 1
)
