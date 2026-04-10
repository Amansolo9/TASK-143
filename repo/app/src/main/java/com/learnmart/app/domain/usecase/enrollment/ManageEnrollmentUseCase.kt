package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageEnrollmentUseCase @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val waitlistPromotion: WaitlistPromotionUseCase,
    private val sessionManager: SessionManager
) {
    suspend fun getPendingRequests(): AppResult<Flow<List<EnrollmentRequest>>> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError("Requires enrollment.review permission")
        }
        return AppResult.Success(enrollmentRepository.getPendingRequests())
    }

    suspend fun getRequestById(id: String): AppResult<EnrollmentRequest> {
        val request = enrollmentRepository.getRequestById(id)
            ?: return AppResult.NotFoundError("REQUEST_NOT_FOUND")
        // Learners can see their own requests; reviewers can see any
        val currentUserId = sessionManager.getCurrentUserId()
        if (request.learnerId != currentUserId &&
            !checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError("Requires enrollment.review permission")
        }
        return AppResult.Success(request)
    }

    suspend fun getRequestsForClass(classOfferingId: String): AppResult<List<EnrollmentRequest>> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError("Requires enrollment.review permission")
        }
        return AppResult.Success(enrollmentRepository.getRequestsForClassOffering(classOfferingId))
    }

    suspend fun getRequestsForLearner(learnerId: String): AppResult<List<EnrollmentRequest>> {
        val currentUserId = sessionManager.getCurrentUserId()
        if (learnerId != currentUserId &&
            !checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError("Requires enrollment.review permission")
        }
        return AppResult.Success(enrollmentRepository.getRequestsForLearner(learnerId))
    }

    suspend fun getMyRequests(): List<EnrollmentRequest> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return enrollmentRepository.getRequestsForLearner(userId)
    }

    suspend fun getWaitlistForClass(classOfferingId: String): List<WaitlistEntry> =
        enrollmentRepository.getActiveWaitlistForClass(classOfferingId)

    suspend fun getEnrollmentsForClass(classOfferingId: String): List<EnrollmentRecord> =
        enrollmentRepository.getActiveEnrollmentsForClass(classOfferingId)

    suspend fun getMyEnrollments(): List<EnrollmentRecord> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return enrollmentRepository.getEnrollmentsForLearner(userId)
    }

    suspend fun getPendingTasksForCurrentUser(): List<EnrollmentApprovalTask> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()

        // Get tasks assigned to user directly
        val directTasks = enrollmentRepository.getPendingTasksForUser(userId)

        // Also get tasks assigned to user's roles
        val roles = checkPermission.getCurrentUserRoles()
        val roleTasks = roles.flatMap { roleType ->
            enrollmentRepository.getPendingTasksByRole(roleType)
        }

        return (directTasks + roleTasks).distinctBy { it.id }
    }

    suspend fun getDecisionHistory(requestId: String): List<EnrollmentDecisionEvent> =
        enrollmentRepository.getDecisionEventsForRequest(requestId)

    suspend fun withdrawEnrollment(
        enrollmentRecordId: String,
        reason: String
    ): AppResult<EnrollmentRecord> {
        if (!checkPermission.hasAnyPermission(Permission.ENROLLMENT_REVIEW, Permission.ENROLLMENT_REQUEST)) {
            return AppResult.PermissionError()
        }

        if (reason.isBlank()) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("reason" to "Reason is required for withdrawal")
            )
        }

        val record = enrollmentRepository.getEnrollmentRecordById(enrollmentRecordId)
            ?: return AppResult.NotFoundError("ENROLLMENT_NOT_FOUND")

        if (record.status != EnrollmentRecordStatus.ACTIVE) {
            return AppResult.ValidationError(
                globalErrors = listOf("Enrollment is not active")
            )
        }

        val now = TimeUtils.nowUtc()
        val updated = record.copy(
            status = EnrollmentRecordStatus.WITHDRAWN,
            withdrawnAt = now
        )
        enrollmentRepository.updateEnrollmentRecord(updated)

        // Update request
        enrollmentRepository.updateRequestStatus(
            record.enrollmentRequestId, EnrollmentRequestStatus.WITHDRAWN, 0
        )

        // Decrement enrolled count
        courseRepository.adjustEnrolledCount(record.classOfferingId, -1)

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRecord",
            entityId = enrollmentRecordId,
            fromState = "ACTIVE",
            toState = "WITHDRAWN",
            triggeredBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = reason,
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_WITHDRAWN,
            targetEntityType = "EnrollmentRecord",
            targetEntityId = enrollmentRecordId,
            beforeSummary = "status=ACTIVE",
            afterSummary = "status=WITHDRAWN",
            reason = reason,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        // Promote next in waitlist since a seat freed up
        waitlistPromotion.promoteNextIfAvailable(record.classOfferingId)

        return AppResult.Success(updated)
    }

    suspend fun cancelRequest(requestId: String): AppResult<EnrollmentRequest> {
        val request = enrollmentRepository.getRequestById(requestId)
            ?: return AppResult.NotFoundError("REQUEST_NOT_FOUND")

        val currentUserId = sessionManager.getCurrentUserId()
        if (request.learnerId != currentUserId &&
            !checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError()
        }

        if (!request.status.canTransitionTo(EnrollmentRequestStatus.CANCELLED)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot cancel request in ${request.status} state")
            )
        }

        val now = TimeUtils.nowUtc()
        enrollmentRepository.updateRequestStatus(
            requestId, EnrollmentRequestStatus.CANCELLED, request.version
        )

        // Cancel related waitlist entry if any
        val waitlistEntry = enrollmentRepository.getActiveWaitlistForLearnerAndClass(
            request.learnerId, request.classOfferingId
        )
        if (waitlistEntry != null) {
            enrollmentRepository.updateWaitlistEntry(waitlistEntry.copy(
                status = WaitlistStatus.CANCELLED
            ))
        }

        // Expire pending approval tasks
        val pendingTasks = enrollmentRepository.getPendingTasksForRequest(requestId)
        pendingTasks.forEach { task ->
            enrollmentRepository.updateApprovalTask(task.copy(
                status = ApprovalTaskStatus.SKIPPED,
                decidedBy = "SYSTEM",
                decidedAt = now,
                notes = "Request cancelled"
            ))
        }

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = requestId,
            fromState = request.status.name,
            toState = "CANCELLED",
            triggeredBy = currentUserId ?: "SYSTEM",
            reason = "User cancelled",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = currentUserId,
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_CANCELLED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = requestId,
            beforeSummary = "status=${request.status}",
            afterSummary = "status=CANCELLED",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(request.copy(status = EnrollmentRequestStatus.CANCELLED))
    }

    /**
     * Create or update an approval flow definition for a class offering.
     */
    suspend fun createApprovalFlow(
        classOfferingId: String?,
        name: String,
        flowType: ApprovalFlowType,
        isDefault: Boolean,
        steps: List<Pair<Int, RoleType>> // (stepOrder, approverRoleType)
    ): AppResult<ApprovalFlowDefinition> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError()
        }

        if (name.isBlank()) {
            return AppResult.ValidationError(fieldErrors = mapOf("name" to "Name is required"))
        }
        if (steps.isEmpty()) {
            return AppResult.ValidationError(globalErrors = listOf("At least one approval step is required"))
        }

        val now = TimeUtils.nowUtc()
        val flow = ApprovalFlowDefinition(
            id = IdGenerator.newId(),
            classOfferingId = classOfferingId,
            name = name,
            flowType = flowType,
            isDefault = isDefault,
            createdAt = now
        )
        enrollmentRepository.createApprovalFlowDefinition(flow)

        val stepDefinitions = steps.map { (order, roleType) ->
            ApprovalStepDefinition(
                id = IdGenerator.newId(),
                flowId = flow.id,
                stepOrder = order,
                approverRoleType = roleType,
                isRequired = true,
                createdAt = now
            )
        }
        enrollmentRepository.createApprovalStepDefinitions(stepDefinitions)

        return AppResult.Success(flow)
    }
}
