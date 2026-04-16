package com.learnmart.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Integration tests for state machine models — no mocking, exercises
 * the real transition logic of all lifecycle enums.
 */
class StateTransitionIntegrationTest {

    // --- Order State Machine ---

    @Test
    fun `order lifecycle happy path CART to CLOSED`() {
        assertThat(OrderStatus.PLACED_UNPAID.canTransitionTo(OrderStatus.PAID)).isTrue()
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FULFILLMENT_IN_PROGRESS)).isTrue()
        assertThat(OrderStatus.FULFILLMENT_IN_PROGRESS.canTransitionTo(OrderStatus.DELIVERED)).isTrue()
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CLOSED)).isTrue()
    }

    @Test
    fun `cancelled orders are terminal`() {
        assertThat(OrderStatus.AUTO_CANCELLED.isTerminal()).isTrue()
        assertThat(OrderStatus.MANUAL_CANCELLED.isTerminal()).isTrue()
    }

    @Test
    fun `paid order cannot go back to unpaid`() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.PLACED_UNPAID)).isFalse()
    }

    // --- Payment State Machine ---

    @Test
    fun `payment lifecycle RECORDED to CLEARED`() {
        assertThat(PaymentStatus.RECORDED.canTransitionTo(PaymentStatus.ALLOCATED)).isTrue()
        assertThat(PaymentStatus.ALLOCATED.canTransitionTo(PaymentStatus.CLEARED)).isTrue()
    }

    @Test
    fun `refunded payment is terminal`() {
        assertThat(PaymentStatus.REFUNDED.allowedTransitions()).isEmpty()
    }

    @Test
    fun `voided payment is terminal`() {
        assertThat(PaymentStatus.VOIDED.allowedTransitions()).isEmpty()
    }

    @Test
    fun `allocated can transition to discrepancy`() {
        assertThat(PaymentStatus.ALLOCATED.canTransitionTo(PaymentStatus.DISCREPANCY_FLAGGED)).isTrue()
    }

    // --- Submission State Machine ---

    @Test
    fun `submission lifecycle SUBMITTED to FINALIZED`() {
        assertThat(SubmissionStatus.SUBMITTED.canTransitionTo(SubmissionStatus.AUTO_GRADED)).isTrue()
        assertThat(SubmissionStatus.AUTO_GRADED.canTransitionTo(SubmissionStatus.FINALIZED)).isTrue()
    }

    @Test
    fun `submission with manual review path`() {
        assertThat(SubmissionStatus.SUBMITTED.canTransitionTo(SubmissionStatus.QUEUED_FOR_MANUAL_REVIEW)).isTrue()
        assertThat(SubmissionStatus.QUEUED_FOR_MANUAL_REVIEW.canTransitionTo(SubmissionStatus.GRADED)).isTrue()
        assertThat(SubmissionStatus.GRADED.canTransitionTo(SubmissionStatus.FINALIZED)).isTrue()
    }

    @Test
    fun `finalized can be reopened by instructor`() {
        assertThat(SubmissionStatus.FINALIZED.canTransitionTo(SubmissionStatus.REOPENED_BY_INSTRUCTOR)).isTrue()
    }

    @Test
    fun `missed and abandoned are terminal`() {
        assertThat(SubmissionStatus.MISSED.isTerminal()).isTrue()
        assertThat(SubmissionStatus.ABANDONED.isTerminal()).isTrue()
    }

    // --- Enrollment State Machine ---

    @Test
    fun `enrollment lifecycle SUBMITTED to ENROLLED`() {
        assertThat(EnrollmentRequestStatus.SUBMITTED.canTransitionTo(EnrollmentRequestStatus.PENDING_APPROVAL)).isTrue()
        assertThat(EnrollmentRequestStatus.PENDING_APPROVAL.canTransitionTo(EnrollmentRequestStatus.APPROVED)).isTrue()
        assertThat(EnrollmentRequestStatus.APPROVED.canTransitionTo(EnrollmentRequestStatus.ENROLLED)).isTrue()
    }

    @Test
    fun `capacity exception path exists`() {
        assertThat(EnrollmentRequestStatus.SUBMITTED.canTransitionTo(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION)).isTrue()
        assertThat(EnrollmentRequestStatus.PENDING_CAPACITY_EXCEPTION.canTransitionTo(EnrollmentRequestStatus.APPROVED)).isTrue()
    }

    @Test
    fun `rejected and expired are terminal`() {
        assertThat(EnrollmentRequestStatus.REJECTED.isTerminal()).isTrue()
        assertThat(EnrollmentRequestStatus.EXPIRED.isTerminal()).isTrue()
    }

    // --- Import Job State Machine ---

    @Test
    fun `import lifecycle CREATED to APPLIED`() {
        assertThat(ImportJobStatus.CREATED.canTransitionTo(ImportJobStatus.VALIDATING)).isTrue()
        assertThat(ImportJobStatus.VALIDATING.canTransitionTo(ImportJobStatus.READY_TO_APPLY)).isTrue()
    }

    @Test
    fun `rejected import is terminal`() {
        assertThat(ImportJobStatus.REJECTED.allowedTransitions()).isEmpty()
    }

    // --- Backup State Machine ---

    @Test
    fun `backup lifecycle REQUESTED to VERIFIED`() {
        assertThat(BackupStatus.REQUESTED.canTransitionTo(BackupStatus.RUNNING)).isTrue()
        assertThat(BackupStatus.RUNNING.canTransitionTo(BackupStatus.SUCCEEDED)).isTrue()
        assertThat(BackupStatus.SUCCEEDED.canTransitionTo(BackupStatus.VERIFIED)).isTrue()
    }

    @Test
    fun `verified backup is terminal`() {
        assertThat(BackupStatus.VERIFIED.allowedTransitions()).isEmpty()
    }
}
