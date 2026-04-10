package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.Policy
import com.learnmart.app.domain.model.PolicyType
import kotlinx.coroutines.flow.Flow

interface PolicyRepository {
    suspend fun createPolicy(policy: Policy): Policy
    suspend fun updatePolicy(policy: Policy, changedBy: String, reason: String?): Policy
    suspend fun getPolicyById(id: String): Policy?
    suspend fun getActivePolicy(type: PolicyType, key: String): Policy?
    suspend fun getActivePoliciesByType(type: PolicyType): List<Policy>
    fun getAllActivePolicies(): Flow<List<Policy>>
    suspend fun getPolicyValue(type: PolicyType, key: String, default: String): String
    suspend fun getPolicyIntValue(type: PolicyType, key: String, default: Int): Int
    suspend fun getPolicyLongValue(type: PolicyType, key: String, default: Long): Long
    suspend fun getPolicyBoolValue(type: PolicyType, key: String, default: Boolean): Boolean
}
