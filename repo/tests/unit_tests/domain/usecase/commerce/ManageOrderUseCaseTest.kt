package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.*
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class ManageOrderUseCaseTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ManageOrderUseCase

    private fun order(userId: String = "user-1", status: OrderStatus = OrderStatus.PLACED_UNPAID) = Order(
        "o1", userId, status, null, BigDecimal("50"), BigDecimal.ZERO,
        BigDecimal("4"), BigDecimal("1.5"), BigDecimal("1.5"), BigDecimal("57"),
        BigDecimal.ZERO, Instant.now(), null, null, Instant.now(), Instant.now(), 1
    )

    @Before
    fun setUp() {
        orderRepository = mockk(relaxed = true)
        paymentRepository = mockk(relaxed = true)
        inventoryRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ManageOrderUseCase(orderRepository, paymentRepository, inventoryRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `getOrderById missing returns not found`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getOrderById("missing") } returns null
        val result = useCase.getOrderById("missing")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `getOrderById owner can access own order`() = runTest {
        sessionManager.createSession("user-1")
        coEvery { orderRepository.getOrderById("o1") } returns order("user-1")
        val result = useCase.getOrderById("o1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `getOrderById non-owner without permission denied`() = runTest {
        sessionManager.createSession("other-user")
        coEvery { orderRepository.getOrderById("o1") } returns order("user-1")
        coEvery { checkPermission.hasAnyPermission(*anyVararg()) } returns false
        val result = useCase.getOrderById("o1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `getOrderById finance clerk can access any order`() = runTest {
        sessionManager.createSession("clerk-1")
        coEvery { orderRepository.getOrderById("o1") } returns order("user-1")
        coEvery { checkPermission.hasAnyPermission(*anyVararg()) } returns true
        val result = useCase.getOrderById("o1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }
}
