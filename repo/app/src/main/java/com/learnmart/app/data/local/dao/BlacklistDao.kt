package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.BlacklistFlagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(flag: BlacklistFlagEntity)

    @Update
    suspend fun update(flag: BlacklistFlagEntity)

    @Query("SELECT * FROM blacklist_flags WHERE user_id = :userId AND is_active = 1")
    suspend fun getActiveForUser(userId: String): List<BlacklistFlagEntity>

    @Query("SELECT COUNT(*) FROM blacklist_flags WHERE user_id = :userId AND is_active = 1")
    suspend fun isBlacklisted(userId: String): Int

    @Query("SELECT * FROM blacklist_flags WHERE is_active = 1 ORDER BY flagged_at DESC")
    fun getAllActive(): Flow<List<BlacklistFlagEntity>>

    @Query("SELECT * FROM blacklist_flags WHERE user_id = :userId ORDER BY flagged_at DESC")
    suspend fun getAllForUser(userId: String): List<BlacklistFlagEntity>
}
