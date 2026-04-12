package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.data.local.TransactionRunner
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class IssueRefundRequest(
    val orderId: String,
    val paymentId: String,
    val amount: BigDecimal,
    val reason: String,
    val overrideNote: String? = null
)

class IssueRefundUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager,
    private val transactionRunner: TransactionRunner
) {
    suspend operator fun invoke(request: IssueRefundRequest): AppResult<RefundRecord> {
        if (!checkPermission.hasPermission(Permission.REFUND_ISSUE)) {
            return AppResult.PermissionError("Requires refund.issue")
        }

        // Validate amount
        if (request.amount < MoneyUtils.MIN_REFUND) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("amount" to "Minimum refund is \$${MoneyUtils.MIN_REFUND}")
            )
        }
        if (request.reason.isBlank()) {
            return AppResult.ValidationError(
                fieldErrors = mapOf("reason" to "Reason is required")
            )
        }

        // Verify order
        val order = orderRepository.getOrderById(request.orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")

        // Verify payment
        val payment = paymentRepository.getPaymentById(request.paymentId)
            ?: return AppResult.NotFoundError("PAYMENT_NOT_FOUND")

        if (payment.orderId != request.orderId) {
            return AppResult.ValidationError(globalErrors = listOf("Payment does not belong to this order"))
        }

        // Check refundable balance
        val existingRefunds = paymentRepository.getRefundsForOrder(request.orderId)
        val totalRefunded = existingRefunds.fold(MoneyUtils.ZERO) { acc, r -> acc.add(r.amount) }
        val refundableBalance = order.paidAmount.subtract(totalRefunded)

        if (request.amount > refundableBalance) {
            return AppResult.ValidationError(
                globalErrors = listOf("Refund amount \$${request.amount} exceeds refundable balance \$$refundableBalance")
            )
        }

        // Daily refund limit check
        val learnerId = order.userId
        val maxRefundsPerDay = policyRepository.getPolicyIntValue(
            PolicyType.RISK, "max_refunds_per_learner_per_day", 3
        )

        val today = LocalDate.now(ZoneId.systemDefault())
        val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val refundsToday = paymentRepository.countRefundsForLearnerToday(learnerId, dayStart, dayEnd)

        var overrideUsed = false
        if (refundsToday >= maxRefundsPerDay) {
            if (!checkPermission.hasPermission(Permission.REFUND_OVERRIDE_LIMIT)) {
                return AppResult.ValidationError(
                    globalErrors = listOf("Daily refund limit ($maxRefundsPerDay) exceeded. Administrator override required.")
                )
            }
            if (request.overrideNote.isNullOrBlank()) {
                return AppResult.ValidationError(
                    fieldErrors = mapOf("overrideNote" to "Override note is required when exceeding daily refund limit")
                )
            }
            overrideUsed = true
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"

        // Determine refund method matching original tender
        val refundMethod = payment.tenderType

        val refund = RefundRecord(
            id = IdGenerator.newId(),
            orderId = request.orderId,
            paymentId = request.paymentId,
            learnerId = learnerId,
            amount = MoneyUtils.round(request.amount),
            reason = request.reason,
            refundMethod = refundMethod,
            externalReference = if (refundMethod != TenderType.CASH) "REF-${IdGenerator.newId().take(8)}" else null,
            processedBy = userId,
            processedAt = now,
            overrideUsed = overrideUsed,
            overrideNote = request.overrideNote,
            createdAt = now
        )

        // All refund writes in a single transaction
        try {
            transactionRunner.runInTransaction {
                paymentRepository.recordRefund(refund)

                // Ledger entry
                paymentRepository.createLedgerEntry(LedgerEntry(
                    id = IdGenerator.newId(),
                    orderId = request.orderId,
                    paymentId = request.paymentId,
                    refundId = refund.id,
                    entryType = LedgerEntryType.REFUND_ISSUED,
                    amount = refund.amount.negate(),
                    description = "Refund of \$${refund.amount} via ${refundMethod}: ${request.reason}",
                    createdAt = now
                ))

                // Update payment status
                val allRefundsForPayment = paymentRepository.getRefundsForOrder(request.orderId)
                    .filter { it.paymentId == request.paymentId }
                val totalRefundedForPayment = allRefundsForPayment.fold(MoneyUtils.ZERO) { acc, r -> acc.add(r.amount) }
                val newPaymentStatus = if (totalRefundedForPayment >= payment.amount) {
                    PaymentStatus.REFUNDED
                } else {
                    PaymentStatus.PARTIALLY_REFUNDED
                }
                paymentRepository.updatePaymentStatus(payment.id, newPaymentStatus, payment.version)

                // Audit
                auditRepository.logEvent(AuditEvent(
                    id = IdGenerator.newId(),
                    actorId = userId,
                    actorUsername = null,
                    actionType = if (overrideUsed) AuditActionType.REFUND_OVERRIDE else AuditActionType.REFUND_ISSUED,
                    targetEntityType = "RefundRecord",
                    targetEntityId = refund.id,
                    beforeSummary = "paidAmount=${order.paidAmount}",
                    afterSummary = "refundAmount=${refund.amount}, method=$refundMethod",
                    reason = request.reason + if (overrideUsed) " [OVERRIDE: ${request.overrideNote}]" else "",
                    sessionId = sessionManager.getCurrentSessionId(),
                    outcome = AuditOutcome.SUCCESS,
                    timestamp = now,
                    metadata = null
                ))
            }
        } catch (e: Exception) {
            return AppResult.SystemError(
                "REFUND_FAILED",
                "Refund issuance failed during atomic write: ${e.message}",
                retryable = true
            )
        }

        return AppResult.Success(refund)
    }
}
