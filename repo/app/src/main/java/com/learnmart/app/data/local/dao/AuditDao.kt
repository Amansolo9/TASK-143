package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.learnmart.app.data.local.entity.AuditEventEntity
import com.learnmart.app.data.local.entity.StateTransitionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditEvent(event: AuditEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStateTransition(log: StateTransitionLogEntity)

    @Query("""
        SELECT * FROM audit_events
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAuditEventsPaged(limit: Int, offset: Int): List<AuditEventEntity>

    @Query("""
        SELECT * FROM audit_events
        WHERE action_type = :actionType
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAuditEventsByType(actionType: String, limit: Int, offset: Int): List<AuditEventEntity>

    @Query("""
        SELECT * FROM audit_events
        WHERE actor_id = :actorId
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAuditEventsByActor(actorId: String, limit: Int, offset: Int): List<AuditEventEntity>

    @Query("""
        SELECT * FROM audit_events
        WHERE target_entity_type = :entityType AND target_entity_id = :entityId
        ORDER BY timestamp DESC
    """)
    suspend fun getAuditEventsForEntity(entityType: String, entityId: String): List<AuditEventEntity>

    @Query("""
        SELECT * FROM audit_events
        WHERE timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAuditEventsByDateRange(startTime: Long, endTime: Long, limit: Int, offset: Int): List<AuditEventEntity>

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM audit_events WHERE action_type = :actionType")
    suspend fun countByType(actionType: String): Int

    @Query("""
        SELECT * FROM state_transition_logs
        WHERE entity_type = :entityType AND entity_id = :entityId
        ORDER BY timestamp ASC
    """)
    suspend fun getTransitionsForEntity(entityType: String, entityId: String): List<StateTransitionLogEntity>

    @Query("""
        SELECT * FROM audit_events
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun getRecentAuditEvents(limit: Int): Flow<List<AuditEventEntity>>
}
