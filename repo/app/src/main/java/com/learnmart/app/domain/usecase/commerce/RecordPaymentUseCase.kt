package com.learnmart.app.domain.usecase.commerce

import androidx.room.withTransaction
import com.learnmart.app.data.local.LearnMartRoomDatabase
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import javax.inject.Inject

data class RecordPaymentRequest(
    val orderId: String,
    val amount: BigDecimal,
    val tenderType: TenderType,
    val externalReference: String?,
    val notes: String?
)

class RecordPaymentUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager,
    private val database: LearnMartRoomDatabase
) {
    suspend operator fun invoke(request: RecordPaymentRequest): AppResult<PaymentRecord> {
        if (!checkPermission.hasPermission(Permission.PAYMENT_RECORD)) {
            return AppResult.PermissionError("Requires payment.record")
        }

        // Validate
        val errors = mutableMapOf<String, String>()
        if (request.amount <= BigDecimal.ZERO) {
            errors["amount"] = "Amount must be positive"
        }
        if (request.tenderType == TenderType.EXTERNAL_CARD_TERMINAL_REFERENCE && request.externalReference.isNullOrBlank()) {
            errors["externalReference"] = "External reference required for card terminal payments"
        }
        if (request.tenderType == TenderType.CHECK && request.externalReference.isNullOrBlank()) {
            errors["externalReference"] = "Check number/reference required"
        }
        if (errors.isNotEmpty()) return AppResult.ValidationError(fieldErrors = errors)

        // Verify order
        val order = orderRepository.getOrderById(request.orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")

        if (order.status in listOf(OrderStatus.AUTO_CANCELLED, OrderStatus.MANUAL_CANCELLED)) {
            return AppResult.ValidationError(globalErrors = listOf("Cannot record payment for cancelled order"))
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"

        // Create payment record
        val checkStatus = if (request.tenderType == TenderType.CHECK) "RECORDED_UNCLEARED" else null
        val payment = PaymentRecord(
            id = IdGenerator.newId(),
            orderId = request.orderId,
            amount = MoneyUtils.round(request.amount),
            tenderType = request.tenderType,
            status = PaymentStatus.RECORDED,
            externalReference = request.externalReference,
            receivedBy = userId,
            receivedAt = now,
            notes = request.notes?.let {
                if (checkStatus != null) "$it [$checkStatus]" else it
            } ?: checkStatus,
            createdAt = now,
            version = 1
        )

        // All financial writes in a single transaction
        try {
            database.withTransaction {
                paymentRepository.recordPayment(payment)

                // Create allocation
                val allocation = PaymentAllocation(
                    id = IdGenerator.newId(),
                    paymentId = payment.id,
                    orderId = request.orderId,
                    amount = payment.amount,
                    allocatedAt = now
                )
                paymentRepository.createAllocation(allocation)

                // Update payment to ALLOCATED
                paymentRepository.updatePaymentStatus(payment.id, PaymentStatus.ALLOCATED, payment.version)

                // Ledger entries
                paymentRepository.createLedgerEntry(LedgerEntry(
                    id = IdGenerator.newId(),
                    orderId = request.orderId,
                    paymentId = payment.id,
                    refundId = null,
                    entryType = LedgerEntryType.PAYMENT_RECEIVED,
                    amount = payment.amount,
                    description = "${request.tenderType} payment of \$${payment.amount}",
                    createdAt = now
                ))

                paymentRepository.createLedgerEntry(LedgerEntry(
                    id = IdGenerator.newId(),
                    orderId = request.orderId,
                    paymentId = payment.id,
                    refundId = null,
                    entryType = LedgerEntryType.PAYMENT_ALLOCATED,
                    amount = payment.amount,
                    description = "Allocated to order ${request.orderId}",
                    createdAt = now
                ))

                // Check if order is now fully paid
                val totalAllocated = paymentRepository.getTotalAllocatedForOrder(request.orderId)
                val newPaidAmount = totalAllocated
                val newStatus = when {
                    newPaidAmount >= order.grandTotal -> OrderStatus.PAID
                    newPaidAmount > MoneyUtils.ZERO -> OrderStatus.PARTIALLY_PAID
                    else -> order.status
                }

                if (newStatus != order.status) {
                    orderRepository.updateOrderStatus(order.id, newStatus, order.version)

                    auditRepository.logStateTransition(StateTransitionLog(
                        id = IdGenerator.newId(),
                        entityType = "Order",
                        entityId = order.id,
                        fromState = order.status.name,
                        toState = newStatus.name,
                        triggeredBy = userId,
                        reason = "Payment recorded: \$${payment.amount}",
                        timestamp = now
                    ))
                }

                auditRepository.logEvent(AuditEvent(
                    id = IdGenerator.newId(),
                    actorId = userId,
                    actorUsername = null,
                    actionType = AuditActionType.PAYMENT_RECORDED,
                    targetEntityType = "PaymentRecord",
                    targetEntityId = payment.id,
                    beforeSummary = null,
                    afterSummary = "amount=${payment.amount}, tender=${request.tenderType}, order=${request.orderId}",
                    reason = null,
                    sessionId = sessionManager.getCurrentSessionId(),
                    outcome = AuditOutcome.SUCCESS,
                    timestamp = now,
                    metadata = null
                ))
            }
        } catch (e: Exception) {
            return AppResult.SystemError(
                "PAYMENT_RECORDING_FAILED",
                "Payment recording failed during atomic write: ${e.message}",
                retryable = true
            )
        }

        return AppResult.Success(payment)
    }
}
