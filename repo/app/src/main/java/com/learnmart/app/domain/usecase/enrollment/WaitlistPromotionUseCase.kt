package com.learnmart.app.domain.usecase.enrollment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class WaitlistPromotionUseCase @Inject constructor(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository
) {
    /**
     * Called when a seat becomes available (withdrawal, cancellation, capacity increase).
     * Promotes the next eligible waitlist entry per PRD rules:
     *   1. Highest priority tier (lowest numeric value)
     *   2. Earliest waitlist timestamp
     *   3. Lowest row ID as tiebreaker
     */
    suspend fun promoteNextIfAvailable(classOfferingId: String): AppResult<WaitlistEntry?> {
        val classOffering = courseRepository.getClassOfferingById(classOfferingId)
            ?: return AppResult.NotFoundError("CLASS_NOT_FOUND")

        if (!classOffering.waitlistEnabled) {
            return AppResult.Success(null)
        }

        val enrolledCount = enrollmentRepository.countActiveEnrollmentsForClass(classOfferingId)
        if (enrolledCount >= classOffering.hardCapacity) {
            return AppResult.Success(null) // Still at capacity
        }

        // Get next entry (ordered by priority_tier ASC, added_at ASC, id ASC)
        val nextEntry = enrollmentRepository.getNextWaitlistEntry(classOfferingId)
            ?: return AppResult.Success(null)

        val now = TimeUtils.nowUtc()
        val offerExpiryHours = policyRepository.getPolicyLongValue(
            PolicyType.ENROLLMENT, "waitlist_offer_expiry_hours", 24
        )
        val offerExpiresAt = TimeUtils.hoursFromNow(offerExpiryHours)

        // Promote: offer the spot
        val updatedEntry = nextEntry.copy(
            status = WaitlistStatus.OFFERED,
            offeredAt = now,
            offerExpiresAt = offerExpiresAt
        )
        enrollmentRepository.updateWaitlistEntry(updatedEntry)

        // Update enrollment request status
        enrollmentRepository.updateRequestStatus(
            nextEntry.enrollmentRequestId, EnrollmentRequestStatus.OFFERED, 0 // version check skipped for system actions
        )

        // Update expiry on the request
        val request = enrollmentRepository.getRequestById(nextEntry.enrollmentRequestId)
        if (request != null) {
            enrollmentRepository.updateRequest(request.copy(
                status = EnrollmentRequestStatus.OFFERED,
                expiresAt = offerExpiresAt,
                updatedAt = now
            ))
        }

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = nextEntry.enrollmentRequestId,
            fromState = "WAITLISTED",
            toState = "OFFERED",
            triggeredBy = "SYSTEM",
            reason = "Seat became available, promoted from waitlist position ${nextEntry.position}",
            timestamp = now
        ))

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = null,
            actorUsername = null,
            actionType = AuditActionType.ENROLLMENT_OFFERED,
            targetEntityType = "EnrollmentRequest",
            targetEntityId = nextEntry.enrollmentRequestId,
            beforeSummary = "waitlistPosition=${nextEntry.position}",
            afterSummary = "status=OFFERED, expiresAt=$offerExpiresAt",
            reason = "Waitlist promotion",
            sessionId = null,
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(updatedEntry)
    }

    /**
     * Called when a learner accepts a waitlist offer.
     */
    suspend fun acceptOffer(waitlistEntryId: String): AppResult<EnrollmentRecord> {
        val entry = enrollmentRepository.getWaitlistEntryById(waitlistEntryId)
            ?: return AppResult.NotFoundError("WAITLIST_ENTRY_NOT_FOUND")

        if (entry.status != WaitlistStatus.OFFERED) {
            return AppResult.ValidationError(
                globalErrors = listOf("Waitlist entry is not in OFFERED status")
            )
        }

        // Check if offer expired
        if (entry.offerExpiresAt != null && TimeUtils.isExpired(entry.offerExpiresAt)) {
            return AppResult.ValidationError(
                globalErrors = listOf("This offer has expired")
            )
        }

        val now = TimeUtils.nowUtc()

        // Accept the offer
        enrollmentRepository.updateWaitlistEntry(entry.copy(
            status = WaitlistStatus.ACCEPTED
        ))

        // Create enrollment
        val record = EnrollmentRecord(
            id = IdGenerator.newId(),
            learnerId = entry.learnerId,
            classOfferingId = entry.classOfferingId,
            enrollmentRequestId = entry.enrollmentRequestId,
            enrolledAt = now,
            completedAt = null,
            withdrawnAt = null,
            status = EnrollmentRecordStatus.ACTIVE
        )
        enrollmentRepository.createEnrollmentRecord(record)
        courseRepository.adjustEnrolledCount(entry.classOfferingId, 1)

        // Update request to enrolled
        val request = enrollmentRepository.getRequestById(entry.enrollmentRequestId)
        if (request != null) {
            enrollmentRepository.updateRequest(request.copy(
                status = EnrollmentRequestStatus.ENROLLED,
                updatedAt = now
            ))
        }

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = entry.enrollmentRequestId,
            fromState = "OFFERED",
            toState = "ENROLLED",
            triggeredBy = entry.learnerId,
            reason = "Accepted waitlist offer",
            timestamp = now
        ))

        return AppResult.Success(record)
    }

    /**
     * Called when a learner declines a waitlist offer.
     */
    suspend fun declineOffer(waitlistEntryId: String): AppResult<Unit> {
        val entry = enrollmentRepository.getWaitlistEntryById(waitlistEntryId)
            ?: return AppResult.NotFoundError("WAITLIST_ENTRY_NOT_FOUND")

        if (entry.status != WaitlistStatus.OFFERED) {
            return AppResult.ValidationError(
                globalErrors = listOf("Waitlist entry is not in OFFERED status")
            )
        }

        val now = TimeUtils.nowUtc()

        enrollmentRepository.updateWaitlistEntry(entry.copy(
            status = WaitlistStatus.CANCELLED
        ))

        enrollmentRepository.updateRequestStatus(
            entry.enrollmentRequestId, EnrollmentRequestStatus.DECLINED, 0
        )

        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "EnrollmentRequest",
            entityId = entry.enrollmentRequestId,
            fromState = "OFFERED",
            toState = "DECLINED",
            triggeredBy = entry.learnerId,
            reason = "Declined waitlist offer",
            timestamp = now
        ))

        // Try to promote next in line
        promoteNextIfAvailable(entry.classOfferingId)

        return AppResult.Success(Unit)
    }
}
