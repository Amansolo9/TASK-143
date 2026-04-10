package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface CartRepository {
    suspend fun createCart(cart: Cart): Cart
    suspend fun getCartById(id: String): Cart?
    suspend fun getActiveCartForUser(userId: String): Cart?
    suspend fun updateCart(cart: Cart)
    suspend fun addLineItem(item: CartLineItem)
    suspend fun getLineItemsForCart(cartId: String): List<CartLineItem>
    suspend fun clearCartItems(cartId: String)
    suspend fun removeLineItem(lineItemId: String)
    suspend fun createQuoteSnapshot(quote: QuoteSnapshot)
    suspend fun getQuoteForCart(cartId: String): QuoteSnapshot?
}

interface OrderRepository {
    suspend fun createOrder(order: Order): Order
    suspend fun updateOrder(order: Order): Boolean
    suspend fun getOrderById(id: String): Order?
    suspend fun getOrdersForUser(userId: String, limit: Int, offset: Int): List<Order>
    suspend fun getOrdersByStatus(status: OrderStatus): List<Order>
    suspend fun updateOrderStatus(id: String, status: OrderStatus, currentVersion: Int): Boolean
    suspend fun addOrderLineItems(items: List<OrderLineItem>)
    suspend fun getOrderLineItems(orderId: String): List<OrderLineItem>
    suspend fun addOrderPriceComponents(components: List<OrderPriceComponent>)
    suspend fun getOrderPriceComponents(orderId: String): List<OrderPriceComponent>
    suspend fun findByIdempotencyToken(token: String): Order?
    suspend fun saveIdempotencyToken(idempotencyToken: IdempotencyToken)
    suspend fun getIdempotencyToken(token: String): IdempotencyToken?
    suspend fun cleanupExpiredTokens()
}

interface InventoryRepository {
    suspend fun createItem(item: InventoryItem): InventoryItem
    suspend fun updateItem(item: InventoryItem): Boolean
    suspend fun getItemById(id: String): InventoryItem?
    suspend fun getItemByMaterialId(materialId: String): InventoryItem?
    suspend fun getItemBySku(sku: String): InventoryItem?
    suspend fun adjustReservedStock(id: String, delta: Int, currentVersion: Int): Boolean
    suspend fun acquireLock(lock: InventoryLock): InventoryLock
    suspend fun releaseLock(lockId: String, reason: String)
    suspend fun consumeLock(lockId: String)
    suspend fun getActiveLocksByInventoryItem(inventoryItemId: String): List<InventoryLock>
    suspend fun getLocksByOrderId(orderId: String): List<InventoryLock>
    suspend fun getExpiredLocks(): List<InventoryLock>
}

interface PaymentRepository {
    suspend fun recordPayment(payment: PaymentRecord): PaymentRecord
    suspend fun updatePayment(payment: PaymentRecord): Boolean
    suspend fun getPaymentById(id: String): PaymentRecord?
    suspend fun getPaymentsForOrder(orderId: String): List<PaymentRecord>
    suspend fun updatePaymentStatus(id: String, status: PaymentStatus, currentVersion: Int): Boolean
    suspend fun createAllocation(allocation: PaymentAllocation)
    suspend fun getAllocationsForOrder(orderId: String): List<PaymentAllocation>
    suspend fun getTotalAllocatedForOrder(orderId: String): BigDecimal
    suspend fun recordRefund(refund: RefundRecord): RefundRecord
    suspend fun getRefundsForOrder(orderId: String): List<RefundRecord>
    suspend fun countRefundsForLearnerToday(learnerId: String, dayStartMs: Long, dayEndMs: Long): Int
    suspend fun getRefundsForLearner(learnerId: String): List<RefundRecord>
    suspend fun createLedgerEntry(entry: LedgerEntry)
    suspend fun getLedgerEntriesForOrder(orderId: String): List<LedgerEntry>
    suspend fun getAllLedgerEntries(limit: Int, offset: Int): List<LedgerEntry>
    suspend fun createFulfillment(record: FulfillmentRecord)
    suspend fun getFulfillmentForOrder(orderId: String): FulfillmentRecord?
    suspend fun createDeliveryConfirmation(confirmation: DeliveryConfirmation)
    suspend fun getDeliveryConfirmation(orderId: String): DeliveryConfirmation?
    suspend fun createReturnExchange(record: ReturnExchangeRecord)
    suspend fun getReturnExchangesForOrder(orderId: String): List<ReturnExchangeRecord>
}
