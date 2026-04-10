package com.learnmart.app.domain.usecase.policy

import com.learnmart.app.domain.model.AuditActionType
import com.learnmart.app.domain.model.AuditEvent
import com.learnmart.app.domain.model.AuditOutcome
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.model.Policy
import com.learnmart.app.domain.model.PolicyType
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManagePolicyUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    suspend fun getAllActivePolicies(): AppResult<Flow<List<Policy>>> {
        if (!checkPermission.hasPermission(Permission.POLICY_MANAGE)) {
            return AppResult.PermissionError("Requires policy.manage permission")
        }
        return AppResult.Success(policyRepository.getAllActivePolicies())
    }

    suspend fun getPoliciesByType(type: PolicyType): AppResult<List<Policy>> {
        if (!checkPermission.hasPermission(Permission.POLICY_MANAGE)) {
            return AppResult.PermissionError("Requires policy.manage permission")
        }
        return AppResult.Success(policyRepository.getActivePoliciesByType(type))
    }

    suspend fun createPolicy(
        type: PolicyType,
        key: String,
        value: String,
        description: String
    ): AppResult<Policy> {
        if (!checkPermission.hasPermission(Permission.POLICY_MANAGE)) {
            return AppResult.PermissionError("Requires policy.manage permission")
        }

        // Validate
        if (key.isBlank()) return AppResult.ValidationError(fieldErrors = mapOf("key" to "Key is required"))
        if (value.isBlank()) return AppResult.ValidationError(fieldErrors = mapOf("value" to "Value is required"))

        // Check if already exists
        val existing = policyRepository.getActivePolicy(type, key)
        if (existing != null) {
            return AppResult.ConflictError("POLICY_EXISTS", "Active policy already exists for $type/$key")
        }

        val now = TimeUtils.nowUtc()
        val policy = Policy(
            id = IdGenerator.newId(),
            type = type,
            key = key,
            value = value,
            description = description,
            version = 1,
            isActive = true,
            effectiveFrom = now,
            effectiveUntil = null,
            createdBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            createdAt = now,
            updatedAt = now
        )

        val created = policyRepository.createPolicy(policy)

        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = sessionManager.getCurrentUserId(),
                actorUsername = null,
                actionType = AuditActionType.POLICY_CREATED,
                targetEntityType = "Policy",
                targetEntityId = created.id,
                beforeSummary = null,
                afterSummary = "$type/$key=$value",
                reason = null,
                sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS,
                timestamp = now,
                metadata = null
            )
        )

        return AppResult.Success(created)
    }

    suspend fun updatePolicy(
        policyId: String,
        newValue: String,
        reason: String?
    ): AppResult<Policy> {
        if (!checkPermission.hasPermission(Permission.POLICY_MANAGE)) {
            return AppResult.PermissionError("Requires policy.manage permission")
        }

        if (newValue.isBlank()) {
            return AppResult.ValidationError(fieldErrors = mapOf("value" to "Value is required"))
        }

        val existing = policyRepository.getPolicyById(policyId)
            ?: return AppResult.NotFoundError("POLICY_NOT_FOUND")

        val changedBy = sessionManager.getCurrentUserId() ?: "SYSTEM"
        val updated = policyRepository.updatePolicy(
            existing.copy(value = newValue),
            changedBy = changedBy,
            reason = reason
        )

        auditRepository.logEvent(
            AuditEvent(
                id = IdGenerator.newId(),
                actorId = sessionManager.getCurrentUserId(),
                actorUsername = null,
                actionType = AuditActionType.POLICY_UPDATED,
                targetEntityType = "Policy",
                targetEntityId = policyId,
                beforeSummary = "${existing.type}/${existing.key}=${existing.value}",
                afterSummary = "${existing.type}/${existing.key}=$newValue",
                reason = reason,
                sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS,
                timestamp = TimeUtils.nowUtc(),
                metadata = null
            )
        )

        return AppResult.Success(updated)
    }

    suspend fun getPolicyValue(type: PolicyType, key: String, default: String): AppResult<String> {
        if (!checkPermission.hasPermission(Permission.POLICY_MANAGE)) {
            return AppResult.PermissionError("Requires policy.manage permission")
        }
        return AppResult.Success(policyRepository.getPolicyValue(type, key, default))
    }

    suspend fun getPolicyById(policyId: String): AppResult<Policy> {
        if (!checkPermission.hasPermission(Permission.POLICY_MANAGE)) {
            return AppResult.PermissionError("Requires policy.manage permission")
        }
        val policy = policyRepository.getPolicyById(policyId)
            ?: return AppResult.NotFoundError("POLICY_NOT_FOUND")
        return AppResult.Success(policy)
    }
}
