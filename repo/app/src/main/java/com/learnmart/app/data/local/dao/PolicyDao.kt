package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.PolicyEntity
import com.learnmart.app.data.local.entity.PolicyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PolicyDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(policy: PolicyEntity)

    @Update
    suspend fun update(policy: PolicyEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(history: PolicyHistoryEntity)

    @Query("SELECT * FROM policies WHERE id = :id")
    suspend fun getById(id: String): PolicyEntity?

    @Query("SELECT * FROM policies WHERE type = :type AND key = :key AND is_active = 1 LIMIT 1")
    suspend fun getActiveByTypeAndKey(type: String, key: String): PolicyEntity?

    @Query("SELECT * FROM policies WHERE type = :type AND is_active = 1 ORDER BY key ASC")
    suspend fun getActiveByType(type: String): List<PolicyEntity>

    @Query("SELECT * FROM policies WHERE is_active = 1 ORDER BY type, key")
    fun getAllActive(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies ORDER BY type, key, version DESC")
    fun getAll(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policy_history WHERE policy_id = :policyId ORDER BY changed_at DESC")
    suspend fun getHistoryForPolicy(policyId: String): List<PolicyHistoryEntity>

    @Query("""
        SELECT * FROM policies
        WHERE type = :type AND key = :key
        ORDER BY version DESC
    """)
    suspend fun getPolicyVersions(type: String, key: String): List<PolicyEntity>
}
