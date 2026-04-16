package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.security.SettlementSignatureVerifier
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ImportSettlementUseCaseTest {
    private lateinit var operationsRepository: OperationsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var signatureVerifier: SettlementSignatureVerifier
    private lateinit var useCase: ImportSettlementUseCase

    @Before
    fun setUp() {
        operationsRepository = mockk(relaxed = true)
        paymentRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        signatureVerifier = mockk(relaxed = true)
        coEvery { policyRepository.getPolicyLongValue(any(), any(), any()) } returns 26214400L
        coEvery { policyRepository.getPolicyBoolValue(any(), any(), any()) } returns false
        useCase = ImportSettlementUseCase(operationsRepository, paymentRepository, policyRepository, auditRepository, checkPermission, sessionManager, signatureVerifier)
    }

    @Test
    fun `requires IMPORT_MANAGE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.IMPORT_MANAGE) } returns false
        val result = useCase.importFile("f.csv", "csv", 100, emptyList())
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `rejects oversized file`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.IMPORT_MANAGE) } returns true
        val result = useCase.importFile("f.csv", "csv", 999999999, emptyList())
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `rejects unsupported file type`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.IMPORT_MANAGE) } returns true
        val result = useCase.importFile("f.xml", "xml", 100, emptyList())
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `all-invalid rows rejects import`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.IMPORT_MANAGE) } returns true
        sessionManager.createSession("finance-1")
        val rows = listOf(mapOf("amount" to "not_a_number"))
        val result = useCase.importFile("f.csv", "csv", 100, rows)
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `valid rows produce READY_TO_APPLY status`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.IMPORT_MANAGE) } returns true
        sessionManager.createSession("finance-1")
        coEvery { operationsRepository.getSettlementRowByExternalId(any()) } returns null
        coEvery { operationsRepository.createImportJob(any()) } answers { firstArg() }
        val rows = listOf(mapOf(
            "external_id" to "ext-1", "amount" to "100.00",
            "payment_reference" to "pay-1", "tender_type" to "CASH",
            "transaction_date" to "2025-01-15T10:00:00Z", "status" to "CLEARED"
        ))
        val result = useCase.importFile("f.csv", "csv", 100, rows)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).data.status).isEqualTo(ImportJobStatus.READY_TO_APPLY)
    }
}
