package com.learnmart.app.domain.usecase.commerce

import androidx.room.withTransaction
import com.learnmart.app.data.local.LearnMartRoomDatabase
import com.learnmart.app.domain.model.*
import com.learnmart.app.data.local.dao.BlacklistDao
import com.learnmart.app.domain.repository.*
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import java.security.MessageDigest
import javax.inject.Inject

class CheckoutUseCase @Inject constructor(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val pricingEngine: PricingEngine,
    private val blacklistDao: BlacklistDao,
    private val sessionManager: SessionManager,
    private val database: LearnMartRoomDatabase
) {
    /**
     * Submit an order from the cart. Idempotent via client-generated token.
     * All writes are wrapped in a Room transaction for atomicity.
     */
    suspend fun submitOrder(
        cartId: String,
        idempotencyToken: String
    ): AppResult<Order> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        // --- Idempotency Check ---
        val existingToken = orderRepository.getIdempotencyToken(idempotencyToken)
        if (existingToken != null) {
            if (!TimeUtils.isExpired(existingToken.expiresAt)) {
                val cart = cartRepository.getCartById(cartId)
                val items = cart?.let { cartRepository.getLineItemsForCart(it.id) } ?: emptyList()
                val currentHash = computePayloadHash(cartId, items)

                if (currentHash == existingToken.requestHash) {
                    val existingOrder = orderRepository.getOrderById(existingToken.resultReference)
                    if (existingOrder != null) {
                        return AppResult.Success(existingOrder)
                    }
                } else {
                    return AppResult.ConflictError(
                        "IDEMPOTENCY_PAYLOAD_MISMATCH",
                        "Same token with different payload within validity window"
                    )
                }
            }
        }

        // --- Load Cart ---
        val cart = cartRepository.getCartById(cartId)
            ?: return AppResult.NotFoundError("CART_NOT_FOUND")

        if (cart.userId != userId) {
            return AppResult.PermissionError("Cart does not belong to current user")
        }

        if (cart.status != CartStatus.ACTIVE) {
            return AppResult.ValidationError(globalErrors = listOf("Cart is not active"))
        }

        val lineItems = cartRepository.getLineItemsForCart(cartId)
        if (lineItems.isEmpty()) {
            return AppResult.ValidationError(globalErrors = listOf("Cart is empty"))
        }

        // --- Checkout Policy Validation ---
        val checkoutPolicy = pricingEngine.getCheckoutPolicy()
        if (checkoutPolicy == CheckoutPolicy.SAME_CLASS_ONLY) {
            val classIds = lineItems.mapNotNull { it.classOfferingId }.distinct()
            if (classIds.size > 1) {
                return AppResult.ValidationError(
                    globalErrors = listOf("Checkout policy requires all items from the same class")
                )
            }
        }

        // --- Blacklist check ---
        if (blacklistDao.isBlacklisted(userId) > 0) {
            return AppResult.ValidationError(
                globalErrors = listOf("Checkout denied: account is flagged")
            )
        }

        // --- Reprice at submit time ---
        val pricing = pricingEngine.calculatePricing(lineItems)

        // --- Minimum order total ---
        val minTotal = pricingEngine.getMinimumOrderTotal()
        if (pricing.grandTotal < minTotal) {
            return AppResult.ValidationError(
                globalErrors = listOf("Order total \$${pricing.grandTotal} is below minimum \$$minTotal")
            )
        }

        // --- Inventory Lock Acquisition ---
        val physicalItems = lineItems.filter { it.itemType == LineItemType.PHYSICAL_MATERIAL }
        val acquiredLocks = mutableListOf<InventoryLock>()

        for (item in physicalItems) {
            val inventoryItem = inventoryRepository.getItemByMaterialId(item.referenceId)
            if (inventoryItem == null) {
                releaseLocks(acquiredLocks)
                return AppResult.ValidationError(
                    globalErrors = listOf("Inventory not found for material: ${item.title}")
                )
            }

            if (inventoryItem.availableStock < item.quantity) {
                releaseLocks(acquiredLocks)
                return AppResult.ValidationError(
                    globalErrors = listOf("Insufficient stock for ${item.title}: available=${inventoryItem.availableStock}, requested=${item.quantity}")
                )
            }

            val lockExpiryMinutes = policyRepository.getPolicyLongValue(
                PolicyType.COMMERCE, "inventory_lock_expiry_minutes", 10
            )
            val lock = InventoryLock(
                id = IdGenerator.newId(),
                inventoryItemId = inventoryItem.id,
                orderId = null,
                cartId = cartId,
                quantity = item.quantity,
                status = InventoryLockStatus.ACTIVE,
                acquiredAt = TimeUtils.nowUtc(),
                expiresAt = TimeUtils.minutesFromNow(lockExpiryMinutes),
                releasedAt = null
            )

            inventoryRepository.acquireLock(lock)
            inventoryRepository.adjustReservedStock(inventoryItem.id, item.quantity, inventoryItem.version)
            acquiredLocks.add(lock)
        }

        // --- Atomic transaction: create order, line items, components, update locks, mark cart, save token, audit ---
        val now = TimeUtils.nowUtc()
        val orderId = IdGenerator.newId()

        val order = Order(
            id = orderId,
            userId = userId,
            status = OrderStatus.PLACED_UNPAID,
            idempotencyToken = idempotencyToken,
            subtotal = pricing.subtotal,
            discountTotal = pricing.discountTotal,
            taxAmount = pricing.taxAmount,
            serviceFee = pricing.serviceFee,
            packagingFee = pricing.packagingFee,
            grandTotal = pricing.grandTotal,
            paidAmount = MoneyUtils.ZERO,
            placedAt = now,
            cancelledAt = null,
            cancelReason = null,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        try {
            database.withTransaction {
                orderRepository.createOrder(order)

                // Order Line Items
                val orderLineItems = lineItems.map { cartItem ->
                    OrderLineItem(
                        id = IdGenerator.newId(),
                        orderId = orderId,
                        itemType = cartItem.itemType,
                        referenceId = cartItem.referenceId,
                        classOfferingId = cartItem.classOfferingId,
                        title = cartItem.title,
                        quantity = cartItem.quantity,
                        unitPrice = cartItem.unitPrice,
                        lineTotal = cartItem.lineTotal
                    )
                }
                orderRepository.addOrderLineItems(orderLineItems)

                // Price Components
                val components = listOf(
                    OrderPriceComponent(IdGenerator.newId(), orderId, PriceComponentType.SUBTOTAL, pricing.subtotal, null, "Subtotal"),
                    OrderPriceComponent(IdGenerator.newId(), orderId, PriceComponentType.DISCOUNT, pricing.discountTotal, null, "Discounts"),
                    OrderPriceComponent(IdGenerator.newId(), orderId, PriceComponentType.TAX, pricing.taxAmount, pricing.taxRate, "Sales Tax"),
                    OrderPriceComponent(IdGenerator.newId(), orderId, PriceComponentType.SERVICE_FEE, pricing.serviceFee, pricing.serviceFeeRate, "Service Fee"),
                    OrderPriceComponent(IdGenerator.newId(), orderId, PriceComponentType.PACKAGING_FEE, pricing.packagingFee, null, "Packaging Fee"),
                    OrderPriceComponent(IdGenerator.newId(), orderId, PriceComponentType.GRAND_TOTAL, pricing.grandTotal, null, "Grand Total")
                )
                orderRepository.addOrderPriceComponents(components)

                // Update locks with orderId
                acquiredLocks.forEach { lock ->
                    inventoryRepository.releaseLock(lock.id, "ORDER_CREATED")
                    inventoryRepository.acquireLock(lock.copy(orderId = orderId, status = InventoryLockStatus.ACTIVE))
                }

                // Mark cart as checked out
                cartRepository.updateCart(cart.copy(status = CartStatus.CHECKED_OUT, updatedAt = now))

                // Save idempotency token
                val idempotencyWindowMinutes = policyRepository.getPolicyLongValue(
                    PolicyType.COMMERCE, "idempotency_window_minutes", 5
                )
                orderRepository.saveIdempotencyToken(IdempotencyToken(
                    token = idempotencyToken,
                    requestHash = computePayloadHash(cartId, lineItems),
                    resultReference = orderId,
                    createdAt = now,
                    expiresAt = TimeUtils.minutesFromNow(idempotencyWindowMinutes)
                ))

                // Audit
                auditRepository.logEvent(AuditEvent(
                    id = IdGenerator.newId(),
                    actorId = userId,
                    actorUsername = null,
                    actionType = AuditActionType.ORDER_PLACED,
                    targetEntityType = "Order",
                    targetEntityId = orderId,
                    beforeSummary = null,
                    afterSummary = "total=${pricing.grandTotal}, items=${lineItems.size}",
                    reason = null,
                    sessionId = sessionManager.getCurrentSessionId(),
                    outcome = AuditOutcome.SUCCESS,
                    timestamp = now,
                    metadata = null
                ))

                auditRepository.logStateTransition(StateTransitionLog(
                    id = IdGenerator.newId(),
                    entityType = "Order",
                    entityId = orderId,
                    fromState = "CART",
                    toState = "PLACED_UNPAID",
                    triggeredBy = userId,
                    reason = null,
                    timestamp = now
                ))
            }
        } catch (e: Exception) {
            // Transaction failed - release inventory locks
            releaseLocks(acquiredLocks)
            return AppResult.SystemError(
                "CHECKOUT_FAILED",
                "Checkout failed during atomic write: ${e.message}",
                retryable = true
            )
        }

        return AppResult.Success(order)
    }

    private suspend fun releaseLocks(locks: List<InventoryLock>) {
        locks.forEach { lock ->
            try {
                inventoryRepository.releaseLock(lock.id, "CHECKOUT_FAILED")
                val item = inventoryRepository.getItemById(lock.inventoryItemId)
                if (item != null) {
                    inventoryRepository.adjustReservedStock(item.id, -lock.quantity, item.version)
                }
            } catch (_: Exception) {
                // Best effort release
            }
        }
    }

    private fun computePayloadHash(cartId: String, items: List<CartLineItem>): String {
        val payload = "$cartId|${items.sortedBy { it.id }.joinToString(",") { "${it.referenceId}:${it.quantity}:${it.unitPrice}" }}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
