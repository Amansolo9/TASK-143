package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.StateTransitionLog
import kotlinx.coroutines.flow.Flow

interface AuditRepository {
    suspend fun logEvent(event: AuditEvent)
    suspend fun logStateTransition(log: StateTransitionLog)
    suspend fun getEventsPaged(limit: Int, offset: Int): List<AuditEvent>
    suspend fun getEventsByType(actionType: String, limit: Int, offset: Int): List<AuditEvent>
    suspend fun getEventsByActor(actorId: String, limit: Int, offset: Int): List<AuditEvent>
    suspend fun getEventsForEntity(entityType: String, entityId: String): List<AuditEvent>
    suspend fun getEventsByDateRange(startTime: Long, endTime: Long, limit: Int, offset: Int): List<AuditEvent>
    suspend fun getTransitionsForEntity(entityType: String, entityId: String): List<StateTransitionLog>
    fun getRecentEvents(limit: Int): Flow<List<AuditEvent>>
    suspend fun countAll(): Int
}
