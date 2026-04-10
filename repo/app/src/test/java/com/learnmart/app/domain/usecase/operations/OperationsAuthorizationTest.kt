package com.learnmart.app.domain.usecase.operations

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class OperationsAuthorizationTest {

    private lateinit var operationsRepository: OperationsRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var useCase: ManageOperationsUseCase

    @Before
    fun setUp() {
        operationsRepository = mockk(relaxed = true)
        checkPermission = mockk()
        useCase = ManageOperationsUseCase(operationsRepository, checkPermission)
    }

    @Test
    fun `learner cannot access import jobs`() = runTest {
        coEvery { checkPermission.hasAnyPermission(Permission.IMPORT_MANAGE, Permission.AUDIT_VIEW) } returns false
        val result = useCase.getImportJobs()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `finance clerk can access import jobs`() = runTest {
        coEvery { checkPermission.hasAnyPermission(Permission.IMPORT_MANAGE, Permission.AUDIT_VIEW) } returns true
        coEvery { operationsRepository.getAllImportJobs() } returns emptyList()
        val result = useCase.getImportJobs()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `learner cannot access discrepancy cases`() = runTest {
        coEvery { checkPermission.hasAnyPermission(Permission.PAYMENT_RECONCILE, Permission.AUDIT_VIEW) } returns false
        val result = useCase.getDiscrepancyCases()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `learner cannot access backup archives`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.BACKUP_RUN) } returns false
        val result = useCase.getBackupArchives()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `admin can access backup archives`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.BACKUP_RUN) } returns true
        coEvery { operationsRepository.getAllBackupArchives() } returns emptyList()
        val result = useCase.getBackupArchives()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `learner cannot access export jobs`() = runTest {
        coEvery { checkPermission.hasAnyPermission(Permission.EXPORT_MANAGE, Permission.AUDIT_VIEW) } returns false
        val result = useCase.getExportJobs()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `admin can access all operations data`() = runTest {
        coEvery { checkPermission.hasAnyPermission(*anyVararg()) } returns true
        coEvery { checkPermission.hasPermission(any()) } returns true
        coEvery { operationsRepository.getAllImportJobs() } returns emptyList()
        coEvery { operationsRepository.getAllDiscrepancyCases(any(), any()) } returns emptyList()
        coEvery { operationsRepository.getAllBackupArchives() } returns emptyList()
        coEvery { operationsRepository.getAllExportJobs(any(), any()) } returns emptyList()

        assertThat(useCase.getImportJobs()).isInstanceOf(AppResult.Success::class.java)
        assertThat(useCase.getDiscrepancyCases()).isInstanceOf(AppResult.Success::class.java)
        assertThat(useCase.getBackupArchives()).isInstanceOf(AppResult.Success::class.java)
        assertThat(useCase.getExportJobs()).isInstanceOf(AppResult.Success::class.java)
    }
}
