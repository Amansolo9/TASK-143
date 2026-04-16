package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.data.local.TransactionRunner
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.domain.repository.PaymentRepository
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

class RecordPaymentUseCaseTest {
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: RecordPaymentUseCase

    @Before
    fun setUp() {
        paymentRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        transactionRunner = mockk()
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } coAnswers {
            firstArg<suspend () -> Any>().invoke()
        }
        useCase = RecordPaymentUseCase(paymentRepository, orderRepository, auditRepository, checkPermission, sessionManager, transactionRunner)
    }

    @Test
    fun `requires PAYMENT_RECORD permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECORD) } returns false
        val result = useCase(RecordPaymentRequest("o1", BigDecimal("10"), TenderType.CASH, null, null))
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `negative amount rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECORD) } returns true
        val result = useCase(RecordPaymentRequest("o1", BigDecimal("-5"), TenderType.CASH, null, null))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `card terminal requires external reference`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECORD) } returns true
        val result = useCase(RecordPaymentRequest("o1", BigDecimal("50"), TenderType.EXTERNAL_CARD_TERMINAL_REFERENCE, null, null))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `cancelled order rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECORD) } returns true
        coEvery { orderRepository.getOrderById("o1") } returns Order(
            "o1", "u1", OrderStatus.AUTO_CANCELLED, null, BigDecimal("50"), BigDecimal.ZERO,
            BigDecimal("4"), BigDecimal("1.5"), BigDecimal("1.5"), BigDecimal("57"),
            BigDecimal.ZERO, Instant.now(), null, null, Instant.now(), Instant.now(), 1
        )
        val result = useCase(RecordPaymentRequest("o1", BigDecimal("57"), TenderType.CASH, null, null))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `successful payment recording`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECORD) } returns true
        sessionManager.createSession("clerk-1")
        coEvery { orderRepository.getOrderById("o1") } returns Order(
            "o1", "u1", OrderStatus.PLACED_UNPAID, null, BigDecimal("50"), BigDecimal.ZERO,
            BigDecimal("4"), BigDecimal("1.5"), BigDecimal("1.5"), BigDecimal("57"),
            BigDecimal.ZERO, Instant.now(), null, null, Instant.now(), Instant.now(), 1
        )
        coEvery { paymentRepository.getTotalAllocatedForOrder("o1") } returns BigDecimal("57.00")
        val result = useCase(RecordPaymentRequest("o1", BigDecimal("57"), TenderType.CASH, null, null))
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `transaction failure returns system error`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECORD) } returns true
        sessionManager.createSession("clerk-1")
        coEvery { orderRepository.getOrderById("o1") } returns Order(
            "o1", "u1", OrderStatus.PLACED_UNPAID, null, BigDecimal("50"), BigDecimal.ZERO,
            BigDecimal("4"), BigDecimal("1.5"), BigDecimal("1.5"), BigDecimal("57"),
            BigDecimal.ZERO, Instant.now(), null, null, Instant.now(), Instant.now(), 1
        )
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } throws RuntimeException("fail")
        val result = useCase(RecordPaymentRequest("o1", BigDecimal("57"), TenderType.CASH, null, null))
        assertThat(result).isInstanceOf(AppResult.SystemError::class.java)
    }
}
