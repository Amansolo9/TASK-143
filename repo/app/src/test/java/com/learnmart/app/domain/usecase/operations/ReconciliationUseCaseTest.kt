package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.data.local.TransactionRunner
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
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

class ReconciliationUseCaseTest {
    private lateinit var operationsRepository: OperationsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: ReconciliationUseCase

    @Before
    fun setUp() {
        operationsRepository = mockk(relaxed = true)
        paymentRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        transactionRunner = mockk()
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } coAnswers {
            firstArg<suspend () -> Any>().invoke()
        }
        useCase = ReconciliationUseCase(operationsRepository, paymentRepository, auditRepository, checkPermission, sessionManager, transactionRunner)
    }

    @Test
    fun `requires PAYMENT_RECONCILE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns false
        val result = useCase.runReconciliation("batch-1")
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `missing batch returns not found`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns true
        coEvery { operationsRepository.getSettlementBatchById("missing") } returns null
        val result = useCase.runReconciliation("missing")
        assertThat(result).isInstanceOf(AppResult.NotFoundError::class.java)
    }

    @Test
    fun `empty valid rows returns validation error`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns true
        coEvery { operationsRepository.getSettlementBatchById("b1") } returns SettlementImportBatch("b1", "j1", "B-001", true, 0, Instant.now())
        coEvery { operationsRepository.getSettlementRowsByBatch("b1") } returns emptyList()
        val result = useCase.runReconciliation("b1")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `successful reconciliation returns run data`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns true
        coEvery { operationsRepository.getSettlementBatchById("b1") } returns SettlementImportBatch("b1", "j1", "B-001", true, 1, Instant.now())
        val row = SettlementImportRow("r1", "b1", "ext-1", "pay-1", BigDecimal("100"), "CASH", "CLEARED", Instant.now(), "{}", true, null, false)
        coEvery { operationsRepository.getSettlementRowsByBatch("b1") } returns listOf(row)
        coEvery { paymentRepository.getPaymentById("pay-1") } returns PaymentRecord("pay-1", "o1", BigDecimal("100"), TenderType.CASH, PaymentStatus.ALLOCATED, null, "c", Instant.now(), null, Instant.now(), 1)
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false
        coEvery { operationsRepository.getImportJobById(any()) } returns null

        val result = useCase.runReconciliation("b1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data.matchedCount).isEqualTo(1)
    }

    @Test
    fun `resolve discrepancy requires non-blank note`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns true
        val result = useCase.resolveDiscrepancy("case-1", "")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `transaction failure returns system error`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns true
        coEvery { operationsRepository.getSettlementBatchById("b1") } returns SettlementImportBatch("b1", "j1", "B-001", true, 1, Instant.now())
        val row = SettlementImportRow("r1", "b1", "ext-1", "pay-1", BigDecimal("100"), "CASH", null, Instant.now(), "{}", true, null, false)
        coEvery { operationsRepository.getSettlementRowsByBatch("b1") } returns listOf(row)
        coEvery { paymentRepository.getPaymentById("pay-1") } returns null
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } throws RuntimeException("DB fail")

        val result = useCase.runReconciliation("b1")
        assertThat(result).isInstanceOf(AppResult.SystemError::class.java)
    }
}
