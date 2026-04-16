package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.data.local.TransactionRunner
import com.learnmart.app.data.local.dao.BlacklistDao
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.*
import com.learnmart.app.security.DeviceFingerprintProvider
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CheckoutUseCaseTest {
    private lateinit var cartRepository: CartRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var pricingEngine: PricingEngine
    private lateinit var blacklistDao: BlacklistDao
    private lateinit var sessionManager: SessionManager
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var deviceFingerprintProvider: DeviceFingerprintProvider
    private lateinit var useCase: CheckoutUseCase

    @Before
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        inventoryRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        pricingEngine = mockk(relaxed = true)
        blacklistDao = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        transactionRunner = mockk()
        deviceFingerprintProvider = mockk(relaxed = true)

        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } coAnswers {
            firstArg<suspend () -> Any>().invoke()
        }
        coEvery { blacklistDao.isBlacklisted(any()) } returns 0
        coEvery { policyRepository.getPolicyLongValue(any(), any(), any()) } returns 5L

        useCase = CheckoutUseCase(cartRepository, orderRepository, inventoryRepository, policyRepository, auditRepository, pricingEngine, blacklistDao, sessionManager, transactionRunner, deviceFingerprintProvider)
    }

    @Test
    fun `unauthenticated user gets permission error`() = runTest {
        val result = useCase.submitOrder("cart-1", "token-1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `missing cart returns not found`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getIdempotencyToken(any()) } returns null
        coEvery { cartRepository.getCartById("missing") } returns null
        val result = useCase.submitOrder("missing", "token-1")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `cart belonging to different user returns permission error`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getIdempotencyToken(any()) } returns null
        coEvery { cartRepository.getCartById("cart-1") } returns Cart(
            "cart-1", "other-user", CartStatus.ACTIVE, CheckoutPolicy.CROSS_CLASS_ALLOWED,
            Instant.now(), Instant.now()
        )
        val result = useCase.submitOrder("cart-1", "token-1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `empty cart returns validation error`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getIdempotencyToken(any()) } returns null
        coEvery { cartRepository.getCartById("cart-1") } returns Cart(
            "cart-1", "user-1", CartStatus.ACTIVE, CheckoutPolicy.CROSS_CLASS_ALLOWED,
            Instant.now(), Instant.now()
        )
        coEvery { cartRepository.getLineItemsForCart("cart-1") } returns emptyList()
        val result = useCase.submitOrder("cart-1", "token-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `blacklisted user rejected`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getIdempotencyToken(any()) } returns null
        coEvery { cartRepository.getCartById("cart-1") } returns Cart(
            "cart-1", "user-1", CartStatus.ACTIVE, CheckoutPolicy.CROSS_CLASS_ALLOWED,
            Instant.now(), Instant.now()
        )
        coEvery { cartRepository.getLineItemsForCart("cart-1") } returns listOf(mockk(relaxed = true))
        coEvery { pricingEngine.getCheckoutPolicy() } returns CheckoutPolicy.CROSS_CLASS_ALLOWED
        coEvery { blacklistDao.isBlacklisted("user-1") } returns 1
        val result = useCase.submitOrder("cart-1", "token-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `order below minimum total rejected`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getIdempotencyToken(any()) } returns null
        coEvery { cartRepository.getCartById("cart-1") } returns Cart(
            "cart-1", "user-1", CartStatus.ACTIVE, CheckoutPolicy.CROSS_CLASS_ALLOWED,
            Instant.now(), Instant.now()
        )
        val item = mockk<CartLineItem>(relaxed = true)
        every { item.itemType } returns LineItemType.COURSE_FEE
        coEvery { cartRepository.getLineItemsForCart("cart-1") } returns listOf(item)
        coEvery { pricingEngine.getCheckoutPolicy() } returns CheckoutPolicy.CROSS_CLASS_ALLOWED
        coEvery { pricingEngine.calculatePricing(any()) } returns PricingResult(
            subtotal = BigDecimal("5.00"), discountTotal = BigDecimal.ZERO,
            taxAmount = BigDecimal("0.40"), serviceFee = BigDecimal("0.15"),
            packagingFee = BigDecimal("1.50"), grandTotal = BigDecimal("7.05"),
            taxRate = BigDecimal("0.08"), serviceFeeRate = BigDecimal("0.03"),
            hasPhysicalItems = false
        )
        coEvery { pricingEngine.getMinimumOrderTotal() } returns BigDecimal("25.00")
        val result = useCase.submitOrder("cart-1", "token-1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `transaction failure returns system error`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getIdempotencyToken(any()) } returns null
        coEvery { cartRepository.getCartById("cart-1") } returns Cart(
            "cart-1", "user-1", CartStatus.ACTIVE, CheckoutPolicy.CROSS_CLASS_ALLOWED,
            Instant.now(), Instant.now()
        )
        val item = mockk<CartLineItem>(relaxed = true)
        every { item.itemType } returns LineItemType.COURSE_FEE
        coEvery { cartRepository.getLineItemsForCart("cart-1") } returns listOf(item)
        coEvery { pricingEngine.getCheckoutPolicy() } returns CheckoutPolicy.CROSS_CLASS_ALLOWED
        coEvery { pricingEngine.calculatePricing(any()) } returns PricingResult(
            subtotal = BigDecimal("50.00"), discountTotal = BigDecimal.ZERO,
            taxAmount = BigDecimal("4.00"), serviceFee = BigDecimal("1.50"),
            packagingFee = BigDecimal("1.50"), grandTotal = BigDecimal("57.00"),
            taxRate = BigDecimal("0.08"), serviceFeeRate = BigDecimal("0.03"),
            hasPhysicalItems = false
        )
        coEvery { pricingEngine.getMinimumOrderTotal() } returns BigDecimal("25.00")
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } throws RuntimeException("DB fail")

        val result = useCase.submitOrder("cart-1", "token-1")
        assertThat(result).isInstanceOf(AppResult.SystemError::class.java)
    }
}
