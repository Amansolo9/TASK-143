package com.learnmart.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that handles:
 * 1. Auto-expiring pending enrollment requests after 48 hours
 * 2. Auto-expiring waitlist offers after 24 hours
 * 3. Auto-expiring pending approval tasks
 *
 * This worker is idempotent and safe to re-run.
 */
@HiltWorker
class EnrollmentExpiryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val enrollmentRepository: EnrollmentRepository,
    private val auditRepository: AuditRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = TimeUtils.nowUtc()
            val currentTimeMs = now.toEpochMilli()

            // 1. Expire pending enrollment requests
            val expiredRequests = enrollmentRepository.getExpiredRequests(currentTimeMs)
            expiredRequests.forEach { request ->
                expireRequest(request)
            }

            // 2. Expire waitlist offers
            val expiredOffers = enrollmentRepository.getExpiredOffers(currentTimeMs)
            expiredOffers.forEach { entry ->
                expireWaitlistOffer(entry)
            }

            // 3. Expire pending approval tasks
            val expiredTasks = enrollmentRepository.getExpiredTasks(currentTimeMs)
            expiredTasks.forEach { task ->
                expireApprovalTask(task)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 4) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun expireRequest(request: EnrollmentRequest) {
        if (request.status.isTerminal()) return

        val now = TimeUtils.nowUtc()

        enrollmentRepository.updateRequestStatus(
            request.id, EnrollmentRequestStatus.EXPIRED, request.version
        )

        // Cancel related pending tasks
        val pendingTasks = enrollmentRepository.getPendingTasksForRequest(request.id)
        pendingTasks.forEach { task ->
            enrollmentRepository.updateApprovalTask(task.copy(
                status = ApprovalTaskStatus.EXPIRED,
                decidedBy = "SYSTEM",
                decidedAt = now,
                notes = "Request expired"
            ))
        }

        // Cancel waitlist entry if any
        val waitlistEntry = enrollmentRepository.getActiveWaitlistForLearnerAndClass(
            request.learnerId, request.classOfferingId
        )
        if (waitlistEntry != null) {
            enrollmentRepository.updateWaitlistEntry(waitlistEntry.copy(
                status = WaitlistStatus.EXPIRED
            ))
        }

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = request.id,
            approvalTaskId = null,
            decision = "EXPIRED",
            decidedBy = "SYSTEM",
            reason = "Auto-expired: request exceeded time limit",
            timestamp = now
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = request.id,
            fromState = request.status.name,
            toState = "EXPIRED",
            triggeredBy = "SYSTEM",
            reason = "Auto-expiration timeout",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = null,
            actorUsername = "SYSTEM",
            actionType = AuditActionType.ENROLLMENT_EXPIRED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = request.id,
            beforeSummary = "status=${request.status}",
            afterSummary = "status=EXPIRED",
            reason = "Auto-expired after timeout",
            sessionId = null,
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))
    }

    private suspend fun expireWaitlistOffer(entry: WaitlistEntry) {
        val now = TimeUtils.nowUtc()

        enrollmentRepository.updateWaitlistEntry(entry.copy(
            status = WaitlistStatus.EXPIRED
        ))

        // Update the request
        val request = enrollmentRepository.getRequestById(entry.enrollmentRequestId)
        if (request != null && !request.status.isTerminal()) {
            enrollmentRepository.updateRequestStatus(
                request.id, EnrollmentRequestStatus.EXPIRED, request.version
            )
        }

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = entry.enrollmentRequestId,
            approvalTaskId = null,
            decision = "EXPIRED",
            decidedBy = "SYSTEM",
            reason = "Waitlist offer expired after timeout",
            timestamp = now
        ))

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "WaitlistEntry",
            entityId = entry.id,
            fromState = "OFFERED",
            toState = "EXPIRED",
            triggeredBy = "SYSTEM",
            reason = "Offer timeout",
            timestamp = now
        ))

        // Try to promote next person in waitlist
        // Import not needed here - the next scheduled run will pick it up
        // or the promotion happens through the WaitlistPromotionUseCase
    }

    private suspend fun expireApprovalTask(task: EnrollmentApprovalTask) {
        val now = TimeUtils.nowUtc()

        enrollmentRepository.updateApprovalTask(task.copy(
            status = ApprovalTaskStatus.EXPIRED,
            decidedBy = "SYSTEM",
            decidedAt = now,
            notes = "Auto-expired: approval task exceeded time limit"
        ))

        enrollmentRepository.createDecisionEvent(EnrollmentDecisionEvent(
            id = IdGenerator.newId(),
            enrollmentRequestId = task.enrollmentRequestId,
            approvalTaskId = task.id,
            decision = "EXPIRED",
            decidedBy = "SYSTEM",
            reason = "Approval task expired",
            timestamp = now
        ))

        // Check if this causes the whole request to expire
        val request = enrollmentRepository.getRequestById(task.enrollmentRequestId)
        if (request != null && request.status == EnrollmentRequestStatus.PENDING_APPROVAL) {
            val remainingPending = enrollmentRepository.getPendingTasksForRequest(request.id)
            if (remainingPending.isEmpty()) {
                // No more pending tasks - expire the request
                expireRequest(request)
            }
        }
    }
}
