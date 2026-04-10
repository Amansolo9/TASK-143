package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.CommerceDao
import com.learnmart.app.data.local.entity.CartEntity
import com.learnmart.app.data.local.entity.CartLineItemEntity
import com.learnmart.app.data.local.entity.QuoteSnapshotEntity
import com.learnmart.app.domain.model.Cart
import com.learnmart.app.domain.model.CartLineItem
import com.learnmart.app.domain.model.CartStatus
import com.learnmart.app.domain.model.CheckoutPolicy
import com.learnmart.app.domain.model.LineItemType
import com.learnmart.app.domain.model.QuoteSnapshot
import com.learnmart.app.domain.repository.CartRepository
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.firstOrNull
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepositoryImpl @Inject constructor(
    private val commerceDao: CommerceDao
) : CartRepository {

    override suspend fun createCart(cart: Cart): Cart {
        val entity = cart.toEntity()
        commerceDao.insertCart(entity)
        return entity.toDomain()
    }

    override suspend fun getCartById(id: String): Cart? =
        commerceDao.getCartById(id)?.toDomain()

    override suspend fun getActiveCartForUser(userId: String): Cart? =
        commerceDao.getActiveCartForUser(userId).firstOrNull()?.toDomain()

    override suspend fun updateCart(cart: Cart) {
        commerceDao.updateCart(cart.toEntity())
    }

    override suspend fun addLineItem(item: CartLineItem) {
        commerceDao.insertCartLineItem(item.toEntity())
    }

    override suspend fun getLineItemsForCart(cartId: String): List<CartLineItem> =
        commerceDao.getCartLineItemsByCartId(cartId).map { it.toDomain() }

    override suspend fun clearCartItems(cartId: String) {
        commerceDao.deleteCartLineItemsByCartId(cartId)
    }

    override suspend fun removeLineItem(lineItemId: String) {
        commerceDao.deleteCartLineItemById(lineItemId)
    }

    override suspend fun createQuoteSnapshot(quote: QuoteSnapshot) {
        commerceDao.insertQuoteSnapshot(quote.toEntity())
    }

    override suspend fun getQuoteForCart(cartId: String): QuoteSnapshot? =
        commerceDao.getQuoteSnapshotsByCartId(cartId).firstOrNull()?.toDomain()

    // ==================== Entity <-> Domain Mapping ====================

    private fun CartEntity.toDomain() = Cart(
        id = id,
        userId = userId,
        status = CartStatus.valueOf(status),
        checkoutPolicy = CheckoutPolicy.valueOf(checkoutPolicy),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun Cart.toEntity() = CartEntity(
        id = id,
        userId = userId,
        status = status.name,
        checkoutPolicy = checkoutPolicy.name,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun CartLineItemEntity.toDomain() = CartLineItem(
        id = id,
        cartId = cartId,
        itemType = LineItemType.valueOf(itemType),
        referenceId = referenceId,
        classOfferingId = classOfferingId,
        title = title,
        quantity = quantity,
        unitPrice = BigDecimal(unitPrice),
        lineTotal = BigDecimal(lineTotal),
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun CartLineItem.toEntity() = CartLineItemEntity(
        id = id,
        cartId = cartId,
        itemType = itemType.name,
        referenceId = referenceId,
        classOfferingId = classOfferingId,
        title = title,
        quantity = quantity,
        unitPrice = unitPrice.toPlainString(),
        lineTotal = lineTotal.toPlainString(),
        createdAt = createdAt.toEpochMilli()
    )

    private fun QuoteSnapshotEntity.toDomain() = QuoteSnapshot(
        id = id,
        cartId = cartId,
        subtotal = BigDecimal(subtotal),
        discountTotal = BigDecimal(discountTotal),
        taxAmount = BigDecimal(taxAmount),
        serviceFee = BigDecimal(serviceFee),
        packagingFee = BigDecimal(packagingFee),
        grandTotal = BigDecimal(grandTotal),
        taxRate = BigDecimal(taxRate),
        serviceFeeRate = BigDecimal(serviceFeeRate),
        quotedAt = Instant.ofEpochMilli(quotedAt),
        validUntil = Instant.ofEpochMilli(validUntil)
    )

    private fun QuoteSnapshot.toEntity() = QuoteSnapshotEntity(
        id = id,
        cartId = cartId,
        subtotal = subtotal.toPlainString(),
        discountTotal = discountTotal.toPlainString(),
        taxAmount = taxAmount.toPlainString(),
        serviceFee = serviceFee.toPlainString(),
        packagingFee = packagingFee.toPlainString(),
        grandTotal = grandTotal.toPlainString(),
        taxRate = taxRate.toPlainString(),
        serviceFeeRate = serviceFeeRate.toPlainString(),
        quotedAt = quotedAt.toEpochMilli(),
        validUntil = validUntil.toEpochMilli()
    )
}
