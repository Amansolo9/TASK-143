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

class DiscrepancyNotesEnforcementTest {
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
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns true

        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } coAnswers {
            val block = firstArg<suspend () -> Any>()
            block()
        }

        useCase = ReconciliationUseCase(
            operationsRepository, paymentRepository, auditRepository,
            checkPermission, sessionManager, transactionRunner
        )
    }

    @Test
    fun `AMOUNT_MISMATCH discrepancy includes contextual notes`() = runTest {
        val row = SettlementImportRow(
            id = "row-1", batchId = "batch-1", externalRowId = "ext-1",
            paymentReference = "pay-1", amount = BigDecimal("150.00"),
            tenderType = "CASH", status = "CLEARED", transactionDate = Instant.now(),
            rawData = "{}", isValid = true, validationErrors = null, isDuplicate = false
        )
        val payment = PaymentRecord(
            id = "pay-1", orderId = "order-1", amount = BigDecimal("100.00"),
            tenderType = TenderType.CASH, status = PaymentStatus.ALLOCATED,
            externalReference = null, receivedBy = "clerk", receivedAt = Instant.now(),
            notes = null, createdAt = Instant.now(), version = 1
        )

        coEvery { operationsRepository.getSettlementBatchById("batch-1") } returns
            SettlementImportBatch("batch-1", "job-1", "BATCH-001", true, 1, Instant.now())
        coEvery { operationsRepository.getSettlementRowsByBatch("batch-1") } returns listOf(row)
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false
        coEvery { operationsRepository.getImportJobById(any()) } returns null

        val discrepancySlot = slot<DiscrepancyCase>()
        coEvery { operationsRepository.createDiscrepancyCase(capture(discrepancySlot)) } returns mockk()

        useCase.runReconciliation("batch-1")

        assertThat(discrepancySlot.isCaptured).isTrue()
        val notes = discrepancySlot.captured.notes
        assertThat(notes).contains("ext-1")
        assertThat(notes).contains("pay-1")
        assertThat(notes).contains("150.00")
        assertThat(notes).contains("100.00")
        assertThat(notes).isNotEmpty()
    }

    @Test
    fun `UNMATCHED discrepancy includes contextual notes`() = runTest {
        val row = SettlementImportRow(
            id = "row-1", batchId = "batch-1", externalRowId = "ext-1",
            paymentReference = "pay-999", amount = BigDecimal("100.00"),
            tenderType = "CHECK", status = "CLEARED", transactionDate = Instant.now(),
            rawData = "{}", isValid = true, validationErrors = null, isDuplicate = false
        )

        coEvery { operationsRepository.getSettlementBatchById("batch-1") } returns
            SettlementImportBatch("batch-1", "job-1", "BATCH-001", true, 1, Instant.now())
        coEvery { operationsRepository.getSettlementRowsByBatch("batch-1") } returns listOf(row)
        coEvery { paymentRepository.getPaymentById("pay-999") } returns null
        coEvery { paymentRepository.getPaymentsForOrder("pay-999") } returns emptyList()
        coEvery { operationsRepository.getImportJobById(any()) } returns null

        val discrepancySlot = slot<DiscrepancyCase>()
        coEvery { operationsRepository.createDiscrepancyCase(capture(discrepancySlot)) } returns mockk()

        useCase.runReconciliation("batch-1")

        assertThat(discrepancySlot.isCaptured).isTrue()
        val notes = discrepancySlot.captured.notes
        assertThat(notes).contains("ext-1")
        assertThat(notes).contains("pay-999")
        assertThat(notes).contains("100.00")
        assertThat(notes).contains("CHECK")
        assertThat(notes).isNotEmpty()
    }

    @Test
    fun `NO_REFERENCE discrepancy includes row external_id`() = runTest {
        val row = SettlementImportRow(
            id = "row-1", batchId = "batch-1", externalRowId = "ext-1",
            paymentReference = null, amount = BigDecimal("100.00"),
            tenderType = "CASH", status = "CLEARED", transactionDate = Instant.now(),
            rawData = "{}", isValid = true, validationErrors = null, isDuplicate = false
        )

        coEvery { operationsRepository.getSettlementBatchById("batch-1") } returns
            SettlementImportBatch("batch-1", "job-1", "BATCH-001", true, 1, Instant.now())
        coEvery { operationsRepository.getSettlementRowsByBatch("batch-1") } returns listOf(row)
        coEvery { operationsRepository.getImportJobById(any()) } returns null

        val discrepancySlot = slot<DiscrepancyCase>()
        coEvery { operationsRepository.createDiscrepancyCase(capture(discrepancySlot)) } returns mockk()

        useCase.runReconciliation("batch-1")

        assertThat(discrepancySlot.isCaptured).isTrue()
        assertThat(discrepancySlot.captured.notes).contains("ext-1")
        assertThat(discrepancySlot.captured.notes).isNotEmpty()
    }

    @Test
    fun `resolve discrepancy requires non-blank notes`() = runTest {
        val result = useCase.resolveDiscrepancy("case-1", "")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `resolve discrepancy requires non-blank notes even with whitespace`() = runTest {
        val result = useCase.resolveDiscrepancy("case-1", "   ")
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }
}
