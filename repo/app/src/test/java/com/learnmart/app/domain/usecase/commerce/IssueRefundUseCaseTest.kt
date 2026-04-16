package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.data.local.TransactionRunner
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.repository.PolicyRepository
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

class IssueRefundUseCaseTest {
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: IssueRefundUseCase

    @Before
    fun setUp() {
        paymentRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        transactionRunner = mockk()
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } coAnswers {
            firstArg<suspend () -> Any>().invoke()
        }
        coEvery { policyRepository.getPolicyIntValue(any(), any(), any()) } returns 3
        coEvery { paymentRepository.countRefundsForLearnerToday(any(), any(), any()) } returns 0
        useCase = IssueRefundUseCase(paymentRepository, orderRepository, policyRepository, auditRepository, checkPermission, sessionManager, transactionRunner)
    }

    @Test
    fun `requires REFUND_ISSUE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.REFUND_ISSUE) } returns false
        val result = useCase(IssueRefundRequest("o1", "p1", BigDecimal("10"), "reason"))
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `amount below minimum rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.REFUND_ISSUE) } returns true
        val result = useCase(IssueRefundRequest("o1", "p1", BigDecimal("0.001"), "reason"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `blank reason rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.REFUND_ISSUE) } returns true
        val result = useCase(IssueRefundRequest("o1", "p1", BigDecimal("10"), ""))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `payment not belonging to order rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.REFUND_ISSUE) } returns true
        coEvery { orderRepository.getOrderById("o1") } returns mockk(relaxed = true) { every { userId } returns "u1"; every { paidAmount } returns BigDecimal("57") }
        coEvery { paymentRepository.getPaymentById("p1") } returns PaymentRecord(
            "p1", "other-order", BigDecimal("57"), TenderType.CASH, PaymentStatus.ALLOCATED,
            null, "clerk", Instant.now(), null, Instant.now(), 1
        )
        val result = useCase(IssueRefundRequest("o1", "p1", BigDecimal("10"), "reason"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `exceeds refundable balance rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.REFUND_ISSUE) } returns true
        coEvery { orderRepository.getOrderById("o1") } returns Order(
            "o1", "u1", OrderStatus.PAID, null, BigDecimal("50"), BigDecimal.ZERO,
            BigDecimal("4"), BigDecimal("1.5"), BigDecimal("1.5"), BigDecimal("57"),
            BigDecimal("57"), Instant.now(), null, null, Instant.now(), Instant.now(), 1
        )
        coEvery { paymentRepository.getPaymentById("p1") } returns PaymentRecord(
            "p1", "o1", BigDecimal("57"), TenderType.CASH, PaymentStatus.ALLOCATED,
            null, "clerk", Instant.now(), null, Instant.now(), 1
        )
        coEvery { paymentRepository.getRefundsForOrder("o1") } returns listOf(
            RefundRecord("r1", "o1", "p1", "u1", BigDecimal("50"), "prev", TenderType.CASH, null, "clerk", Instant.now(), false, null, Instant.now())
        )
        val result = useCase(IssueRefundRequest("o1", "p1", BigDecimal("10"), "reason"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `daily limit exceeded without override rejected`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.REFUND_ISSUE) } returns true
        coEvery { checkPermission.hasPermission(Permission.REFUND_OVERRIDE_LIMIT) } returns false
        coEvery { orderRepository.getOrderById("o1") } returns Order(
            "o1", "u1", OrderStatus.PAID, null, BigDecimal("50"), BigDecimal.ZERO,
            BigDecimal("4"), BigDecimal("1.5"), BigDecimal("1.5"), BigDecimal("57"),
            BigDecimal("57"), Instant.now(), null, null, Instant.now(), Instant.now(), 1
        )
        coEvery { paymentRepository.getPaymentById("p1") } returns PaymentRecord(
            "p1", "o1", BigDecimal("57"), TenderType.CASH, PaymentStatus.ALLOCATED,
            null, "clerk", Instant.now(), null, Instant.now(), 1
        )
        coEvery { paymentRepository.getRefundsForOrder("o1") } returns emptyList()
        coEvery { paymentRepository.countRefundsForLearnerToday(any(), any(), any()) } returns 3
        val result = useCase(IssueRefundRequest("o1", "p1", BigDecimal("10"), "reason"))
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }
}
