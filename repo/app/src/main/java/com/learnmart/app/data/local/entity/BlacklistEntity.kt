package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blacklist_flags",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["is_active"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BlacklistFlagEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "flagged_by")
    val flaggedBy: String,
    @ColumnInfo(name = "flagged_at")
    val flaggedAt: Long,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "resolved_by")
    val resolvedBy: String? = null,
    @ColumnInfo(name = "resolution_note")
    val resolutionNote: String? = null
)
