package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.CommerceDao
import com.learnmart.app.data.local.entity.IdempotencyTokenEntity
import com.learnmart.app.data.local.entity.OrderEntity
import com.learnmart.app.data.local.entity.OrderLineItemEntity
import com.learnmart.app.data.local.entity.OrderPriceComponentEntity
import com.learnmart.app.domain.model.IdempotencyToken
import com.learnmart.app.domain.model.LineItemType
import com.learnmart.app.domain.model.Order
import com.learnmart.app.domain.model.OrderLineItem
import com.learnmart.app.domain.model.OrderPriceComponent
import com.learnmart.app.domain.model.OrderStatus
import com.learnmart.app.domain.model.PriceComponentType
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val commerceDao: CommerceDao
) : OrderRepository {

    override suspend fun createOrder(order: Order): Order {
        val entity = order.toEntity()
        commerceDao.insertOrder(entity)
        return entity.toDomain()
    }

    override suspend fun updateOrder(order: Order): Boolean {
        val existing = commerceDao.getOrderById(order.id) ?: return false
        commerceDao.updateOrder(order.toEntity())
        return true
    }

    override suspend fun getOrderById(id: String): Order? =
        commerceDao.getOrderById(id)?.toDomain()

    override suspend fun getOrdersForUser(userId: String, limit: Int, offset: Int): List<Order> =
        commerceDao.getOrdersByUserId(userId, limit, offset).map { it.toDomain() }

    override suspend fun getOrdersByStatus(status: OrderStatus): List<Order> =
        commerceDao.getOrdersByStatus(status.name).map { it.toDomain() }

    override suspend fun updateOrderStatus(id: String, status: OrderStatus, currentVersion: Int): Boolean {
        val updatedAt = TimeUtils.nowUtc().toEpochMilli()
        val rows = commerceDao.updateOrderStatus(id, status.name, updatedAt, currentVersion)
        return rows > 0
    }

    override suspend fun addOrderLineItems(items: List<OrderLineItem>) {
        commerceDao.insertAllOrderLineItems(items.map { it.toEntity() })
    }

    override suspend fun getOrderLineItems(orderId: String): List<OrderLineItem> =
        commerceDao.getOrderLineItemsByOrderId(orderId).map { it.toDomain() }

    override suspend fun addOrderPriceComponents(components: List<OrderPriceComponent>) {
        commerceDao.insertAllOrderPriceComponents(components.map { it.toEntity() })
    }

    override suspend fun getOrderPriceComponents(orderId: String): List<OrderPriceComponent> =
        commerceDao.getOrderPriceComponentsByOrderId(orderId).map { it.toDomain() }

    override suspend fun findByIdempotencyToken(token: String): Order? {
        val tokenEntity = commerceDao.getIdempotencyTokenByToken(token) ?: return null
        return commerceDao.getOrderById(tokenEntity.resultReference)?.toDomain()
    }

    override suspend fun saveIdempotencyToken(idempotencyToken: IdempotencyToken) {
        commerceDao.insertIdempotencyToken(idempotencyToken.toEntity())
    }

    override suspend fun getIdempotencyToken(token: String): IdempotencyToken? =
        commerceDao.getIdempotencyTokenByToken(token)?.toDomain()

    override suspend fun cleanupExpiredTokens() {
        val now = TimeUtils.nowUtc().toEpochMilli()
        commerceDao.deleteExpiredIdempotencyTokens(now)
    }

    // ==================== Entity <-> Domain Mapping ====================

    private fun OrderEntity.toDomain() = Order(
        id = id,
        userId = userId,
        status = OrderStatus.valueOf(status),
        idempotencyToken = idempotencyToken,
        subtotal = BigDecimal(subtotal),
        discountTotal = BigDecimal(discountTotal),
        taxAmount = BigDecimal(taxAmount),
        serviceFee = BigDecimal(serviceFee),
        packagingFee = BigDecimal(packagingFee),
        grandTotal = BigDecimal(grandTotal),
        paidAmount = BigDecimal(paidAmount),
        placedAt = placedAt?.let { Instant.ofEpochMilli(it) },
        cancelledAt = cancelledAt?.let { Instant.ofEpochMilli(it) },
        cancelReason = cancelReason,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun Order.toEntity() = OrderEntity(
        id = id,
        userId = userId,
        status = status.name,
        idempotencyToken = idempotencyToken,
        subtotal = subtotal.toPlainString(),
        discountTotal = discountTotal.toPlainString(),
        taxAmount = taxAmount.toPlainString(),
        serviceFee = serviceFee.toPlainString(),
        packagingFee = packagingFee.toPlainString(),
        grandTotal = grandTotal.toPlainString(),
        paidAmount = paidAmount.toPlainString(),
        placedAt = placedAt?.toEpochMilli(),
        cancelledAt = cancelledAt?.toEpochMilli(),
        cancelReason = cancelReason,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    private fun OrderLineItemEntity.toDomain() = OrderLineItem(
        id = id,
        orderId = orderId,
        itemType = LineItemType.valueOf(itemType),
        referenceId = referenceId,
        classOfferingId = classOfferingId,
        title = title,
        quantity = quantity,
        unitPrice = BigDecimal(unitPrice),
        lineTotal = BigDecimal(lineTotal)
    )

    private fun OrderLineItem.toEntity() = OrderLineItemEntity(
        id = id,
        orderId = orderId,
        itemType = itemType.name,
        referenceId = referenceId,
        classOfferingId = classOfferingId,
        title = title,
        quantity = quantity,
        unitPrice = unitPrice.toPlainString(),
        lineTotal = lineTotal.toPlainString()
    )

    private fun OrderPriceComponentEntity.toDomain() = OrderPriceComponent(
        id = id,
        orderId = orderId,
        componentType = PriceComponentType.valueOf(componentType),
        amount = BigDecimal(amount),
        rate = rate?.let { BigDecimal(it) },
        description = description
    )

    private fun OrderPriceComponent.toEntity() = OrderPriceComponentEntity(
        id = id,
        orderId = orderId,
        componentType = componentType.name,
        amount = amount.toPlainString(),
        rate = rate?.toPlainString(),
        description = description
    )

    private fun IdempotencyTokenEntity.toDomain() = IdempotencyToken(
        token = token,
        requestHash = requestHash,
        resultReference = resultReference,
        createdAt = Instant.ofEpochMilli(createdAt),
        expiresAt = Instant.ofEpochMilli(expiresAt)
    )

    private fun IdempotencyToken.toEntity() = IdempotencyTokenEntity(
        token = token,
        requestHash = requestHash,
        resultReference = resultReference,
        createdAt = createdAt.toEpochMilli(),
        expiresAt = expiresAt.toEpochMilli()
    )
}
