package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.domain.model.*
import com.learnmart.app.data.local.dao.BlacklistDao
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class SubmitEnrollmentUseCase @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val blacklistDao: BlacklistDao,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(
        classOfferingId: String,
        priorityTier: Int = 0
    ): AppResult<EnrollmentRequest> {
        val learnerId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        // Validate class offering
        val classOffering = courseRepository.getClassOfferingById(classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        if (classOffering.status != ClassOfferingStatus.OPEN) {
            return AppResult.ValidationError(
                globalErrors = listOf("Class is not open for enrollment (status: ${classOffering.status})")
            )
        }

        // Check for duplicate active request
        val activeCount = enrollmentRepository.countActivePendingRequests(learnerId, classOfferingId)
        if (activeCount > 0) {
            return AppResult.ConflictError("DUPLICATE_REQUEST",
                "You already have an active enrollment request for this class")
        }

        // Check for existing active enrollment
        val existing = enrollmentRepository.getActiveEnrollment(learnerId, classOfferingId)
        if (existing != null) {
            return AppResult.ConflictError("ALREADY_ENROLLED",
                "You are already enrolled in this class")
        }

        // Blacklist check - required before enrollment approval finalization
        if (blacklistDao.isBlacklisted(learnerId) > 0) {
            return AppResult.ValidationError(
                globalErrors = listOf("Enrollment denied: learner is blacklisted")
            )
        }

        val now = TimeUtils.nowUtc()
        val expiryHours = policyRepository.getPolicyLongValue(
            PolicyType.ENROLLMENT, "request_expiry_hours", 48
        )
        val expiresAt = TimeUtils.hoursFromNow(expiryHours)

        // Create eligibility snapshot
        val eligibilitySnapshot = evaluateEligibility(learnerId, classOfferingId)

        // Create request
        val requestId = IdGenerator.newId()
        val request = EnrollmentRequest(
            id = requestId,
            learnerId = learnerId,
            classOfferingId = classOfferingId,
            status = EnrollmentRequestStatus.SUBMITTED,
            priorityTier = priorityTier,
            submittedAt = now,
            expiresAt = expiresAt,
            approvalFlowId = null,
            eligibilitySnapshotId = eligibilitySnapshot.id,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        enrollmentRepository.createRequest(request)
        enrollmentRepository.createEligibilitySnapshot(eligibilitySnapshot)

        // Audit
        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = learnerId,
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_REQUESTED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = requestId,
            beforeSummary = null,
            afterSummary = "classOfferingId=$classOfferingId, status=SUBMITTED",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = requestId,
            fromState = "NONE",
            toState = "SUBMITTED",
            triggeredBy = learnerId,
            reason = null,
            timestamp = now
        ))

        // Check capacity and route
        val enrolledCount = enrollmentRepository.countActiveEnrollmentsForClass(classOfferingId)
        if (enrolledCount >= classOffering.hardCapacity) {
            // Over capacity - check waitlist
            val waitlistEnabled = classOffering.waitlistEnabled
            if (waitlistEnabled) {
                return addToWaitlist(request, classOffering)
            } else {
                return rejectOverCapacity(request)
            }
        }

        // Route to approval flow
        return routeToApproval(request)
    }

    private suspend fun evaluateEligibility(
        learnerId: String, classOfferingId: String
    ): EnrollmentEligibilitySnapshot {
        // Basic eligibility check - extensible for future rules
        return EnrollmentEligibilitySnapshot(
            id = IdGenerator.newId(),
            enrollmentRequestId = "", // Set after request creation
            learnerId = learnerId,
            classOfferingId = classOfferingId,
            isEligible = true,
            eligibilityFlags = """{"no_blacklist":true,"class_open":true}""",
            evaluatedAt = TimeUtils.nowUtc()
        )
    }

    private suspend fun addToWaitlist(
        request: EnrollmentRequest,
        classOffering: ClassOffering
    ): AppResult<EnrollmentRequest> {
        val now = TimeUtils.nowUtc()
        val maxPos = enrollmentRepository.getMaxWaitlistPosition(classOffering.id)

        enrollmentRepository.createWaitlistEntry(WaitlistEntry(
            id = IdGenerator.newId(),
            learnerId = request.learnerId,
            classOfferingId = classOffering.id,
            enrollmentRequestId = request.id,
            position = maxPos + 1,
            priorityTier = request.priorityTier,
            addedAt = now,
            offeredAt = null,
            offerExpiresAt = null,
            status = WaitlistStatus.ACTIVE
        ))

        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.WAITLISTED, request.version
        )

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = "SUBMITTED",
            toState = "WAITLISTED",
            triggeredBy = "SYSTEM",
            reason = "Class at capacity, added to waitlist",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = request.learnerId,
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_WAITLISTED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = "status=SUBMITTED",
            afterSummary = "status=WAITLISTED",
            reason = "Class at capacity",
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(
            request.copy(status = EnrollmentRequestStatus.WAITLISTED),
            warnings = listOf("Class is at capacity. You have been added to the waitlist.")
        )
    }

    private suspend fun rejectOverCapacity(request: EnrollmentRequest): AppResult<EnrollmentRequest> {
        val now = TimeUtils.nowUtc()
        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.REJECTED, request.version
        )

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = null,
            decision = "REJECTED",
            decidedBy = "SYSTEM",
            reason = "Class at capacity and waitlist is disabled",
            timestamp = now
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = "SUBMITTED",
            toState = "REJECTED",
            triggeredBy = "SYSTEM",
            reason = "Over capacity, no waitlist",
            timestamp = now
        ))

        return AppResult.ValidationError(
            globalErrors = listOf("Class is at full capacity and waitlist is not enabled")
        )
    }

    private suspend fun routeToApproval(request: EnrollmentRequest): AppResult<EnrollmentRequest> {
        // Find approval flow
        val flow = enrollmentRepository.getApprovalFlowsForClass(request.classOfferingId).firstOrNull()
            ?: enrollmentRepository.getDefaultApprovalFlow()

        if (flow == null) {
            // No approval flow defined - auto-approve
            return autoApprove(request)
        }

        val steps = enrollmentRepository.getStepsForFlow(flow.id)
        if (steps.isEmpty()) {
            return autoApprove(request)
        }

        val now = TimeUtils.nowUtc()
        val expiryHours = 48L // Matches request expiry

        // Update request with approval flow reference
        enrollmentRepository.updateRequest(request.copy(
            status = EnrollmentRequestStatus.PENDING_APPROVAL,
            approvalFlowId = flow.id,
            updatedAt = now,
            version = request.version + 1
        ))

        // Create approval tasks based on flow type
        when (flow.flowType) {
            ApprovalFlowType.SERIAL -> {
                // Only create task for first step
                val firstStep = steps.first()
                enrollmentRepository.createApprovalTask(EnrollmentApprovalTask(
                    id = IdGenerator.newId(),
                    enrollmentRequestId = request.id,
                    stepDefinitionId = firstStep.id,
                    assignedToUserId = null,
                    assignedToRoleType = firstStep.approverRoleType,
                    status = ApprovalTaskStatus.PENDING,
                    decidedBy = null,
                    decidedAt = null,
                    notes = null,
                    createdAt = now,
                    expiresAt = TimeUtils.hoursFromNow(expiryHours)
                ))
            }
            ApprovalFlowType.PARALLEL -> {
                // Create tasks for all steps
                val tasks = steps.map { step ->
                    EnrollmentApprovalTask(
                        id = IdGenerator.newId(),
                        enrollmentRequestId = request.id,
                        stepDefinitionId = step.id,
                        assignedToUserId = null,
                        assignedToRoleType = step.approverRoleType,
                        status = ApprovalTaskStatus.PENDING,
                        decidedBy = null,
                        decidedAt = null,
                        notes = null,
                        createdAt = now,
                        expiresAt = TimeUtils.hoursFromNow(expiryHours)
                    )
                }
                enrollmentRepository.createApprovalTasks(tasks)
            }
        }

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = "SUBMITTED",
            toState = "PENDING_APPROVAL",
            triggeredBy = "SYSTEM",
            reason = "Routed to ${flow.flowType} approval flow: ${flow.name}",
            timestamp = now
        ))

        return AppResult.Success(
            request.copy(
                status = EnrollmentRequestStatus.PENDING_APPROVAL,
                approvalFlowId = flow.id
            )
        )
    }

    private suspend fun autoApprove(request: EnrollmentRequest): AppResult<EnrollmentRequest> {
        val now = TimeUtils.nowUtc()

        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.APPROVED, request.version
        )

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = null,
            decision = "APPROVED",
            decidedBy = "SYSTEM",
            reason = "Auto-approved: no approval flow configured",
            timestamp = now
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = "SUBMITTED",
            toState = "APPROVED",
            triggeredBy = "SYSTEM",
            reason = "Auto-approved",
            timestamp = now
        ))

        // Create enrollment record
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

        // Update enrolled count
        courseRepository.adjustEnrolledCount(request.classOfferingId, 1)

        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.ENROLLED, request.version + 1
        )

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = request.learnerId,
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_APPROVED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = null,
            afterSummary = "Auto-enrolled",
            reason = null,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(
            request.copy(status = EnrollmentRequestStatus.ENROLLED)
        )
    }
}
