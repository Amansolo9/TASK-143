package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.CartEntity
import com.learnmart.app.data.local.entity.CartLineItemEntity
import com.learnmart.app.data.local.entity.DeliveryConfirmationEntity
import com.learnmart.app.data.local.entity.FulfillmentRecordEntity
import com.learnmart.app.data.local.entity.IdempotencyTokenEntity
import com.learnmart.app.data.local.entity.InventoryItemEntity
import com.learnmart.app.data.local.entity.InventoryLockEntity
import com.learnmart.app.data.local.entity.OrderEntity
import com.learnmart.app.data.local.entity.OrderLineItemEntity
import com.learnmart.app.data.local.entity.OrderPriceComponentEntity
import com.learnmart.app.data.local.entity.QuoteSnapshotEntity
import com.learnmart.app.data.local.entity.ReturnExchangeRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommerceDao {

    // ──────────────────────────────────────────────
    // Cart
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCart(cart: CartEntity)

    @Query("SELECT * FROM carts WHERE id = :id")
    suspend fun getCartById(id: String): CartEntity?

    @Query("SELECT * FROM carts WHERE user_id = :userId AND status = 'ACTIVE' LIMIT 1")
    fun getActiveCartForUser(userId: String): Flow<CartEntity?>

    @Update
    suspend fun updateCart(cart: CartEntity)

    // ──────────────────────────────────────────────
    // CartLineItem
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCartLineItem(item: CartLineItemEntity)

    @Query("SELECT * FROM cart_line_items WHERE cart_id = :cartId")
    suspend fun getCartLineItemsByCartId(cartId: String): List<CartLineItemEntity>

    @Query("DELETE FROM cart_line_items WHERE cart_id = :cartId")
    suspend fun deleteCartLineItemsByCartId(cartId: String)

    @Query("DELETE FROM cart_line_items WHERE id = :id")
    suspend fun deleteCartLineItemById(id: String)

    // ──────────────────────────────────────────────
    // QuoteSnapshot
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuoteSnapshot(snapshot: QuoteSnapshotEntity)

    @Query("SELECT * FROM quote_snapshots WHERE cart_id = :cartId ORDER BY quoted_at DESC")
    suspend fun getQuoteSnapshotsByCartId(cartId: String): List<QuoteSnapshotEntity>

    // ──────────────────────────────────────────────
    // Order
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: OrderEntity)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getOrdersByUserId(userId: String, limit: Int, offset: Int): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY created_at DESC")
    suspend fun getOrdersByStatus(status: String): List<OrderEntity>

    @Query("""
        UPDATE orders SET status = :status, updated_at = :updatedAt, version = version + 1
        WHERE id = :id AND version = :currentVersion
    """)
    suspend fun updateOrderStatus(id: String, status: String, updatedAt: Long, currentVersion: Int): Int

    @Query("""
        SELECT * FROM orders
        WHERE idempotency_token = :token
        LIMIT 1
    """)
    suspend fun getOrderByIdempotencyToken(token: String): OrderEntity?

    // ──────────────────────────────────────────────
    // OrderLineItem
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrderLineItems(items: List<OrderLineItemEntity>)

    @Query("SELECT * FROM order_line_items WHERE order_id = :orderId")
    suspend fun getOrderLineItemsByOrderId(orderId: String): List<OrderLineItemEntity>

    // ──────────────────────────────────────────────
    // OrderPriceComponent
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrderPriceComponents(components: List<OrderPriceComponentEntity>)

    @Query("SELECT * FROM order_price_components WHERE order_id = :orderId")
    suspend fun getOrderPriceComponentsByOrderId(orderId: String): List<OrderPriceComponentEntity>

    // ──────────────────────────────────────────────
    // InventoryItem
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryItem(item: InventoryItemEntity)

    @Update
    suspend fun updateInventoryItem(item: InventoryItemEntity)

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getInventoryItemById(id: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE material_id = :materialId")
    suspend fun getInventoryItemByMaterialId(materialId: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE sku = :sku")
    suspend fun getInventoryItemBySku(sku: String): InventoryItemEntity?

    @Query("""
        UPDATE inventory_items
        SET reserved_stock = reserved_stock + :delta,
            available_stock = total_stock - reserved_stock - :delta,
            version = version + 1
        WHERE id = :id AND version = :version
    """)
    suspend fun adjustReservedStock(id: String, delta: Int, version: Int): Int

    // ──────────────────────────────────────────────
    // InventoryLock
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryLock(lock: InventoryLockEntity)

    @Update
    suspend fun updateInventoryLock(lock: InventoryLockEntity)

    @Query("SELECT * FROM inventory_locks WHERE id = :id")
    suspend fun getInventoryLockById(id: String): InventoryLockEntity?

    @Query("SELECT * FROM inventory_locks WHERE inventory_item_id = :inventoryItemId AND status = 'ACTIVE'")
    suspend fun getActiveInventoryLocksByInventoryItemId(inventoryItemId: String): List<InventoryLockEntity>

    @Query("SELECT * FROM inventory_locks WHERE order_id = :orderId")
    suspend fun getInventoryLocksByOrderId(orderId: String): List<InventoryLockEntity>

    @Query("SELECT * FROM inventory_locks WHERE status = 'ACTIVE' AND expires_at < :currentTime")
    suspend fun getExpiredInventoryLocks(currentTime: Long): List<InventoryLockEntity>

    @Query("UPDATE inventory_locks SET status = :status WHERE id = :id")
    suspend fun updateInventoryLockStatus(id: String, status: String)

    // ──────────────────────────────────────────────
    // FulfillmentRecord
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFulfillmentRecord(record: FulfillmentRecordEntity)

    @Query("SELECT * FROM fulfillment_records WHERE order_id = :orderId")
    suspend fun getFulfillmentRecordsByOrderId(orderId: String): List<FulfillmentRecordEntity>

    // ──────────────────────────────────────────────
    // DeliveryConfirmation
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeliveryConfirmation(confirmation: DeliveryConfirmationEntity)

    @Query("SELECT * FROM delivery_confirmations WHERE order_id = :orderId")
    suspend fun getDeliveryConfirmationsByOrderId(orderId: String): List<DeliveryConfirmationEntity>

    // ──────────────────────────────────────────────
    // ReturnExchangeRecord
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturnExchangeRecord(record: ReturnExchangeRecordEntity)

    @Query("SELECT * FROM return_exchange_records WHERE original_order_id = :originalOrderId")
    suspend fun getReturnExchangeRecordsByOriginalOrderId(originalOrderId: String): List<ReturnExchangeRecordEntity>

    // ──────────────────────────────────────────────
    // IdempotencyToken
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdempotencyToken(token: IdempotencyTokenEntity)

    @Query("SELECT * FROM idempotency_tokens WHERE token = :token")
    suspend fun getIdempotencyTokenByToken(token: String): IdempotencyTokenEntity?

    @Query("DELETE FROM idempotency_tokens WHERE expires_at < :currentTime")
    suspend fun deleteExpiredIdempotencyTokens(currentTime: Long)
}
