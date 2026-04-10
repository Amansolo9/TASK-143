package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.learnmart.app.data.local.entity.SessionEntity

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM session_records WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM session_records WHERE user_id = :userId AND is_active = 1 ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveSessionForUser(userId: String): SessionEntity?

    @Query("""
        UPDATE session_records
        SET last_activity_at = :lastActivityAt,
            expires_at = :expiresAt
        WHERE id = :sessionId AND is_active = 1
    """)
    suspend fun refreshSession(sessionId: String, lastActivityAt: Long, expiresAt: Long)

    @Query("""
        UPDATE session_records
        SET is_active = 0,
            terminated_reason = :reason
        WHERE id = :sessionId
    """)
    suspend fun terminateSession(sessionId: String, reason: String)

    @Query("""
        UPDATE session_records
        SET is_active = 0,
            terminated_reason = 'SESSION_EXPIRED'
        WHERE is_active = 1 AND expires_at < :currentTime
    """)
    suspend fun expireOldSessions(currentTime: Long): Int

    @Query("UPDATE session_records SET is_active = 0, terminated_reason = 'LOGOUT' WHERE user_id = :userId AND is_active = 1")
    suspend fun terminateAllForUser(userId: String)
}
