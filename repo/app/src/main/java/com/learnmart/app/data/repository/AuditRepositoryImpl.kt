package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.AuditDao
import com.learnmart.app.data.local.entity.AuditEventEntity
import com.learnmart.app.data.local.entity.StateTransitionLogEntity
import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.StateTransitionLog
import com.learnmart.app.domain.repository.AuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao,
    private val sqlDelightAuditRepo: SqlDelightAuditRepository
) : AuditRepository {

    override suspend fun logEvent(event: AuditEvent) {
        auditDao.insertAuditEvent(event.toEntity())
        try {
            sqlDelightAuditRepo.syncAuditEvent(event)
        } catch (_: Exception) {
            // SQLDelight mirror is best-effort; Room is authoritative
        }
    }

    override suspend fun logStateTransition(log: StateTransitionLog) {
        auditDao.insertStateTransition(log.toEntity())
    }

    override suspend fun getEventsPaged(limit: Int, offset: Int): List<AuditEvent> =
        auditDao.getAuditEventsPaged(limit, offset).map { it.toDomain() }

    override suspend fun getEventsByType(actionType: String, limit: Int, offset: Int): List<AuditEvent> =
        auditDao.getAuditEventsByType(actionType, limit, offset).map { it.toDomain() }

    override suspend fun getEventsByActor(actorId: String, limit: Int, offset: Int): List<AuditEvent> =
        auditDao.getAuditEventsByActor(actorId, limit, offset).map { it.toDomain() }

    override suspend fun getEventsForEntity(entityType: String, entityId: String): List<AuditEvent> =
        auditDao.getAuditEventsForEntity(entityType, entityId).map { it.toDomain() }

    override suspend fun getEventsByDateRange(startTime: Long, endTime: Long, limit: Int, offset: Int): List<AuditEvent> =
        auditDao.getAuditEventsByDateRange(startTime, endTime, limit, offset).map { it.toDomain() }

    override suspend fun getTransitionsForEntity(entityType: String, entityId: String): List<StateTransitionLog> =
        auditDao.getTransitionsForEntity(entityType, entityId).map { it.toDomain() }

    override fun getRecentEvents(limit: Int): Flow<List<AuditEvent>> =
        auditDao.getRecentAuditEvents(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun countAll(): Int = auditDao.countAll()

    private fun AuditEvent.toEntity() = AuditEventEntity(
        id = id,
        actorId = actorId,
        actorUsername = actorUsername,
        actionType = actionType.name,
        targetEntityType = targetEntityType,
        targetEntityId = targetEntityId,
        beforeSummary = beforeSummary,
        afterSummary = afterSummary,
        reason = reason,
        sessionId = sessionId,
        outcome = outcome.name,
        timestamp = timestamp.toEpochMilli(),
        metadata = metadata
    )

    private fun AuditEventEntity.toDomain() = AuditEvent(
        id = id,
        actorId = actorId,
        actorUsername = actorUsername,
        actionType = AuditActionType.valueOf(actionType),
        targetEntityType = targetEntityType,
        targetEntityId = targetEntityId,
        beforeSummary = beforeSummary,
        afterSummary = afterSummary,
        reason = reason,
        sessionId = sessionId,
        outcome = AuditOutcome.valueOf(outcome),
        timestamp = Instant.ofEpochMilli(timestamp),
        metadata = metadata
    )

    private fun StateTransitionLog.toEntity() = StateTransitionLogEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        fromState = fromState,
        toState = toState,
        triggeredBy = triggeredBy,
        reason = reason,
        timestamp = timestamp.toEpochMilli()
    )

    private fun StateTransitionLogEntity.toDomain() = StateTransitionLog(
        id = id,
        entityType = entityType,
        entityId = entityId,
        fromState = fromState,
        toState = toState,
        triggeredBy = triggeredBy,
        reason = reason,
        timestamp = Instant.ofEpochMilli(timestamp)
    )
}
