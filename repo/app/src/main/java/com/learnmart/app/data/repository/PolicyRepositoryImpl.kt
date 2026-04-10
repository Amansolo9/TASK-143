package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.PolicyDao
import com.learnmart.app.data.local.entity.PolicyEntity
import com.learnmart.app.data.local.entity.PolicyHistoryEntity
import com.learnmart.app.domain.model.Policy
import com.learnmart.app.domain.model.PolicyType
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolicyRepositoryImpl @Inject constructor(
    private val policyDao: PolicyDao
) : PolicyRepository {

    override suspend fun createPolicy(policy: Policy): Policy {
        policyDao.insert(policy.toEntity())
        return policy
    }

    override suspend fun updatePolicy(policy: Policy, changedBy: String, reason: String?): Policy {
        val existing = policyDao.getById(policy.id)
            ?: throw IllegalStateException("Policy not found: ${policy.id}")

        val now = TimeUtils.nowUtc()
        val updatedPolicy = policy.copy(
            version = existing.version + 1,
            updatedAt = now
        )

        // Deactivate old version
        val deactivated = existing.copy(
            isActive = false,
            effectiveUntil = now.toEpochMilli()
        )
        policyDao.update(deactivated)

        // Create new version
        val newEntity = updatedPolicy.toEntity().copy(
            id = IdGenerator.newId(),
            version = existing.version + 1,
            isActive = true,
            effectiveFrom = now.toEpochMilli(),
            effectiveUntil = null,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli()
        )
        policyDao.insert(newEntity)

        // Record history
        val history = PolicyHistoryEntity(
            id = IdGenerator.newId(),
            policyId = policy.id,
            type = policy.type.name,
            key = policy.key,
            oldValue = existing.value,
            newValue = policy.value,
            version = existing.version + 1,
            changedBy = changedBy,
            changedAt = now.toEpochMilli(),
            reason = reason
        )
        policyDao.insertHistory(history)

        return newEntity.toDomain()
    }

    override suspend fun getPolicyById(id: String): Policy? =
        policyDao.getById(id)?.toDomain()

    override suspend fun getActivePolicy(type: PolicyType, key: String): Policy? =
        policyDao.getActiveByTypeAndKey(type.name, key)?.toDomain()

    override suspend fun getActivePoliciesByType(type: PolicyType): List<Policy> =
        policyDao.getActiveByType(type.name).map { it.toDomain() }

    override fun getAllActivePolicies(): Flow<List<Policy>> =
        policyDao.getAllActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getPolicyValue(type: PolicyType, key: String, default: String): String =
        policyDao.getActiveByTypeAndKey(type.name, key)?.value ?: default

    override suspend fun getPolicyIntValue(type: PolicyType, key: String, default: Int): Int =
        policyDao.getActiveByTypeAndKey(type.name, key)?.value?.toIntOrNull() ?: default

    override suspend fun getPolicyLongValue(type: PolicyType, key: String, default: Long): Long =
        policyDao.getActiveByTypeAndKey(type.name, key)?.value?.toLongOrNull() ?: default

    override suspend fun getPolicyBoolValue(type: PolicyType, key: String, default: Boolean): Boolean =
        policyDao.getActiveByTypeAndKey(type.name, key)?.value?.toBooleanStrictOrNull() ?: default

    private fun Policy.toEntity() = PolicyEntity(
        id = id,
        type = type.name,
        key = key,
        value = value,
        description = description,
        version = version,
        isActive = isActive,
        effectiveFrom = effectiveFrom.toEpochMilli(),
        effectiveUntil = effectiveUntil?.toEpochMilli(),
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun PolicyEntity.toDomain() = Policy(
        id = id,
        type = PolicyType.valueOf(type),
        key = key,
        value = value,
        description = description,
        version = version,
        isActive = isActive,
        effectiveFrom = Instant.ofEpochMilli(effectiveFrom),
        effectiveUntil = effectiveUntil?.let { Instant.ofEpochMilli(it) },
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}
