package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ExportUseCaseTest {
    private lateinit var operationsRepository: OperationsRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: ExportUseCase

    @Before
    fun setUp() {
        operationsRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = ExportUseCase(operationsRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `requires EXPORT_MANAGE permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.EXPORT_MANAGE) } returns false
        val result = useCase.createExport("orders", "csv", "data", 10)
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `rejects unsupported format`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.EXPORT_MANAGE) } returns true
        val result = useCase.createExport("orders", "xml", "data", 10)
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `succeeds with csv format`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.EXPORT_MANAGE) } returns true
        sessionManager.createSession("finance-1")
        coEvery { operationsRepository.createExportJob(any()) } answers { firstArg() }
        val result = useCase.createExport("orders", "csv", "col1,col2\nv1,v2", 1)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `succeeds with json format`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.EXPORT_MANAGE) } returns true
        sessionManager.createSession("finance-1")
        coEvery { operationsRepository.createExportJob(any()) } answers { firstArg() }
        val result = useCase.createExport("orders", "json", """[{"id":"1"}]""", 1)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }
}
