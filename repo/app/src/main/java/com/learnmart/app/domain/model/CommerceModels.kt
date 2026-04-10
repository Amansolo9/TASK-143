package com.learnmart.app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

// --- Order Lifecycle State Machine ---
enum class OrderStatus {
    CART,
    QUOTED,
    PENDING_SUBMISSION,
    PLACED_UNPAID,
    PARTIALLY_PAID,
    PAID,
    FULFILLMENT_IN_PROGRESS,
    AWAITING_PICKUP,
    DELIVERED,
    CLOSED,
    AUTO_CANCELLED,
    MANUAL_CANCELLED,
    REFUND_IN_PROGRESS,
    RETURN_REQUESTED,
    RETURNED,
    EXCHANGE_IN_PROGRESS,
    EXCHANGED;

    fun allowedTransitions(): Set<OrderStatus> = when (this) {
        CART -> setOf(QUOTED, PENDING_SUBMISSION)
        QUOTED -> setOf(PENDING_SUBMISSION, CART)
        PENDING_SUBMISSION -> setOf(PLACED_UNPAID)
        PLACED_UNPAID -> setOf(PARTIALLY_PAID, PAID, AUTO_CANCELLED, MANUAL_CANCELLED)
        PARTIALLY_PAID -> setOf(PAID, AUTO_CANCELLED, MANUAL_CANCELLED)
        PAID -> setOf(FULFILLMENT_IN_PROGRESS, REFUND_IN_PROGRESS)
        FULFILLMENT_IN_PROGRESS -> setOf(AWAITING_PICKUP, DELIVERED)
        AWAITING_PICKUP -> setOf(DELIVERED, CLOSED)
        DELIVERED -> setOf(CLOSED, RETURN_REQUESTED, EXCHANGE_IN_PROGRESS)
        CLOSED -> setOf(RETURN_REQUESTED, EXCHANGE_IN_PROGRESS)
        RETURN_REQUESTED -> setOf(RETURNED)
        EXCHANGE_IN_PROGRESS -> setOf(EXCHANGED)
        AUTO_CANCELLED -> emptySet()
        MANUAL_CANCELLED -> emptySet()
        REFUND_IN_PROGRESS -> setOf(PAID, CLOSED)
        RETURNED -> emptySet()
        EXCHANGED -> emptySet()
    }

    fun canTransitionTo(target: OrderStatus): Boolean = target in allowedTransitions()
    fun isTerminal(): Boolean = this in setOf(AUTO_CANCELLED, MANUAL_CANCELLED, RETURNED, EXCHANGED)
}

// --- Payment Lifecycle ---
enum class PaymentStatus {
    RECORDED,
    ALLOCATED,
    CLEARED,
    DISCREPANCY_FLAGGED,
    RESOLVED,
    VOIDED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    fun allowedTransitions(): Set<PaymentStatus> = when (this) {
        RECORDED -> setOf(ALLOCATED, VOIDED, DISCREPANCY_FLAGGED)
        ALLOCATED -> setOf(CLEARED, PARTIALLY_REFUNDED, REFUNDED, DISCREPANCY_FLAGGED)
        CLEARED -> setOf(PARTIALLY_REFUNDED, REFUNDED, DISCREPANCY_FLAGGED)
        DISCREPANCY_FLAGGED -> setOf(RESOLVED, CLEARED, PARTIALLY_REFUNDED, REFUNDED)
        RESOLVED -> setOf(CLEARED, PARTIALLY_REFUNDED, REFUNDED)
        VOIDED -> emptySet()
        PARTIALLY_REFUNDED -> setOf(REFUNDED, DISCREPANCY_FLAGGED)
        REFUNDED -> emptySet()
    }

    fun canTransitionTo(target: PaymentStatus): Boolean = target in allowedTransitions()
}

enum class TenderType {
    CASH,
    CHECK,
    EXTERNAL_CARD_TERMINAL_REFERENCE
}

enum class CheckoutPolicy {
    SAME_CLASS_ONLY,
    CROSS_CLASS_ALLOWED
}

// --- Cart ---
data class Cart(
    val id: String,
    val userId: String,
    val status: CartStatus,
    val checkoutPolicy: CheckoutPolicy,
    val createdAt: Instant,
    val updatedAt: Instant
)

enum class CartStatus { ACTIVE, CHECKED_OUT, ABANDONED }

data class CartLineItem(
    val id: String,
    val cartId: String,
    val itemType: LineItemType,
    val referenceId: String, // courseId or materialId
    val classOfferingId: String?,
    val title: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal,
    val createdAt: Instant
)

enum class LineItemType { COURSE_FEE, PHYSICAL_MATERIAL }

// --- Quote ---
data class QuoteSnapshot(
    val id: String,
    val cartId: String,
    val subtotal: BigDecimal,
    val discountTotal: BigDecimal,
    val taxAmount: BigDecimal,
    val serviceFee: BigDecimal,
    val packagingFee: BigDecimal,
    val grandTotal: BigDecimal,
    val taxRate: BigDecimal,
    val serviceFeeRate: BigDecimal,
    val quotedAt: Instant,
    val validUntil: Instant
)

