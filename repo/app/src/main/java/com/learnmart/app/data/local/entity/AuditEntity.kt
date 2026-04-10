package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_events",
    indices = [
        Index(value = ["actor_id"]),
        Index(value = ["action_type"]),
        Index(value = ["target_entity_type", "target_entity_id"]),
        Index(value = ["timestamp"])
    ]
)
data class AuditEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "actor_id")
    val actorId: String?,
    @ColumnInfo(name = "actor_username")
    val actorUsername: String?,
    @ColumnInfo(name = "action_type")
    val actionType: String,
    @ColumnInfo(name = "target_entity_type")
    val targetEntityType: String?,
    @ColumnInfo(name = "target_entity_id")
    val targetEntityId: String?,
    @ColumnInfo(name = "before_summary")
    val beforeSummary: String?,
    @ColumnInfo(name = "after_summary")
    val afterSummary: String?,
    @ColumnInfo(name = "reason")
    val reason: String?,
    @ColumnInfo(name = "session_id")
    val sessionId: String?,
    @ColumnInfo(name = "outcome")
    val outcome: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "metadata")
    val metadata: String?
)

@Entity(
    tableName = "state_transition_logs",
    indices = [
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["timestamp"])
    ]
)
data class StateTransitionLogEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "from_state")
    val fromState: String,
    @ColumnInfo(name = "to_state")
    val toState: String,
    @ColumnInfo(name = "triggered_by")
    val triggeredBy: String,
    @ColumnInfo(name = "reason")
    val reason: String?,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
