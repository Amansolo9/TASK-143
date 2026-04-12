package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class ResolveApprovalUseCase @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    suspend fun approve(taskId: String, notes: String?): AppResult<EnrollmentRequest> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError("Requires enrollment.review")
        }
        return resolveTask(taskId, ApprovalTaskStatus.APPROVED, notes)
    }

    suspend fun reject(taskId: String, notes: String?): AppResult<EnrollmentRequest> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_REVIEW)) {
            return AppResult.PermissionError("Requires enrollment.review")
        }
        if (notes.isNullOrBlank()) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("notes" to "Rejection reason is required")
            )
        }
        return resolveTask(taskId, ApprovalTaskStatus.REJECTED, notes)
    }

    private suspend fun resolveTask(
        taskId: String,
        resolution: ApprovalTaskStatus,
        notes: String?
    ): AppResult<EnrollmentRequest> {
        val task = enrollmentRepository.getApprovalTaskById(taskId)
            ?: return AppResult.NotFoundError("TASK_NOT_FOUND")

        if (task.status != ApprovalTaskStatus.PENDING) {
            return AppResult.ConflictError("ALREADY_RESOLVED", "This task has already been resolved")
        }

        // Enforce assignee/role ownership with strict user-assignment precedence:
        // - If assignedToUserId is set, ONLY that user or an admin can resolve
        // - If assignedToUserId is null, any user with the matching role (or admin) can resolve
        val currentUserId = sessionManager.getCurrentUserId() ?: return AppResult.PermissionError("Not authenticated")
        val currentRoles = checkPermission.getCurrentUserRoles()
        val isAdmin = RoleType.ADMINISTRATOR in currentRoles

        if (task.assignedToUserId != null) {
            // Strict user assignment: only the assigned user or admin
            if (task.assignedToUserId != currentUserId && !isAdmin) {
                return AppResult.PermissionError(
                    "Task is assigned to user ${task.assignedToUserId} — only that user or an administrator can resolve it"
                )
            }
        } else {
            // Role-based assignment: user must have the matching role or be admin
            val hasMatchingRole = task.assignedToRoleType != null && task.assignedToRoleType in currentRoles
            if (!hasMatchingRole && !isAdmin) {
                return AppResult.PermissionError(
                    "Task is assigned to role ${task.assignedToRoleType} — you do not have this role"
                )
            }
        }

        val request = enrollmentRepository.getRequestById(task.enrollmentRequestId)
            ?: return AppResult.NotFoundError("REQUEST_NOT_FOUND")

        if (request.status != EnrollmentRequestStatus.PENDING_APPROVAL) {
            return AppResult.ConflictError("REQUEST_NOT_PENDING",
                "Request is no longer pending approval")
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"

        // Update task
        enrollmentRepository.updateApprovalTask(task.copy(
            status = resolution,
            decidedBy = userId,
            decidedAt = now,
            notes = notes
        ))

        // Record decision event
        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = taskId,
            decision = resolution.name,
            decidedBy = userId,
            reason = notes,
            timestamp = now
        ))

        // Determine flow type and evaluate overall result
        val flow = request.approvalFlowId?.let { enrollmentRepository.getApprovalFlowById(it) }

        if (resolution == ApprovalTaskStatus.REJECTED) {
            // Any rejection in required path rejects the request
            return rejectRequest(request, "Rejected by approver", notes, now)
        }

        // For APPROVED, check if all steps are done
        val allTasks = enrollmentRepository.getTasksForRequest(request.id)

        return when (flow?.flowType) {
            ApprovalFlowType.SERIAL -> handleSerialApproval(request, allTasks, flow, now)
            ApprovalFlowType.PARALLEL -> handleParallelApproval(request, allTasks, now)
            null -> finalizeApproval(request, now) // Single task or no flow
        }
    }

    private suspend fun handleSerialApproval(
        request: EnrollmentRequest,
        allTasks: List<EnrollmentApprovalTask>,
        flow: ApprovalFlowDefinition,
        now: java.time.Instant
    ): AppResult<EnrollmentRequest> {
        val pendingTasks = allTasks.filter { it.status == ApprovalTaskStatus.PENDING }
        if (pendingTasks.isNotEmpty()) {
            // Still have pending tasks - wait
            return AppResult.Success(request, warnings = listOf("Approval step completed. Waiting for remaining steps."))
        }

        // Check if all are approved
        val allApproved = allTasks.all { it.status == ApprovalTaskStatus.APPROVED || it.status == ApprovalTaskStatus.SKIPPED }

        if (allApproved) {
            // Check if there are more steps to create
            val steps = enrollmentRepository.getStepsForFlow(flow.id)
            val completedStepIds = allTasks.map { it.stepDefinitionId }.toSet()
            val nextStep = steps.firstOrNull { it.id !in completedStepIds }

            if (nextStep != null) {
                // Create next serial task
                enrollmentRepository.createApprovalTask(EnrollmentApprovalTask(
                    id = IdGenerator.newId(),
                    enrollmentRequestId = request.id,
                    stepDefinitionId = nextStep.id,
                    assignedToUserId = null,
                    assignedToRoleType = nextStep.approverRoleType,
                    status = ApprovalTaskStatus.PENDING,
                    decidedBy = null,
                    decidedAt = null,
                    notes = null,
                    createdAt = now,
                    expiresAt = TimeUtils.hoursFromNow(48)
                ))
                return AppResult.Success(request, warnings = listOf("Next approval step activated."))
            }

            // All steps done and approved
            return finalizeApproval(request, now)
        }

        return AppResult.Success(request)
    }

    private suspend fun handleParallelApproval(
        request: EnrollmentRequest,
        allTasks: List<EnrollmentApprovalTask>,
        now: java.time.Instant
    ): AppResult<EnrollmentRequest> {
        val pendingTasks = allTasks.filter { it.status == ApprovalTaskStatus.PENDING }
        if (pendingTasks.isNotEmpty()) {
            return AppResult.Success(request, warnings = listOf("Approval step completed. Waiting for remaining approvers."))
        }

        // All resolved - check if unanimous approval (PRD default: no quorum)
        val allApproved = allTasks.all { it.status == ApprovalTaskStatus.APPROVED || it.status == ApprovalTaskStatus.SKIPPED }
        if (allApproved) {
            return finalizeApproval(request, now)
        }

        // Some rejected
        return rejectRequest(request, "Not all approvers approved", null, now)
    }

    private suspend fun finalizeApproval(
        request: EnrollmentRequest,
        now: java.time.Instant
    ): AppResult<EnrollmentRequest> {
        // Check capacity one more time
        val classOffering = courseRepository.getClassOfferingById(request.classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        val enrolledCount = enrollmentRepository.countActiveEnrollmentsForClass(request.classOfferingId)
        if (enrolledCount >= classOffering.hardCapacity) {
            // Over capacity after approval — route to capacity exception workflow
            return routeToCapacityException(request, classOffering, now)
        }

        // Enroll
        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.APPROVED, request.version
        )

        enrollmentRepository.createEnrollmentRecord(EnrollmentRecord(
            id = IdGenerator.newId(),
            learnerId = request.learnerId,
            classOfferingId = request.classOfferingId,
            enrollmentRequestId = request.id,
            enrolledAt = now,
            completedAt = null,
            withdrawnAt = null,
            status = EnrollmentRecordStatus.ACTIVE
        ))

        courseRepository.adjustEnrolledCount(request.classOfferingId, 1)

        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.ENROLLED, request.version + 1
        )

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = "PENDING_APPROVAL",
            toState = "ENROLLED",
            triggeredBy = "SYSTEM",
            reason = "All approval steps completed",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = null,
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_APPROVED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = "status=PENDING_APPROVAL",
            afterSummary = "status=ENROLLED",
            reason = "Fully approved",
            sessionId = null,
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(request.copy(status = EnrollmentRequestStatus.ENROLLED))
    }

    private suspend fun rejectRequest(
        request: EnrollmentRequest,
        reason: String,
        notes: String?,
        now: java.time.Instant
    ): AppResult<EnrollmentRequest> {
        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.REJECTED, request.version
        )

        // Expire remaining pending tasks
        val pendingTasks = enrollmentRepository.getPendingTasksForRequest(request.id)
        pendingTasks.forEach { task ->
            enrollmentRepository.updateApprovalTask(task.copy(
                status = ApprovalTaskStatus.SKIPPED,
                decidedBy = "SYSTEM",
                decidedAt = now,
                notes = "Request rejected - task skipped"
            ))
        }

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = null,
            decision = "REJECTED",
            decidedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = notes ?: reason,
            timestamp = now
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = request.status.name,
            toState = "REJECTED",
            triggeredBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = reason,
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_REJECTED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = "status=${request.status}",
            afterSummary = "status=REJECTED",
            reason = notes ?: reason,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(request.copy(status = EnrollmentRequestStatus.REJECTED))
    }

    /**
     * Route an approved-but-over-capacity request to the explicit capacity exception
     * workflow. An administrator or registrar with ENROLLMENT_OVERRIDE_CAPACITY
     * permission must approve the exception before enrollment proceeds.
     */
    private suspend fun routeToCapacityException(
        request: EnrollmentRequest,
        classOffering: ClassOffering,
        now: java.time.Instant
    ): AppResult<EnrollmentRequest> {
        // Check if over-capacity exceptions are policy-enabled
        val allowExceptions = policyRepository.getPolicyBoolValue(
            PolicyType.ENROLLMENT, "allow_over_capacity_exceptions", true
        )

        if (!allowExceptions) {
            // Exceptions not allowed — fall back to waitlist or reject
            if (classOffering.waitlistEnabled) {
                val maxPos = enrollmentRepository.getMaxWaitlistPosition(classOffering.id)
                enrollmentRepository.createWaitlistEntry(WaitlistEntry(
                    id = IdGenerator.newId(),
                    learnerId = request.learnerId,
                    classOfferingId = request.classOfferingId,
                    enrollmentRequestId = request.id,
                    position = maxPos + 1,
                    priorityTier = request.priorityTier,
                    addedAt = now, offeredAt = null, offerExpiresAt = null,
                    status = WaitlistStatus.ACTIVE
                ))
                enrollmentRepository.updateRequestStatus(
                    request.id, EnrollmentRequestStatus.WAITLISTED, request.version
                )
                return AppResult.Success(
                    request.copy(status = EnrollmentRequestStatus.WAITLISTED),
                    warnings = listOf("Approved but class is at capacity. Added to waitlist.")
                )
            }
            return rejectRequest(request, "Class at capacity after approval", null, now)
        }

        // Transition to PENDING_CAPACITY_EXCEPTION
        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION, request.version
        )

        // Create exception decision record for audit trail
        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = null,
            decision = "PENDING_CAPACITY_EXCEPTION",
            decidedBy = "SYSTEM",
            reason = "Approved but class at capacity (${classOffering.hardCapacity}). " +
                "Requires ENROLLMENT_OVERRIDE_CAPACITY permission to proceed.",
            timestamp = now
        ))

        val expiryHours = policyRepository.getPolicyLongValue(
            PolicyType.ENROLLMENT, "capacity_exception_expiry_hours", 48
        )

        // Create an approval task for the exception
        enrollmentRepository.createApprovalTask(EnrollmentApprovalTask(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            stepDefinitionId = "CAPACITY_EXCEPTION",
            assignedToUserId = null,
            assignedToRoleType = RoleType.REGISTRAR,
            status = ApprovalTaskStatus.PENDING,
            decidedBy = null,
            decidedAt = null,
            notes = "Over-capacity exception: class at ${classOffering.hardCapacity} seats",
            createdAt = now,
            expiresAt = TimeUtils.hoursFromNow(expiryHours)
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = request.status.name,
            toState = "PENDING_CAPACITY_EXCEPTION",
            triggeredBy = "SYSTEM",
            reason = "Approved but over hard capacity. Routed to exception review.",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = null, actorUsername = null,
            actionType = AuditActionType.CAPACITY_OVERRIDE,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = "status=${request.status}",
            afterSummary = "status=PENDING_CAPACITY_EXCEPTION",
            reason = "Class at capacity. Exception approval required.",
            sessionId = null,
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(
            request.copy(status = EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION),
            warnings = listOf("Approved but class is at capacity. Routed to over-capacity exception review.")
        )
    }

    /**
     * Approve a capacity exception — allows enrollment above the hard cap.
     * Requires ENROLLMENT_OVERRIDE_CAPACITY permission.
     */
    suspend fun approveCapacityException(
        taskId: String,
        reason: String
    ): AppResult<EnrollmentRequest> {
        if (!checkPermission.hasPermission(Permission.ENROLLMENT_OVERRIDE_CAPACITY)) {
            return AppResult.PermissionError("Requires enrollment.override_capacity")
        }
        if (reason.isBlank()) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("reason" to "Reason is required for capacity exceptions")
            )
        }

        val task = enrollmentRepository.getApprovalTaskById(taskId)
            ?: return AppResult.NotFoundError("TASK_NOT_FOUND")

        val request = enrollmentRepository.getRequestById(task.enrollmentRequestId)
            ?: return AppResult.NotFoundError("REQUEST_NOT_FOUND")

        if (request.status != EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION) {
            return AppResult.ConflictError("NOT_PENDING_EXCEPTION",
                "Request is not pending a capacity exception")
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"

        // Resolve the exception task
        enrollmentRepository.updateApprovalTask(task.copy(
            status = ApprovalTaskStatus.APPROVED,
            decidedBy = userId,
            decidedAt = now,
            notes = reason
        ))

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = taskId,
            decision = "CAPACITY_EXCEPTION_APPROVED",
            decidedBy = userId,
            reason = reason,
            timestamp = now
        ))

        // Proceed to enrollment
        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.APPROVED, request.version
        )

        enrollmentRepository.createEnrollmentRecord(EnrollmentRecord(
            id = IdGenerator.newId(),
            learnerId = request.learnerId,
            classOfferingId = request.classOfferingId,
            enrollmentRequestId = request.id,
            enrolledAt = now,
            completedAt = null,
            withdrawnAt = null,
            status = EnrollmentRecordStatus.ACTIVE
        ))

        courseRepository.adjustEnrolledCount(request.classOfferingId, 1)

        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.ENROLLED, request.version + 1
        )

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = "PENDING_CAPACITY_EXCEPTION",
            toState = "ENROLLED",
            triggeredBy = userId,
            reason = "Capacity exception approved: $reason",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = userId, actorUsername = null,
            actionType = AuditActionType.CAPACITY_OVERRIDE,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = "status=PENDING_CAPACITY_EXCEPTION",
            afterSummary = "status=ENROLLED (over-capacity exception)",
            reason = reason,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(request.copy(status = EnrollmentRequestStatus.ENROLLED))
    }
}
