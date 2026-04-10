package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.*
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

class ManageOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    /**
     * Object-level authorization: owner can read own orders;
     * ADMIN, FINANCE_CLERK, REGISTRAR can read any order.
     */
    private suspend fun authorizeOrderRead(order: Order): AppResult<Unit> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")
        if (order.userId == userId) return AppResult.Success(Unit)
        if (checkPermission.hasAnyPermission(
                Permission.PAYMENT_RECORD, Permission.REFUND_ISSUE,
                Permission.ORDER_FULFILL, Permission.AUDIT_VIEW
            )) return AppResult.Success(Unit)
        return AppResult.PermissionError("You do not have access to this order")
    }

    /**
     * Object-level authorization: only ADMIN/FINANCE can mutate any order;
     * owner can cancel their own unpaid order.
     */
    private suspend fun authorizeOrderMutate(order: Order, allowOwnerCancel: Boolean = false): AppResult<Unit> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")
        if (checkPermission.hasAnyPermission(
                Permission.ORDER_FULFILL, Permission.PAYMENT_RECORD, Permission.REFUND_ISSUE
            )) return AppResult.Success(Unit)
        if (allowOwnerCancel && order.userId == userId) return AppResult.Success(Unit)
        return AppResult.PermissionError("You do not have permission to modify this order")
    }

    suspend fun getOrderById(orderId: String): AppResult<Order> {
        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")
        val authResult = authorizeOrderRead(order)
        if (authResult is AppResult.PermissionError) return authResult
        return AppResult.Success(order)
    }

    suspend fun getMyOrders(limit: Int, offset: Int): List<Order> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return orderRepository.getOrdersForUser(userId, limit, offset)
    }

    suspend fun getOrderLineItems(orderId: String): AppResult<List<OrderLineItem>> {
        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")
        val authResult = authorizeOrderRead(order)
        if (authResult is AppResult.PermissionError) return authResult
        return AppResult.Success(orderRepository.getOrderLineItems(orderId))
    }

    suspend fun getOrderPriceComponents(orderId: String): AppResult<List<OrderPriceComponent>> {
        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")
        val authResult = authorizeOrderRead(order)
        if (authResult is AppResult.PermissionError) return authResult
        return AppResult.Success(orderRepository.getOrderPriceComponents(orderId))
    }

    suspend fun startFulfillment(orderId: String): AppResult<Order> {
        if (!checkPermission.hasPermission(Permission.ORDER_FULFILL)) {
            return AppResult.PermissionError("Requires order.fulfill")
        }

        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")

        if (order.status != OrderStatus.PAID) {
            return AppResult.ValidationError(
                globalErrors = listOf("Order must be PAID before fulfillment (current: ${order.status})")
            )
        }

        val success = orderRepository.updateOrderStatus(orderId, OrderStatus.FULFILLMENT_IN_PROGRESS, order.version)
        if (!success) return AppResult.ConflictError("OPTIMISTIC_LOCK", "Order was modified concurrently")

        val locks = inventoryRepository.getLocksByOrderId(orderId)
        locks.filter { it.status == InventoryLockStatus.ACTIVE }.forEach { lock ->
            inventoryRepository.consumeLock(lock.id)
        }

        val now = TimeUtils.nowUtc()
        paymentRepository.createFulfillment(FulfillmentRecord(
            id = IdGenerator.newId(),
            orderId = orderId,
            fulfilledBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            fulfilledAt = now,
            notes = null
        ))

        logOrderTransition(orderId, "PAID", "FULFILLMENT_IN_PROGRESS", now)
        return AppResult.Success(orderRepository.getOrderById(orderId)!!)
    }

    suspend fun confirmDelivery(orderId: String, deliveryType: DeliveryType, notes: String?): AppResult<Order> {
        if (!checkPermission.hasPermission(Permission.ORDER_FULFILL)) {
            return AppResult.PermissionError()
        }

        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")

        val targetStatus = when (deliveryType) {
            DeliveryType.PICKUP -> OrderStatus.AWAITING_PICKUP
            DeliveryType.DELIVERY -> OrderStatus.DELIVERED
        }

        if (!order.status.canTransitionTo(targetStatus)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot transition from ${order.status} to $targetStatus")
            )
        }

        orderRepository.updateOrderStatus(orderId, targetStatus, order.version)

        val now = TimeUtils.nowUtc()
        paymentRepository.createDeliveryConfirmation(DeliveryConfirmation(
            id = IdGenerator.newId(),
            orderId = orderId,
            deliveryType = deliveryType,
            confirmedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            confirmedAt = now,
            notes = notes
        ))

        logOrderTransition(orderId, order.status.name, targetStatus.name, now)
        return AppResult.Success(orderRepository.getOrderById(orderId)!!)
    }

    suspend fun closeOrder(orderId: String): AppResult<Order> {
        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")
        val authResult = authorizeOrderMutate(order)
        if (authResult is AppResult.PermissionError) return authResult

        if (!order.status.canTransitionTo(OrderStatus.CLOSED)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot close order from ${order.status} state")
            )
        }

        orderRepository.updateOrderStatus(orderId, OrderStatus.CLOSED, order.version)
        logOrderTransition(orderId, order.status.name, "CLOSED", TimeUtils.nowUtc())
        return AppResult.Success(orderRepository.getOrderById(orderId)!!)
    }

    suspend fun cancelOrder(orderId: String, reason: String): AppResult<Order> {
        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")
        // Owner can cancel their own unpaid order; staff with permissions can cancel any
        val authResult = authorizeOrderMutate(order, allowOwnerCancel = true)
        if (authResult is AppResult.PermissionError) return authResult

        val targetStatus = OrderStatus.MANUAL_CANCELLED
        if (!order.status.canTransitionTo(targetStatus)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot cancel order from ${order.status} state")
            )
        }

        orderRepository.updateOrderStatus(orderId, targetStatus, order.version)
        releaseOrderLocks(orderId)

        val now = TimeUtils.nowUtc()
        logOrderTransition(orderId, order.status.name, targetStatus.name, now)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(),
            actorId = sessionManager.getCurrentUserId(),
            actorUsername = null,
            actionType = AuditActionType.ORDER_CANCELLED,
            targetEntityType = "Order",
            targetEntityId = orderId,
            beforeSummary = "status=${order.status}",
            afterSummary = "status=$targetStatus",
            reason = reason,
            sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS,
            timestamp = now,
            metadata = null
        ))

        return AppResult.Success(orderRepository.getOrderById(orderId)!!)
    }

    suspend fun initiateReturn(orderId: String, reason: String): AppResult<ReturnExchangeRecord> {
        val order = orderRepository.getOrderById(orderId)
            ?: return AppResult.NotFoundError("ORDER_NOT_FOUND")
        val authResult = authorizeOrderMutate(order, allowOwnerCancel = true)
        if (authResult is AppResult.PermissionError) return authResult

        if (!order.status.canTransitionTo(OrderStatus.RETURN_REQUESTED)) {
            return AppResult.ValidationError(
                globalErrors = listOf("Cannot initiate return from ${order.status} state")
            )
        }

        orderRepository.updateOrderStatus(orderId, OrderStatus.RETURN_REQUESTED, order.version)

        val now = TimeUtils.nowUtc()
        val record = ReturnExchangeRecord(
            id = IdGenerator.newId(),
            originalOrderId = orderId,
            replacementOrderId = null,
            recordType = ReturnExchangeType.RETURN,
            reason = reason,
            processedBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            processedAt = now,
            notes = null
        )
        paymentRepository.createReturnExchange(record)

        logOrderTransition(orderId, order.status.name, "RETURN_REQUESTED", now)
        return AppResult.Success(record)
    }

    internal suspend fun releaseOrderLocks(orderId: String) {
        val locks = inventoryRepository.getLocksByOrderId(orderId)
        locks.filter { it.status == InventoryLockStatus.ACTIVE }.forEach { lock ->
            inventoryRepository.releaseLock(lock.id, "ORDER_CANCELLED")
            val item = inventoryRepository.getItemById(lock.inventoryItemId)
            if (item != null) {
                inventoryRepository.adjustReservedStock(item.id, -lock.quantity, item.version)
            }
        }
    }

    private suspend fun logOrderTransition(orderId: String, from: String, to: String, now: java.time.Instant) {
        auditRepository.logStateTransition(StateTransitionLog(
            id = IdGenerator.newId(),
            entityType = "Order",
            entityId = orderId,
            fromState = from,
            toState = to,
            triggeredBy = sessionManager.getCurrentUserId() ?: "SYSTEM",
            reason = null,
            timestamp = now
        ))
    }
}
