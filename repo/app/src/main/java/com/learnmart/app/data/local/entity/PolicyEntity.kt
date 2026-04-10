package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "policies",
    indices = [
        Index(value = ["type", "key"]),
        Index(value = ["is_active"])
    ]
)
data class PolicyEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "type")
    val type: String, // PolicyType enum name
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long,
    @ColumnInfo(name = "effective_until")
    val effectiveUntil: Long?,
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "policy_history",
    indices = [
        Index(value = ["policy_id"]),
        Index(value = ["changed_at"])
    ]
)
data class PolicyHistoryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "policy_id")
    val policyId: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "old_value")
    val oldValue: String?,
    @ColumnInfo(name = "new_value")
    val newValue: String,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "changed_by")
    val changedBy: String,
    @ColumnInfo(name = "changed_at")
    val changedAt: Long,
    @ColumnInfo(name = "reason")
    val reason: String?
)