// --- Order ---
data class Order(
    val id: String,
    val userId: String,
    val status: OrderStatus,
    val idempotencyToken: String?,
    val subtotal: BigDecimal,
    val discountTotal: BigDecimal,
    val taxAmount: BigDecimal,
    val serviceFee: BigDecimal,
    val packagingFee: BigDecimal,
    val grandTotal: BigDecimal,
    val paidAmount: BigDecimal,
    val placedAt: Instant?,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class OrderLineItem(
    val id: String,
    val orderId: String,
    val itemType: LineItemType,
    val referenceId: String,
    val classOfferingId: String?,
    val title: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

enum class PriceComponentType {
    SUBTOTAL, DISCOUNT, TAX, SERVICE_FEE, PACKAGING_FEE, GRAND_TOTAL
}

data class OrderPriceComponent(
    val id: String,
    val orderId: String,
    val componentType: PriceComponentType,
    val amount: BigDecimal,
    val rate: BigDecimal?,
    val description: String
)

// --- Inventory ---
data class InventoryItem(
    val id: String,
    val materialId: String,
    val sku: String,
    val totalStock: Int,
    val reservedStock: Int,
    val availableStock: Int,
    val updatedAt: Instant,
    val version: Int
)

enum class InventoryLockStatus { ACTIVE, RELEASED, EXPIRED, CONSUMED }

data class InventoryLock(
    val id: String,
    val inventoryItemId: String,
    val orderId: String?,
    val cartId: String?,
    val quantity: Int,
    val status: InventoryLockStatus,
    val acquiredAt: Instant,
    val expiresAt: Instant,
    val releasedAt: Instant?
)

// --- Fulfillment ---
data class FulfillmentRecord(
    val id: String,
    val orderId: String,
    val fulfilledBy: String,
    val fulfilledAt: Instant,
    val notes: String?
)

data class DeliveryConfirmation(
    val id: String,
    val orderId: String,
    val deliveryType: DeliveryType,
    val confirmedBy: String,
    val confirmedAt: Instant,
    val notes: String?
)

enum class DeliveryType { PICKUP, DELIVERY }

data class ReturnExchangeRecord(
    val id: String,
    val originalOrderId: String,
    val replacementOrderId: String?,
    val recordType: ReturnExchangeType,
    val reason: String,
    val processedBy: String,
    val processedAt: Instant,
    val notes: String?
)

enum class ReturnExchangeType { RETURN, EXCHANGE }

// --- Payment ---
data class PaymentRecord(
    val id: String,
    val orderId: String,
    val amount: BigDecimal,
    val tenderType: TenderType,
    val status: PaymentStatus,
    val externalReference: String?,
    val receivedBy: String,
    val receivedAt: Instant,
    val notes: String?,
    val createdAt: Instant,
    val version: Int
)

data class PaymentAllocation(
    val id: String,
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    val allocatedAt: Instant
)

data class RefundRecord(
    val id: String,
    val orderId: String,
    val paymentId: String,
    val learnerId: String,
    val amount: BigDecimal,
    val reason: String,
    val refundMethod: TenderType,
    val externalReference: String?,
    val processedBy: String,
    val processedAt: Instant,
    val overrideUsed: Boolean,
    val overrideNote: String?,
    val createdAt: Instant
)

enum class LedgerEntryType {
    PAYMENT_RECEIVED,
    PAYMENT_ALLOCATED,
    PAYMENT_VOIDED,
    REFUND_ISSUED,
    ADJUSTMENT
}

data class LedgerEntry(
    val id: String,
    val orderId: String?,
    val paymentId: String?,
    val refundId: String?,
    val entryType: LedgerEntryType,
    val amount: BigDecimal, // positive for credit, negative for debit
    val description: String,
    val createdAt: Instant
)

// --- Idempotency ---
data class IdempotencyToken(
    val token: String,
    val requestHash: String,
    val resultReference: String,
    val createdAt: Instant,
    val expiresAt: Instant
)

// --- Money Utilities ---
object MoneyUtils {
    val SCALE = 2
    val ROUNDING = RoundingMode.HALF_UP

    fun round(value: BigDecimal): BigDecimal = value.setScale(SCALE, ROUNDING)

    fun calculateTax(subtotal: BigDecimal, taxRate: BigDecimal): BigDecimal =
        round(subtotal.multiply(taxRate))

    fun calculateServiceFee(subtotal: BigDecimal, rate: BigDecimal): BigDecimal =
        round(subtotal.multiply(rate))

    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(SCALE)
    val MIN_ORDER_TOTAL: BigDecimal = BigDecimal("25.00")
    val DEFAULT_PACKAGING_FEE: BigDecimal = BigDecimal("1.50")
    val MIN_REFUND: BigDecimal = BigDecimal("0.01")
}
