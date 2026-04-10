package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE status != 'ARCHIVED' ORDER BY display_name ASC")
    fun getAllActive(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY display_name ASC")
    fun getAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE status != 'ARCHIVED' ORDER BY display_name ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users WHERE status != 'ARCHIVED'")
    suspend fun countActive(): Int

    @Query("""
        UPDATE users
        SET failed_login_attempts = :attempts,
            locked_until = :lockedUntil,
            updated_at = :updatedAt,
            version = version + 1
        WHERE id = :userId AND version = :currentVersion
    """)
    suspend fun updateLoginAttempts(
        userId: String,
        attempts: Int,
        lockedUntil: Long?,
        updatedAt: Long,
        currentVersion: Int
    ): Int

    @Query("""
        UPDATE users
        SET last_login_at = :lastLoginAt,
            failed_login_attempts = 0,
            locked_until = NULL,
            updated_at = :updatedAt,
            version = version + 1
        WHERE id = :userId
    """)
    suspend fun recordSuccessfulLogin(userId: String, lastLoginAt: Long, updatedAt: Long)

    @Query("""
        UPDATE users
        SET status = :status,
            updated_at = :updatedAt,
            version = version + 1
        WHERE id = :userId AND version = :currentVersion
    """)
    suspend fun updateStatus(userId: String, status: String, updatedAt: Long, currentVersion: Int): Int

    @Query("SELECT * FROM users WHERE display_name LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<UserEntity>
}
