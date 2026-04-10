package com.learnmart.app.data.repository

import app.cash.sqldelight.db.SqlDriver
import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Read-optimized audit query repository backed by SQLDelight driver.
 * Uses the SqlDriver directly with type-safe column access.
 * Data is mirrored from Room's authoritative store on each audit write.
 *
 * Boundary: Room owns the primary encrypted DB and all schema/transactions.
 * SQLDelight (via SqlDriver) owns this separate query-optimized DB for audit reads.
 */
@Singleton
class SqlDelightAuditRepository @Inject constructor(
    @Named("auditSqlDriver") private val driver: SqlDriver
) {
    fun syncAuditEvent(event: AuditEvent) {
        driver.execute(null, """
            INSERT OR REPLACE INTO audit_events(id, actor_id, actor_username, action_type, 
                target_entity_type, target_entity_id, before_summary, after_summary, 
                reason, session_id, outcome, timestamp, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(), 13) {
            bindString(0, event.id)
            bindString(1, event.actorId)
            bindString(2, event.actorUsername)
            bindString(3, event.actionType.name)
            bindString(4, event.targetEntityType)
            bindString(5, event.targetEntityId)
            bindString(6, event.beforeSummary)
            bindString(7, event.afterSummary)
            bindString(8, event.reason)
            bindString(9, event.sessionId)
            bindString(10, event.outcome.name)
            bindLong(11, event.timestamp.toEpochMilli())
            bindString(12, event.metadata)
        }
    }

    fun getRecentEvents(limit: Long, offset: Long): List<AuditEvent> {
        val results = mutableListOf<AuditEvent>()
        driver.executeQuery(null, 
            "SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT ? OFFSET ?", 
            { cursor ->
                while (cursor.next().value) {
                    results.add(cursorToAuditEvent(cursor))
                }
                app.cash.sqldelight.db.QueryResult.Value(results)
            }, 2) {
            bindLong(0, limit)
            bindLong(1, offset)
        }
        return results
    }

    fun getActionTypeCounts(): Map<String, Long> {
        val counts = mutableMapOf<String, Long>()
        driver.executeQuery(null,
            "SELECT action_type, COUNT(*) FROM audit_events GROUP BY action_type ORDER BY COUNT(*) DESC",
            { cursor ->
                while (cursor.next().value) {
                    val actionType = cursor.getString(0) ?: continue
                    val count = cursor.getLong(1) ?: 0L
                    counts[actionType] = count
                }
                app.cash.sqldelight.db.QueryResult.Value(counts)
            }, 0)
        return counts
    }

    private fun cursorToAuditEvent(cursor: app.cash.sqldelight.db.SqlCursor): AuditEvent {
        return AuditEvent(
            id = cursor.getString(0)!!,
            actorId = cursor.getString(1),
            actorUsername = cursor.getString(2),
            actionType = AuditActionType.valueOf(cursor.getString(3)!!),
            targetEntityType = cursor.getString(4),
            targetEntityId = cursor.getString(5),
            beforeSummary = cursor.getString(6),
            afterSummary = cursor.getString(7),
            reason = cursor.getString(8),
            sessionId = cursor.getString(9),
            outcome = AuditOutcome.valueOf(cursor.getString(10)!!),
            timestamp = Instant.ofEpochMilli(cursor.getLong(11)!!),
            metadata = cursor.getString(12)
        )
    }
}
