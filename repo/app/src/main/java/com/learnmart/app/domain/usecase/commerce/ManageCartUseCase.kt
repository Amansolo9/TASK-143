package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.CartRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import javax.inject.Inject

data class AddToCartRequest(
    val itemType: LineItemType,
    val referenceId: String,
    val classOfferingId: String?,
    val title: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

class ManageCartUseCase @Inject constructor(
    private val cartRepository: CartRepository,
    private val courseRepository: CourseRepository,
    private val pricingEngine: PricingEngine,
    private val sessionManager: SessionManager
) {
    suspend fun getOrCreateCart(): AppResult<Cart> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        val existing = cartRepository.getActiveCartForUser(userId)
        if (existing != null) return AppResult.Success(existing)

        val checkoutPolicy = pricingEngine.getCheckoutPolicy()
        val now = TimeUtils.nowUtc()
        val cart = Cart(
            id = IdGenerator.newId(),
            userId = userId,
            status = CartStatus.ACTIVE,
            checkoutPolicy = checkoutPolicy,
            createdAt = now,
            updatedAt = now
        )
        cartRepository.createCart(cart)
        return AppResult.Success(cart)
    }

    suspend fun addItem(request: AddToCartRequest): AppResult<CartLineItem> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        // Validate
        if (request.quantity < 1) {
            return AppResult.ValidationError(fieldErrors = mapOf("quantity" to "Quantity must be >= 1"))
        }
        if (request.itemType == LineItemType.COURSE_FEE && request.quantity != 1) {
            return AppResult.ValidationError(fieldErrors = mapOf("quantity" to "Course fee quantity must be 1"))
        }

        val cartResult = getOrCreateCart()
        if (cartResult !is AppResult.Success) return AppResult.ValidationError(globalErrors = listOf("Failed to get cart"))
        val cart = cartResult.data

        // Checkout policy check
        if (cart.checkoutPolicy == CheckoutPolicy.SAME_CLASS_ONLY && request.classOfferingId != null) {
            val existingItems = cartRepository.getLineItemsForCart(cart.id)
            val existingClassIds = existingItems.mapNotNull { it.classOfferingId }.distinct()
            if (existingClassIds.isNotEmpty() && request.classOfferingId !in existingClassIds) {
                return AppResult.ValidationError(
                    globalErrors = listOf("Checkout policy requires all items from the same class. Clear cart first.")
                )
            }
        }

        val lineTotal = MoneyUtils.round(request.unitPrice.multiply(BigDecimal(request.quantity)))
        val item = CartLineItem(
            id = IdGenerator.newId(),
            cartId = cart.id,
            itemType = request.itemType,
            referenceId = request.referenceId,
            classOfferingId = request.classOfferingId,
            title = request.title,
            quantity = request.quantity,
            unitPrice = request.unitPrice,
            lineTotal = lineTotal,
            createdAt = TimeUtils.nowUtc()
        )

        cartRepository.addLineItem(item)
        return AppResult.Success(item)
    }

    suspend fun removeItem(lineItemId: String): AppResult<Unit> {
        cartRepository.removeLineItem(lineItemId)
        return AppResult.Success(Unit)
    }

    suspend fun getCartItems(): AppResult<List<CartLineItem>> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        val cart = cartRepository.getActiveCartForUser(userId)
            ?: return AppResult.Success(emptyList())

        return AppResult.Success(cartRepository.getLineItemsForCart(cart.id))
    }

    suspend fun getCartPricing(): AppResult<PricingResult> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        val cart = cartRepository.getActiveCartForUser(userId)
            ?: return AppResult.Success(pricingEngine.calculatePricing(emptyList()))

        val items = cartRepository.getLineItemsForCart(cart.id)
        return AppResult.Success(pricingEngine.calculatePricing(items))
    }

    suspend fun clearCart(): AppResult<Unit> {
        val userId = sessionManager.getCurrentUserId()
            ?: return AppResult.PermissionError("Not authenticated")

        val cart = cartRepository.getActiveCartForUser(userId)
        if (cart != null) {
            cartRepository.clearCartItems(cart.id)
        }
        return AppResult.Success(Unit)
    }
}
