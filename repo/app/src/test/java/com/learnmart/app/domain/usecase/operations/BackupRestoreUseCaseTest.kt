package com.learnmart.app.domain.usecase.operations

import android.content.Context
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

class BackupRestoreUseCaseTest {
    private lateinit var context: Context
    private lateinit var operationsRepository: OperationsRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var checkPermission: CheckPermissionUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var useCase: BackupRestoreUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        operationsRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        checkPermission = mockk()
        sessionManager = SessionManager(mockk<SessionDao>(relaxed = true))
        useCase = BackupRestoreUseCase(context, operationsRepository, policyRepository, auditRepository, checkPermission, sessionManager)
    }

    @Test
    fun `createBackup requires BACKUP_RUN permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.BACKUP_RUN) } returns false
        val result = useCase.createBackup()
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `createBackup without passphrase returns validation error`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.BACKUP_RUN) } returns true
        coEvery { policyRepository.getPolicyValue(PolicyType.BACKUP, "backup_passphrase", "") } returns ""

        val dbFile = File.createTempFile("testdb", ".db")
        dbFile.writeText("fake db content")
        dbFile.deleteOnExit()
        every { context.getDatabasePath(any()) } returns dbFile
        every { context.filesDir } returns dbFile.parentFile

        val result = useCase.createBackup()
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
        val error = result as AppResult.ValidationError
        assertThat(error.globalErrors.first()).contains("passphrase not configured")
    }

    @Test
    fun `createBackup logs BACKUP_STARTED audit event even when passphrase missing`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.BACKUP_RUN) } returns true
        coEvery { policyRepository.getPolicyValue(PolicyType.BACKUP, "backup_passphrase", "") } returns ""

        val dbFile = File.createTempFile("testdb", ".db")
        dbFile.writeText("fake db content")
        dbFile.deleteOnExit()
        every { context.getDatabasePath(any()) } returns dbFile
        every { context.filesDir } returns dbFile.parentFile

        useCase.createBackup()
        coVerify { auditRepository.logEvent(match { it.actionType == AuditActionType.BACKUP_STARTED }) }
    }

    @Test
    fun `createBackup fails when database file missing`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.BACKUP_RUN) } returns true
        coEvery { policyRepository.getPolicyValue(PolicyType.BACKUP, "backup_passphrase", "") } returns "secret123"

        val fakePath = File("/nonexistent/path/db.db")
        every { context.getDatabasePath(any()) } returns fakePath
        every { context.filesDir } returns File(System.getProperty("java.io.tmpdir"))

        val result = useCase.createBackup()
        assertThat(result).isNotInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `restoreFromStream requires RESTORE_RUN permission`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.RESTORE_RUN) } returns false
        val result = useCase.restoreFromStream(mockk())
        assertThat(result).isInstanceOf(AppResult.PermissionError::class.java)
    }

    @Test
    fun `restoreFromStream rejects empty archive`() = runTest {
        coEvery { checkPermission.hasPermission(Permission.RESTORE_RUN) } returns true

        val cacheDir = File(System.getProperty("java.io.tmpdir"), "test_cache_${System.nanoTime()}")
        cacheDir.mkdirs()
        cacheDir.deleteOnExit()
        every { context.cacheDir } returns cacheDir

        val emptyStream = "".byteInputStream()
        val result = useCase.restoreFromStream(emptyStream)
        assertThat(result).isInstanceOf(AppResult.ValidationError::class.java)
    }

    @Test
    fun `backup encryption algorithms are available`() {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        assertThat(factory).isNotNull()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        assertThat(cipher).isNotNull()
    }
}
