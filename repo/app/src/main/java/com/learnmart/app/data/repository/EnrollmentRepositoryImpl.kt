package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.EnrollmentDao
import com.learnmart.app.data.local.entity.ApprovalFlowDefinitionEntity
import com.learnmart.app.data.local.entity.ApprovalStepDefinitionEntity
import com.learnmart.app.data.local.entity.EnrollmentApprovalTaskEntity
import com.learnmart.app.data.local.entity.EnrollmentDecisionEventEntity
import com.learnmart.app.data.local.entity.EnrollmentEligibilitySnapshotEntity
import com.learnmart.app.data.local.entity.EnrollmentExceptionEntity
import com.learnmart.app.data.local.entity.EnrollmentRecordEntity
import com.learnmart.app.data.local.entity.EnrollmentRequestEntity
import com.learnmart.app.data.local.entity.WaitlistEntryEntity
import com.learnmart.app.domain.model.ApprovalFlowDefinition
import com.learnmart.app.domain.model.ApprovalFlowType
import com.learnmart.app.domain.model.ApprovalStepDefinition
import com.learnmart.app.domain.model.ApprovalTaskStatus
import com.learnmart.app.domain.model.EnrollmentApprovalTask
import com.learnmart.app.domain.model.EnrollmentDecisionEvent
import com.learnmart.app.domain.model.EnrollmentEligibilitySnapshot
import com.learnmart.app.domain.model.EnrollmentException
import com.learnmart.app.domain.model.EnrollmentRecord
import com.learnmart.app.domain.model.EnrollmentRecordStatus
import com.learnmart.app.domain.model.EnrollmentRequest
import com.learnmart.app.domain.model.EnrollmentRequestStatus
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.WaitlistEntry
import com.learnmart.app.domain.model.WaitlistStatus
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnrollmentRepositoryImpl @Inject constructor(
    private val enrollmentDao: EnrollmentDao
) : EnrollmentRepository {

    // ── EnrollmentRequest ───────────────────────────────────────────────

    override suspend fun createRequest(request: EnrollmentRequest): EnrollmentRequest {
        val entity = request.toEntity()
        enrollmentDao.insertEnrollmentRequest(entity)
        return entity.toDomain()
    }

    override suspend fun updateRequest(request: EnrollmentRequest): Boolean {
        val existing = enrollmentDao.getEnrollmentRequestById(request.id) ?: return false
        enrollmentDao.updateEnrollmentRequest(request.toEntity())
        return true
    }

    override suspend fun getRequestById(id: String): EnrollmentRequest? =
        enrollmentDao.getEnrollmentRequestById(id)?.toDomain()

    override suspend fun getRequestsForClassOffering(classOfferingId: String): List<EnrollmentRequest> =
        enrollmentDao.getRequestsForClassOffering(classOfferingId).map { it.toDomain() }

    override suspend fun getRequestsForLearner(learnerId: String): List<EnrollmentRequest> =
        enrollmentDao.getRequestsForLearner(learnerId).map { it.toDomain() }

    override suspend fun getRequestsByClassAndStatus(
        classOfferingId: String,
        status: EnrollmentRequestStatus
    ): List<EnrollmentRequest> =
        enrollmentDao.getRequestsByClassAndStatus(classOfferingId, status.name).map { it.toDomain() }

    override suspend fun countActivePendingRequests(learnerId: String, classOfferingId: String): Int =
        enrollmentDao.countActivePendingRequestsForLearnerAndClass(learnerId, classOfferingId)

    override suspend fun getExpiredRequests(currentTime: Long): List<EnrollmentRequest> =
        enrollmentDao.getExpiredRequests(currentTime).map { it.toDomain() }

    override suspend fun updateRequestStatus(
        id: String,
        status: EnrollmentRequestStatus,
        currentVersion: Int
    ): Boolean {
        val updatedAt = TimeUtils.nowUtc().toEpochMilli()
        val rows = enrollmentDao.updateRequestStatus(id, status.name, updatedAt, currentVersion)
        return rows > 0
    }

    override fun getPendingRequests(): Flow<List<EnrollmentRequest>> =
        enrollmentDao.getPendingRequests().map { list -> list.map { it.toDomain() } }

    // ── EligibilitySnapshot ─────────────────────────────────────────────

    override suspend fun createEligibilitySnapshot(snapshot: EnrollmentEligibilitySnapshot) {
        enrollmentDao.insertEligibilitySnapshot(snapshot.toEntity())
    }

    override suspend fun getEligibilitySnapshot(requestId: String): EnrollmentEligibilitySnapshot? =
        enrollmentDao.getEligibilitySnapshot(requestId)?.toDomain()

    // ── ApprovalFlow ────────────────────────────────────────────────────

    override suspend fun createApprovalFlowDefinition(flow: ApprovalFlowDefinition): ApprovalFlowDefinition {
        val entity = flow.toEntity()
        enrollmentDao.insertApprovalFlowDefinition(entity)
        return entity.toDomain()
    }

    override suspend fun getApprovalFlowById(id: String): ApprovalFlowDefinition? =
        enrollmentDao.getApprovalFlowById(id)?.toDomain()

    override suspend fun getApprovalFlowsForClass(classOfferingId: String): List<ApprovalFlowDefinition> =
        enrollmentDao.getApprovalFlowsForClass(classOfferingId).map { it.toDomain() }

    override suspend fun getDefaultApprovalFlow(): ApprovalFlowDefinition? =
        enrollmentDao.getDefaultApprovalFlow()?.toDomain()

    // ── ApprovalStep ────────────────────────────────────────────────────

    override suspend fun createApprovalStepDefinitions(steps: List<ApprovalStepDefinition>) {
        enrollmentDao.insertApprovalStepDefinitions(steps.map { it.toEntity() })
    }

    override suspend fun getStepsForFlow(flowId: String): List<ApprovalStepDefinition> =
        enrollmentDao.getStepsForFlow(flowId).map { it.toDomain() }

    // ── ApprovalTask ────────────────────────────────────────────────────

    override suspend fun createApprovalTask(task: EnrollmentApprovalTask) {
        enrollmentDao.insertApprovalTask(task.toEntity())
    }

    override suspend fun createApprovalTasks(tasks: List<EnrollmentApprovalTask>) {
        enrollmentDao.insertApprovalTasks(tasks.map { it.toEntity() })
    }

    override suspend fun updateApprovalTask(task: EnrollmentApprovalTask) {
        enrollmentDao.updateApprovalTask(task.toEntity())
    }

    override suspend fun getApprovalTaskById(id: String): EnrollmentApprovalTask? =
        enrollmentDao.getApprovalTaskById(id)?.toDomain()

    override suspend fun getTasksForRequest(requestId: String): List<EnrollmentApprovalTask> =
        enrollmentDao.getTasksForRequest(requestId).map { it.toDomain() }

    override suspend fun getPendingTasksForRequest(requestId: String): List<EnrollmentApprovalTask> =
        enrollmentDao.getPendingTasksForRequest(requestId).map { it.toDomain() }

    override suspend fun getPendingTasksByRole(roleType: RoleType): List<EnrollmentApprovalTask> =
        enrollmentDao.getPendingTasksByRole(roleType.name).map { it.toDomain() }

    override suspend fun getPendingTasksForUser(userId: String): List<EnrollmentApprovalTask> =
        enrollmentDao.getPendingTasksForUser(userId).map { it.toDomain() }

    override suspend fun getExpiredTasks(currentTime: Long): List<EnrollmentApprovalTask> =
        enrollmentDao.getExpiredTasks(currentTime).map { it.toDomain() }

    // ── DecisionEvent ───────────────────────────────────────────────────

    override suspend fun createDecisionEvent(event: EnrollmentDecisionEvent) {
        enrollmentDao.insertDecisionEvent(event.toEntity())
    }

    override suspend fun getDecisionEventsForRequest(requestId: String): List<EnrollmentDecisionEvent> =
        enrollmentDao.getDecisionEventsForRequest(requestId).map { it.toDomain() }

    // ── Waitlist ────────────────────────────────────────────────────────

    override suspend fun createWaitlistEntry(entry: WaitlistEntry) {
        enrollmentDao.insertWaitlistEntry(entry.toEntity())
    }

    override suspend fun updateWaitlistEntry(entry: WaitlistEntry) {
        enrollmentDao.updateWaitlistEntry(entry.toEntity())
    }

    override suspend fun getWaitlistEntryById(id: String): WaitlistEntry? =
        enrollmentDao.getWaitlistEntryById(id)?.toDomain()

    override suspend fun getActiveWaitlistForClass(classOfferingId: String): List<WaitlistEntry> =
        enrollmentDao.getActiveWaitlistForClass(classOfferingId).map { it.toDomain() }

    override suspend fun getNextWaitlistEntry(classOfferingId: String): WaitlistEntry? =
        enrollmentDao.getNextWaitlistEntry(classOfferingId)?.toDomain()

    override suspend fun countActiveWaitlistForClass(classOfferingId: String): Int =
        enrollmentDao.countActiveWaitlistForClass(classOfferingId)

    override suspend fun getActiveWaitlistForLearnerAndClass(
        learnerId: String,
        classOfferingId: String
    ): WaitlistEntry? =
        enrollmentDao.getActiveWaitlistForLearnerAndClass(learnerId, classOfferingId)?.toDomain()

    override suspend fun getExpiredOffers(currentTime: Long): List<WaitlistEntry> =
        enrollmentDao.getExpiredOffers(currentTime).map { it.toDomain() }

    override suspend fun getMaxWaitlistPosition(classOfferingId: String): Int =
        enrollmentDao.getMaxWaitlistPosition(classOfferingId) ?: 0

    // ── EnrollmentRecord ────────────────────────────────────────────────

    override suspend fun createEnrollmentRecord(record: EnrollmentRecord) {
        enrollmentDao.insertEnrollmentRecord(record.toEntity())
    }

    override suspend fun updateEnrollmentRecord(record: EnrollmentRecord) {
        enrollmentDao.updateEnrollmentRecord(record.toEntity())
    }

    override suspend fun getEnrollmentRecordById(id: String): EnrollmentRecord? =
        enrollmentDao.getEnrollmentRecordById(id)?.toDomain()

    override suspend fun getActiveEnrollment(learnerId: String, classOfferingId: String): EnrollmentRecord? =
        enrollmentDao.getActiveEnrollment(learnerId, classOfferingId)?.toDomain()

    override suspend fun getActiveEnrollmentsForClass(classOfferingId: String): List<EnrollmentRecord> =
        enrollmentDao.getActiveEnrollmentsForClass(classOfferingId).map { it.toDomain() }

    override suspend fun countActiveEnrollmentsForClass(classOfferingId: String): Int =
        enrollmentDao.countActiveEnrollmentsForClass(classOfferingId)

    override suspend fun getEnrollmentsForLearner(learnerId: String): List<EnrollmentRecord> =
        enrollmentDao.getEnrollmentsForLearner(learnerId).map { it.toDomain() }

    // ── EnrollmentException ─────────────────────────────────────────────

    override suspend fun createEnrollmentException(exception: EnrollmentException) {
        enrollmentDao.insertEnrollmentException(exception.toEntity())
    }

    override suspend fun getExceptionsForRequest(requestId: String): List<EnrollmentException> =
        enrollmentDao.getExceptionsForRequest(requestId).map { it.toDomain() }

    // ════════════════════════════════════════════════════════════════════
    // Entity <-> Domain mapping extensions
    // ════════════════════════════════════════════════════════════════════

    // ── EnrollmentRequest ───────────────────────────────────────────────

    private fun EnrollmentRequestEntity.toDomain() = EnrollmentRequest(
        id = id,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        status = EnrollmentRequestStatus.valueOf(status),
        priorityTier = priorityTier,
        submittedAt = submittedAt?.let { Instant.ofEpochMilli(it) },
        expiresAt = expiresAt?.let { Instant.ofEpochMilli(it) },
        approvalFlowId = approvalFlowId,
        eligibilitySnapshotId = eligibilitySnapshotId,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun EnrollmentRequest.toEntity() = EnrollmentRequestEntity(
        id = id,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        status = status.name,
        priorityTier = priorityTier,
        submittedAt = submittedAt?.toEpochMilli(),
        expiresAt = expiresAt?.toEpochMilli(),
        approvalFlowId = approvalFlowId,
        eligibilitySnapshotId = eligibilitySnapshotId,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    // ── EligibilitySnapshot ─────────────────────────────────────────────

    private fun EnrollmentEligibilitySnapshotEntity.toDomain() = EnrollmentEligibilitySnapshot(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        isEligible = isEligible,
        eligibilityFlags = eligibilityFlags,
        evaluatedAt = Instant.ofEpochMilli(evaluatedAt)
    )

    private fun EnrollmentEligibilitySnapshot.toEntity() = EnrollmentEligibilitySnapshotEntity(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        isEligible = isEligible,
        eligibilityFlags = eligibilityFlags,
        evaluatedAt = evaluatedAt.toEpochMilli()
    )

    // ── ApprovalFlowDefinition ──────────────────────────────────────────

    private fun ApprovalFlowDefinitionEntity.toDomain() = ApprovalFlowDefinition(
        id = id,
        classOfferingId = classOfferingId,
        name = name,
        flowType = ApprovalFlowType.valueOf(flowType),
        isDefault = isDefault,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun ApprovalFlowDefinition.toEntity() = ApprovalFlowDefinitionEntity(
        id = id,
        classOfferingId = classOfferingId,
        name = name,
        flowType = flowType.name,
        isDefault = isDefault,
        createdAt = createdAt.toEpochMilli()
    )

    // ── ApprovalStepDefinition ──────────────────────────────────────────

    private fun ApprovalStepDefinitionEntity.toDomain() = ApprovalStepDefinition(
        id = id,
        flowId = flowId,
        stepOrder = stepOrder,
        approverRoleType = RoleType.valueOf(approverRoleType),
        isRequired = isRequired,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun ApprovalStepDefinition.toEntity() = ApprovalStepDefinitionEntity(
        id = id,
        flowId = flowId,
        stepOrder = stepOrder,
        approverRoleType = approverRoleType.name,
        isRequired = isRequired,
        createdAt = createdAt.toEpochMilli()
    )

    // ── EnrollmentApprovalTask ──────────────────────────────────────────

    private fun EnrollmentApprovalTaskEntity.toDomain() = EnrollmentApprovalTask(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        stepDefinitionId = stepDefinitionId,
        assignedToUserId = assignedToUserId,
        assignedToRoleType = RoleType.valueOf(assignedToRoleType),
        status = ApprovalTaskStatus.valueOf(status),
        decidedBy = decidedBy,
        decidedAt = decidedAt?.let { Instant.ofEpochMilli(it) },
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
        expiresAt = Instant.ofEpochMilli(expiresAt)
    )

    private fun EnrollmentApprovalTask.toEntity() = EnrollmentApprovalTaskEntity(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        stepDefinitionId = stepDefinitionId,
        assignedToUserId = assignedToUserId,
        assignedToRoleType = assignedToRoleType.name,
        status = status.name,
        decidedBy = decidedBy,
        decidedAt = decidedAt?.toEpochMilli(),
        notes = notes,
        createdAt = createdAt.toEpochMilli(),
        expiresAt = expiresAt.toEpochMilli()
    )

    // ── EnrollmentDecisionEvent ─────────────────────────────────────────

    private fun EnrollmentDecisionEventEntity.toDomain() = EnrollmentDecisionEvent(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        approvalTaskId = approvalTaskId,
        decision = decision,
        decidedBy = decidedBy,
        reason = reason,
        timestamp = Instant.ofEpochMilli(timestamp)
    )

    private fun EnrollmentDecisionEvent.toEntity() = EnrollmentDecisionEventEntity(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        approvalTaskId = approvalTaskId,
        decision = decision,
        decidedBy = decidedBy,
        reason = reason,
        timestamp = timestamp.toEpochMilli()
    )

    // ── WaitlistEntry ───────────────────────────────────────────────────

    private fun WaitlistEntryEntity.toDomain() = WaitlistEntry(
        id = id,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        enrollmentRequestId = enrollmentRequestId,
        position = position,
        priorityTier = priorityTier,
        addedAt = Instant.ofEpochMilli(addedAt),
        offeredAt = offeredAt?.let { Instant.ofEpochMilli(it) },
        offerExpiresAt = offerExpiresAt?.let { Instant.ofEpochMilli(it) },
        status = WaitlistStatus.valueOf(status)
    )

    private fun WaitlistEntry.toEntity() = WaitlistEntryEntity(
        id = id,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        enrollmentRequestId = enrollmentRequestId,
        position = position,
        priorityTier = priorityTier,
        addedAt = addedAt.toEpochMilli(),
        offeredAt = offeredAt?.toEpochMilli(),
        offerExpiresAt = offerExpiresAt?.toEpochMilli(),
        status = status.name
    )

    // ── EnrollmentRecord ────────────────────────────────────────────────

    private fun EnrollmentRecordEntity.toDomain() = EnrollmentRecord(
        id = id,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        enrollmentRequestId = enrollmentRequestId,
        enrolledAt = Instant.ofEpochMilli(enrolledAt),
        completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
        withdrawnAt = withdrawnAt?.let { Instant.ofEpochMilli(it) },
        status = EnrollmentRecordStatus.valueOf(status)
    )

    private fun EnrollmentRecord.toEntity() = EnrollmentRecordEntity(
        id = id,
        learnerId = learnerId,
        classOfferingId = classOfferingId,
        enrollmentRequestId = enrollmentRequestId,
        enrolledAt = enrolledAt.toEpochMilli(),
        completedAt = completedAt?.toEpochMilli(),
        withdrawnAt = withdrawnAt?.toEpochMilli(),
        status = status.name
    )

    // ── EnrollmentException ─────────────────────────────────────────────

    private fun EnrollmentExceptionEntity.toDomain() = EnrollmentException(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        classOfferingId = classOfferingId,
        exceptionType = exceptionType,
        reason = reason,
        approvedBy = approvedBy,
        approvedAt = Instant.ofEpochMilli(approvedAt)
    )

    private fun EnrollmentException.toEntity() = EnrollmentExceptionEntity(
        id = id,
        enrollmentRequestId = enrollmentRequestId,
        classOfferingId = classOfferingId,
        exceptionType = exceptionType,
        reason = reason,
        approvedBy = approvedBy,
        approvedAt = approvedAt.toEpochMilli()
    )
}
