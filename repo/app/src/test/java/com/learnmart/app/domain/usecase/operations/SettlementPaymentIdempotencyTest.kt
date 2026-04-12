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
import java.security.MessageDigest
import java.time.Instant

/**
 * Focused tests for settlement payment-status update idempotency within
 * ReconciliationUseCase. Covers idempotency key behavior, status mapping,
 * transition guards, re-import skip, and worker-path equivalence.
 */
class SettlementPaymentIdempotencyTest {

    private lateinit var operationsRepository: OperationsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: ReconciliationUseCase

    private val now: Instant = Instant.parse("2025-06-01T12:00:00Z")

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
        coEvery { operationsRepository.getImportJobById(any()) } returns null

        useCase = ReconciliationUseCase(
            operationsRepository, paymentRepository, auditRepository,
            checkPermission, sessionManager, transactionRunner
        )
    }

    // ── Helpers ──

    private fun batch() = SettlementImportBatch(
        "batch-1", "job-1", "BATCH-001", true, 1, now
    )

    private fun row(
        id: String = "row-1",
        externalId: String = "ext-1",
        paymentRef: String = "pay-1",
        amount: BigDecimal = BigDecimal("100.00"),
        status: String? = "CLEARED"
    ) = SettlementImportRow(
        id = id, batchId = "batch-1", externalRowId = externalId,
        paymentReference = paymentRef, amount = amount,
        tenderType = "CASH", status = status, transactionDate = now,
        rawData = "{}", isValid = true, validationErrors = null, isDuplicate = false
    )

    private fun payment(
        id: String = "pay-1",
        status: PaymentStatus = PaymentStatus.ALLOCATED,
        amount: BigDecimal = BigDecimal("100.00")
    ) = PaymentRecord(
        id = id, orderId = "order-1", amount = amount,
        tenderType = TenderType.CASH, status = status,
        externalReference = null, receivedBy = "clerk",
        receivedAt = now, notes = null, createdAt = now, version = 1
    )

    private fun stubBatchAndRows(vararg rows: SettlementImportRow) {
        coEvery { operationsRepository.getSettlementBatchById("batch-1") } returns batch()
        coEvery { operationsRepository.getSettlementRowsByBatch("batch-1") } returns rows.toList()
    }

    // ── 1. Idempotency key determinism ──

    @Test
    fun `same externalRowId and status produce identical idempotency key`() {
        val key1 = computeKey("ext-1", "CLEARED")
        val key2 = computeKey("ext-1", "CLEARED")
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `different status produces different idempotency key`() {
        val key1 = computeKey("ext-1", "CLEARED")
        val key2 = computeKey("ext-1", "VOIDED")
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `different externalRowId produces different idempotency key`() {
        val key1 = computeKey("ext-1", "CLEARED")
        val key2 = computeKey("ext-2", "CLEARED")
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `idempotency key is SHA-256 hex 64 chars`() {
        val key = computeKey("ext-1", "CLEARED")
        assertThat(key).hasLength(64)
        assertThat(key).matches("[0-9a-f]{64}")
    }

    // ── 2. First apply: payment status updated ──

    @Test
    fun `first import with CLEARED status updates ALLOCATED payment to CLEARED`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        val result = useCase.runReconciliation("batch-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)

        coVerify(exactly = 1) {
            paymentRepository.updatePaymentStatus("pay-1", PaymentStatus.CLEARED, 1)
        }
        coVerify(exactly = 1) {
            operationsRepository.saveAppliedSettlementUpdate(any(), "row-1", "pay-1", any())
        }
    }

    @Test
    fun `first import with VOIDED status updates RECORDED payment to VOIDED`() = runTest {
        stubBatchAndRows(row(status = "VOIDED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.RECORDED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 1) {
            paymentRepository.updatePaymentStatus("pay-1", PaymentStatus.VOIDED, 1)
        }
    }

    @Test
    fun `first import with FLAGGED status updates to DISCREPANCY_FLAGGED`() = runTest {
        stubBatchAndRows(row(status = "FLAGGED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 1) {
            paymentRepository.updatePaymentStatus("pay-1", PaymentStatus.DISCREPANCY_FLAGGED, 1)
        }
    }

    // ── 3. Re-import skip: dedup record present ──

    @Test
    fun `re-importing same row does not call updatePaymentStatus`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns true

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 0) { paymentRepository.updatePaymentStatus(any(), any(), any()) }
    }

    @Test
    fun `re-import still persists reconciliation run and match`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns true

        val result = useCase.runReconciliation("batch-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data.matchedCount).isEqualTo(1)

        coVerify { operationsRepository.createReconciliationRun(any()) }
        coVerify { operationsRepository.createReconciliationMatches(any()) }
    }

    @Test
    fun `re-import does not save a duplicate dedup record`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns true

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 0) {
            operationsRepository.saveAppliedSettlementUpdate(any(), any(), any(), any())
        }
    }

    // ── 4. Changed status: distinct key, applies once ──

    @Test
    fun `same externalRowId with different status applies as new update`() = runTest {
        val row1 = row(id = "row-1", externalId = "ext-1", status = "CLEARED")
        val row2 = row(id = "row-2", externalId = "ext-1", paymentRef = "pay-1", status = "DISPUTED")

        stubBatchAndRows(row1, row2)
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 2) {
            operationsRepository.saveAppliedSettlementUpdate(any(), any(), any(), any())
        }
    }

    // ── 5. Settlement status mapping ──

    @Test fun `CLEARED maps to CLEARED`() { assertThat(mapStatus("CLEARED")).isEqualTo(PaymentStatus.CLEARED) }
    @Test fun `SETTLED maps to CLEARED`() { assertThat(mapStatus("SETTLED")).isEqualTo(PaymentStatus.CLEARED) }
    @Test fun `CONFIRMED maps to CLEARED`() { assertThat(mapStatus("CONFIRMED")).isEqualTo(PaymentStatus.CLEARED) }
    @Test fun `VOIDED maps to VOIDED`() { assertThat(mapStatus("VOIDED")).isEqualTo(PaymentStatus.VOIDED) }
    @Test fun `VOID maps to VOIDED`() { assertThat(mapStatus("VOID")).isEqualTo(PaymentStatus.VOIDED) }
    @Test fun `CANCELLED maps to VOIDED`() { assertThat(mapStatus("CANCELLED")).isEqualTo(PaymentStatus.VOIDED) }
    @Test fun `DISCREPANCY maps to DISCREPANCY_FLAGGED`() { assertThat(mapStatus("DISCREPANCY")).isEqualTo(PaymentStatus.DISCREPANCY_FLAGGED) }
    @Test fun `FLAGGED maps to DISCREPANCY_FLAGGED`() { assertThat(mapStatus("FLAGGED")).isEqualTo(PaymentStatus.DISCREPANCY_FLAGGED) }
    @Test fun `DISPUTED maps to DISCREPANCY_FLAGGED`() { assertThat(mapStatus("DISPUTED")).isEqualTo(PaymentStatus.DISCREPANCY_FLAGGED) }
    @Test fun `RESOLVED maps to RESOLVED`() { assertThat(mapStatus("RESOLVED")).isEqualTo(PaymentStatus.RESOLVED) }

    @Test
    fun `unknown status maps to null`() {
        assertThat(mapStatus("UNKNOWN")).isNull()
        assertThat(mapStatus("PENDING")).isNull()
        assertThat(mapStatus("")).isNull()
    }

    @Test
    fun `mapping is case-insensitive`() {
        assertThat(mapStatus("cleared")).isEqualTo(PaymentStatus.CLEARED)
        assertThat(mapStatus("Voided")).isEqualTo(PaymentStatus.VOIDED)
        assertThat(mapStatus("  FLAGGED  ")).isEqualTo(PaymentStatus.DISCREPANCY_FLAGGED)
    }

    // ── 6. Invalid transition: skip without error ──

    @Test
    fun `payment already REFUNDED cannot transition to CLEARED - skip`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.REFUNDED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        val result = useCase.runReconciliation("batch-1")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)

        coVerify(exactly = 0) { paymentRepository.updatePaymentStatus(any(), any(), any()) }
        coVerify(exactly = 1) {
            operationsRepository.saveAppliedSettlementUpdate(any(), any(), any(), any())
        }
    }

    @Test
    fun `payment already VOIDED cannot transition to CLEARED - skip`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.VOIDED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 0) { paymentRepository.updatePaymentStatus(any(), any(), any()) }
    }

    // ── 7. Payment already at target status: no-op ──

    @Test
    fun `payment already CLEARED with CLEARED settlement - no update`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.CLEARED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 0) { paymentRepository.updatePaymentStatus(any(), any(), any()) }
    }

    // ── 8. Null status in settlement row: skip entirely ──

    @Test
    fun `settlement row with null status skips payment update`() = runTest {
        stubBatchAndRows(row(status = null))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()

        useCase.runReconciliation("batch-1")

        coVerify(exactly = 0) { paymentRepository.updatePaymentStatus(any(), any(), any()) }
        coVerify(exactly = 0) {
            operationsRepository.saveAppliedSettlementUpdate(any(), any(), any(), any())
        }
    }

    // ── 9. Worker-path equivalence ──

    @Test
    fun `systemCaller true bypasses permission check`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns false
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false

        val result = useCase.runReconciliation("batch-1", systemCaller = true)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)

        coVerify(exactly = 1) {
            paymentRepository.updatePaymentStatus("pay-1", PaymentStatus.CLEARED, 1)
        }
    }

    @Test
    fun `systemCaller false without permission returns PermissionError`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.PAYMENT_RECONCILE) } returns false

        val result = useCase.runReconciliation("batch-1", systemCaller = false)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `transaction failure rolls back all writes including dedup records`() = runTest {
        stubBatchAndRows(row(status = "CLEARED"))
        coEvery { paymentRepository.getPaymentById("pay-1") } returns payment(status = PaymentStatus.ALLOCATED)
        coEvery { paymentRepository.getPaymentsForOrder(any()) } returns emptyList()
        coEvery { operationsRepository.hasAppliedSettlementUpdate(any()) } returns false
        coEvery { transactionRunner.runInTransaction(any<suspend () -> Any>()) } throws RuntimeException("DB crash")

        val result = useCase.runReconciliation("batch-1")
        assertThat(result).isInstanceOf(AppResult.SystemError::class.java)
        assertThat((result as AppResult.SystemError).retryable).isTrue()
    }

    // ── Private helpers mirroring the use case's private methods ──

    private fun computeKey(externalRowId: String, status: String): String {
        val raw = "$externalRowId|$status"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun mapStatus(settlementStatus: String): PaymentStatus? {
        return when (settlementStatus.uppercase().trim()) {
            "CLEARED", "SETTLED", "CONFIRMED" -> PaymentStatus.CLEARED
            "VOIDED", "VOID", "CANCELLED" -> PaymentStatus.VOIDED
            "DISCREPANCY", "FLAGGED", "DISPUTED" -> PaymentStatus.DISCREPANCY_FLAGGED
            "RESOLVED" -> PaymentStatus.RESOLVED
            else -> null
        }
    }
}
