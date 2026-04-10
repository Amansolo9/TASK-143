package com.learnmart.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.InventoryRepository
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker handling:
 * 1. Auto-cancel unpaid orders after 30 minutes
 * 2. Auto-close awaiting-pickup orders after 7 days
 * 3. Release expired inventory locks
 *
 * Idempotent and safe to re-run.
 */
@HiltWorker
class OrderTimeoutWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            handleUnpaidOrders()
            handleAwaitingPickup()
            handleExpiredInventoryLocks()
            orderRepository.cleanupExpiredTokens()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    private suspend fun handleUnpaidOrders() {
        val cancelMinutes = policyRepository.getPolicyLongValue(
            PolicyType.COMMERCE, "order_unpaid_cancel_minutes", 30
        )
        val cutoffTime = TimeUtils.nowUtc().minusSeconds(cancelMinutes * 60)

        val unpaidOrders = orderRepository.getOrdersByStatus(OrderStatus.PLACED_UNPAID) +
                orderRepository.getOrdersByStatus(OrderStatus.PARTIALLY_PAID)

        val now = TimeUtils.nowUtc()
        unpaidOrders.filter { it.placedAt != null && it.placedAt.isBefore(cutoffTime) }.forEach { order ->
            orderRepository.updateOrderStatus(order.id, OrderStatus.AUTO_CANCELLED, order.version)

            // Release inventory locks
            val locks = inventoryRepository.getLocksByOrderId(order.id)
            locks.filter { it.status == InventoryLockStatus.ACTIVE }.forEach { lock ->
                inventoryRepository.releaseLock(lock.id, "AUTO_CANCELLED")
                val item = inventoryRepository.getItemById(lock.inventoryItemId)
                if (item != null) {
                    inventoryRepository.adjustReservedStock(item.id, -lock.quantity, item.version)
                }
            }

            auditRepository.logStateTransition(StateTransitionLog(
                id = IdGenerator.newId(),
                entityType = "Order",
                entityId = order.id,
                fromState = order.status.name,
                toState = "AUTO_CANCELLED",
                triggeredBy = "SYSTEM",
                reason = "Unpaid after $cancelMinutes minutes",
                timestamp = now
            ))

            auditRepository.logEvent(AuditEvent(
                id = IdGenerator.newId(),
                actorId = null,
                actorUsername = "SYSTEM",
                actionType = AuditActionType.ORDER_AUTO_CANCELLED,
                targetEntityType = "Order",
                targetEntityId = order.id,
                beforeSummary = "status=${order.status}",
                afterSummary = "status=AUTO_CANCELLED",
                reason = "Auto-cancel: unpaid timeout",
                sessionId = null,
                outcome = AuditOutcome.SUCCESS,
                timestamp = now,
                metadata = null
            ))
        }
    }

    private suspend fun handleAwaitingPickup() {
        val closeDays = policyRepository.getPolicyLongValue(
            PolicyType.COMMERCE, "awaiting_pickup_close_days", 7
        )
        val cutoffTime = TimeUtils.nowUtc().minusSeconds(closeDays * 86400)

        val awaitingOrders = orderRepository.getOrdersByStatus(OrderStatus.AWAITING_PICKUP)
        val now = TimeUtils.nowUtc()

        awaitingOrders.filter { it.updatedAt.isBefore(cutoffTime) }.forEach { order ->
            orderRepository.updateOrderStatus(order.id, OrderStatus.CLOSED, order.version)

            auditRepository.logStateTransition(StateTransitionLog(
                id = IdGenerator.newId(),
                entityType = "Order",
                entityId = order.id,
                fromState = "AWAITING_PICKUP",
                toState = "CLOSED",
                triggeredBy = "SYSTEM",
                reason = "Auto-closed after $closeDays days awaiting pickup",
                timestamp = now
            ))

            auditRepository.logEvent(AuditEvent(
                id = IdGenerator.newId(),
                actorId = null,
                actorUsername = "SYSTEM",
                actionType = AuditActionType.ORDER_CLOSED,
                targetEntityType = "Order",
                targetEntityId = order.id,
                beforeSummary = "status=AWAITING_PICKUP",
                afterSummary = "status=CLOSED",
                reason = "Auto-close: pickup timeout",
                sessionId = null,
                outcome = AuditOutcome.SUCCESS,
                timestamp = now,
                metadata = null
            ))
        }
    }

    private suspend fun handleExpiredInventoryLocks() {
        val expiredLocks = inventoryRepository.getExpiredLocks()
        val now = TimeUtils.nowUtc()

        expiredLocks.forEach { lock ->
            inventoryRepository.releaseLock(lock.id, "EXPIRED")
            val item = inventoryRepository.getItemById(lock.inventoryItemId)
            if (item != null) {
                inventoryRepository.adjustReservedStock(item.id, -lock.quantity, item.version)
            }

            auditRepository.logEvent(AuditEvent(
                id = IdGenerator.newId(),
                actorId = null,
                actorUsername = "SYSTEM",
                actionType = AuditActionType.INVENTORY_LOCK_EXPIRED,
                targetEntityType = "InventoryLock",
                targetEntityId = lock.id,
                beforeSummary = "status=ACTIVE",
                afterSummary = "status=EXPIRED",
                reason = "Lock expired at ${lock.expiresAt}",
                sessionId = null,
                outcome = AuditOutcome.SUCCESS,
                timestamp = now,
                metadata = null
            ))
        }
    }
}
