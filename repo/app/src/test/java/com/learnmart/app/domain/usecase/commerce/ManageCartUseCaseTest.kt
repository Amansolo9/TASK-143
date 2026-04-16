package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.CartRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ManageCartUseCaseTest {
    private lateinit var cartRepository: CartRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var pricingEngine: PricingEngine
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageCartUseCase

    @Before
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        pricingEngine = mockk(relaxed = true)
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageCartUseCase(cartRepository, courseRepository, pricingEngine, sessionManager)
    }

    @Test
    fun `getOrCreateCart requires authentication`() = runTest {
        val result = useCase.getOrCreateCart()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getOrCreateCart returns existing cart`() = runTest {
        sessionManager.createSession("user-1")
        val cart = Cart("c1", "user-1", CartStatus.ACTIVE, CheckoutPolicy.CROSS_CLASS_ALLOWED, Instant.now(), Instant.now())
        coEvery { cartRepository.getActiveCartForUser("user-1") } returns cart
        val result = useCase.getOrCreateCart()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data.id).isEqualTo("c1")
    }

    @Test
    fun `getOrCreateCart creates new cart when none exists`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { cartRepository.getActiveCartForUser("user-1") } returns null
        coEvery { pricingEngine.getCheckoutPolicy() } returns CheckoutPolicy.SAME_CLASS_ONLY
        val result = useCase.getOrCreateCart()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        coVerify { cartRepository.createCart(any()) }
    }

    @Test
    fun `addItem requires authentication`() = runTest {
        val result = useCase.addItem(AddToCartRequest(LineItemType.COURSE_FEE, "ref", null, "Item", 1, BigDecimal("50")))
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `addItem rejects zero quantity`() = runTest {
        sessionManager.createSession("user-1")
        val result = useCase.addItem(AddToCartRequest(LineItemType.COURSE_FEE, "ref", null, "Item", 0, BigDecimal("50")))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `addItem rejects course fee with quantity greater than 1`() = runTest {
        sessionManager.createSession("user-1")
        val result = useCase.addItem(AddToCartRequest(LineItemType.COURSE_FEE, "ref", null, "Item", 2, BigDecimal("50")))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }
}
