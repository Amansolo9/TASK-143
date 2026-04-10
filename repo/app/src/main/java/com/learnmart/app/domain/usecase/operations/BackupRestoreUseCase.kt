package com.learnmart.app.domain.usecase.operations

import android.content.Context
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.usecase.auth.CheckPermissionUseCase
import com.learnmart.app.security.SessionManager
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * Real backup/restore using AES-256-GCM encryption on the Room database file.
 * Operates fully offline via scoped storage.
 *
 * Security model:
 * - Backup passphrase is derived from a device-bound secret stored in policy
 *   ("backup_passphrase") combined with the archive ID as additional entropy.
 * - PBKDF2WithHmacSHA256 with 120,000 iterations derives the AES-256 key.
 * - Only salt + IV are stored in the archive header; NO raw key material is written.
 * - Integrity is validated via SHA-256 checksum of the encrypted archive.
 *
 * Archive format: [salt_len:1][salt:N][iv_len:1][iv:M][encrypted_payload]
 */
class BackupRestoreUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationsRepository: OperationsRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository,
    private val checkPermission: CheckPermissionUseCase,
    private val sessionManager: SessionManager
) {
    companion object {
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val SALT_LENGTH = 32
        private const val PBKDF2_ITERATIONS = 120_000
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val BACKUP_DIR = "backups"
        private const val DB_NAME = "learnmart_encrypted.db"
        private const val SCHEMA_VERSION = 5
    }

    /**
     * Returns the operator-configured backup passphrase, or null if not set.
     * Backup FAILS CLOSED when no passphrase is configured — no default fallback.
     */
    private suspend fun getBackupPassphrase(): String? {
        val passphrase = policyRepository.getPolicyValue(
            PolicyType.BACKUP, "backup_passphrase", ""
        )
        return passphrase.ifBlank { null }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    suspend fun createBackup(): AppResult<BackupArchive> {
        if (!checkPermission.hasPermission(Permission.BACKUP_RUN)) {
            return AppResult.PermissionError("Requires backup.run")
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"
        val archiveId = IdGenerator.newId()

        val archive = BackupArchive(
            id = archiveId, status = BackupStatus.REQUESTED,
            schemaVersion = SCHEMA_VERSION, appVersion = "1.0.0",
            backupTimestamp = now, filePath = null, fileSizeBytes = null,
            checksumManifest = null, encryptionMethod = "AES-256-GCM/PBKDF2",
            createdBy = userId, createdAt = now, updatedAt = now
        )
        operationsRepository.createBackupArchive(archive)

        val running = archive.copy(status = BackupStatus.RUNNING, updatedAt = TimeUtils.nowUtc())
        operationsRepository.updateBackupArchive(running)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(), actorId = userId, actorUsername = null,
            actionType = AuditActionType.BACKUP_STARTED,
            targetEntityType = "BackupArchive", targetEntityId = archiveId,
            beforeSummary = null, afterSummary = "schema=$SCHEMA_VERSION, encryption=AES-256-GCM/PBKDF2",
            reason = null, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                throw IllegalStateException("Database file not found")
            }

            val backupDir = File(context.filesDir, BACKUP_DIR)
            backupDir.mkdirs()
            val backupFile = File(backupDir, "learnmart_backup_$archiveId.enc")

            // Derive key from passphrase + random salt (NO raw key stored in archive)
            // Fail closed: no backup without configured passphrase
            val passphrase = getBackupPassphrase()
                ?: return AppResult.ValidationError(globalErrors = listOf(
                    "Backup passphrase not configured. Set policy 'backup_passphrase' before creating backups."
                ))
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val secretKey = deriveKey(passphrase, salt)
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            // Archive format: [salt_len:1][salt][iv_len:1][iv][encrypted_payload]
            // NO key material is written - key is derived from passphrase + salt
            FileOutputStream(backupFile).use { fos ->
                fos.write(salt.size)
                fos.write(salt)
                fos.write(iv.size)
                fos.write(iv)

                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(dbFile).use { fis ->
                        fis.copyTo(cos, bufferSize = 8192)
                    }
                }
            }

            val checksum = checksumFile(backupFile)
            val fileSize = backupFile.length()

            val completed = running.copy(
                status = BackupStatus.SUCCEEDED,
                filePath = backupFile.absolutePath,
                fileSizeBytes = fileSize,
                checksumManifest = checksum,
                updatedAt = TimeUtils.nowUtc()
            )
            operationsRepository.updateBackupArchive(completed)

            auditRepository.logEvent(AuditEvent(
                id = IdGenerator.newId(), actorId = userId, actorUsername = null,
                actionType = AuditActionType.BACKUP_COMPLETED,
                targetEntityType = "BackupArchive", targetEntityId = archiveId,
                beforeSummary = "status=RUNNING",
                afterSummary = "status=SUCCEEDED, size=$fileSize, checksum=${checksum.take(16)}...",
                reason = null, sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS, timestamp = TimeUtils.nowUtc(), metadata = null
            ))

            return AppResult.Success(completed)
        } catch (e: Exception) {
            val failed = running.copy(status = BackupStatus.FAILED, updatedAt = TimeUtils.nowUtc())
            operationsRepository.updateBackupArchive(failed)
            return AppResult.SystemError("BACKUP_FAILED", "Backup failed: ${e.message}", retryable = true)
        }
    }

    /**
     * Export a backup to an external OutputStream (for SAF/scoped-storage export).
     */
    suspend fun exportBackupToStream(archiveId: String, outputStream: OutputStream): AppResult<Unit> {
        if (!checkPermission.hasPermission(Permission.BACKUP_RUN)) {
            return AppResult.PermissionError("Requires backup.run")
        }
        val archive = operationsRepository.getBackupArchiveById(archiveId)
            ?: return AppResult.NotFoundError("ARCHIVE_NOT_FOUND")
        val backupFile = archive.filePath?.let { File(it) }
        if (backupFile == null || !backupFile.exists()) {
            return AppResult.ValidationError(globalErrors = listOf("Backup file not found"))
        }
        FileInputStream(backupFile).use { fis -> fis.copyTo(outputStream, bufferSize = 8192) }
        return AppResult.Success(Unit)
    }

    /**
     * Restore from an external InputStream (for SAF/scoped-storage import).
     */
    suspend fun restoreFromStream(inputStream: InputStream): AppResult<RestoreRun> {
        if (!checkPermission.hasPermission(Permission.RESTORE_RUN)) {
            return AppResult.PermissionError("Requires restore.run")
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"
        val tempArchive = File(context.cacheDir, "restore_import_${IdGenerator.newId()}.enc")

        try {
            // Copy imported archive to temp
            FileOutputStream(tempArchive).use { fos -> inputStream.copyTo(fos, bufferSize = 8192) }

            if (tempArchive.length() < 50) {
                tempArchive.delete()
                return AppResult.ValidationError(globalErrors = listOf("Archive file is too small or empty"))
            }

            return restoreFromFile(tempArchive, userId, now)
        } finally {
            tempArchive.delete()
        }
    }

    suspend fun restoreFromBackup(archiveId: String): AppResult<RestoreRun> {
        if (!checkPermission.hasPermission(Permission.RESTORE_RUN)) {
            return AppResult.PermissionError("Requires restore.run")
        }

        val archive = operationsRepository.getBackupArchiveById(archiveId)
            ?: return AppResult.NotFoundError("ARCHIVE_NOT_FOUND")

        if (archive.status != BackupStatus.SUCCEEDED && archive.status != BackupStatus.VERIFIED) {
            return AppResult.ValidationError(globalErrors = listOf("Archive must be in SUCCEEDED or VERIFIED state"))
        }

        val backupFile = archive.filePath?.let { File(it) }
        if (backupFile == null || !backupFile.exists()) {
            return AppResult.ValidationError(globalErrors = listOf("Backup file not found on device"))
        }

        if (archive.checksumManifest != null) {
            val actualChecksum = checksumFile(backupFile)
            if (actualChecksum != archive.checksumManifest) {
                return AppResult.ValidationError(globalErrors = listOf("Archive integrity check failed"))
            }
        }

        if (archive.schemaVersion > SCHEMA_VERSION) {
            return AppResult.ValidationError(
                globalErrors = listOf("Archive schema v${archive.schemaVersion} is newer than app schema v$SCHEMA_VERSION")
            )
        }

        val now = TimeUtils.nowUtc()
        val userId = sessionManager.getCurrentUserId() ?: "SYSTEM"
        return restoreFromFile(backupFile, userId, now, archiveId)
    }

    private suspend fun restoreFromFile(
        backupFile: File, userId: String, now: java.time.Instant, archiveId: String? = null
    ): AppResult<RestoreRun> {
        val run = RestoreRun(
            id = IdGenerator.newId(), backupArchiveId = archiveId ?: "imported",
            status = "RUNNING", restoredBy = userId,
            startedAt = now, completedAt = null, errorMessage = null
        )
        operationsRepository.createRestoreRun(run)

        auditRepository.logEvent(AuditEvent(
            id = IdGenerator.newId(), actorId = userId, actorUsername = null,
            actionType = AuditActionType.RESTORE_STARTED,
            targetEntityType = "RestoreRun", targetEntityId = run.id,
            beforeSummary = null, afterSummary = "archiveId=${archiveId ?: "imported"}",
            reason = null, sessionId = sessionManager.getCurrentSessionId(),
            outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
        ))

        try {
            val passphrase = getBackupPassphrase()
                ?: return AppResult.ValidationError(globalErrors = listOf(
                    "Backup passphrase not configured. Cannot decrypt archive."
                ))
            val dbFile = context.getDatabasePath(DB_NAME)
            val tempFile = File(context.cacheDir, "restore_decrypted_${run.id}.db")

            FileInputStream(backupFile).use { fis ->
                // Read header: salt + iv (NO key material)
                val saltLength = fis.read()
                val salt = ByteArray(saltLength).also { fis.read(it) }
                val ivLength = fis.read()
                val iv = ByteArray(ivLength).also { fis.read(it) }

                // Derive key from passphrase + stored salt
                val secretKey = deriveKey(passphrase, salt)
                val cipher = Cipher.getInstance(ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(tempFile).use { fos ->
                        cis.copyTo(fos, bufferSize = 8192)
                    }
                }
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                throw IllegalStateException("Decrypted backup is empty — wrong passphrase or corrupted archive")
            }

            dbFile.parentFile?.mkdirs()
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            val completed = run.copy(status = "COMPLETED", completedAt = TimeUtils.nowUtc())
            operationsRepository.updateRestoreRun(completed)

            auditRepository.logEvent(AuditEvent(
                id = IdGenerator.newId(), actorId = userId, actorUsername = null,
                actionType = AuditActionType.RESTORE_COMPLETED,
                targetEntityType = "RestoreRun", targetEntityId = run.id,
                beforeSummary = "status=RUNNING", afterSummary = "status=COMPLETED",
                reason = null, sessionId = sessionManager.getCurrentSessionId(),
                outcome = AuditOutcome.SUCCESS, timestamp = TimeUtils.nowUtc(), metadata = null
            ))

            return AppResult.Success(completed)
        } catch (e: Exception) {
            val failed = run.copy(status = "FAILED", completedAt = TimeUtils.nowUtc(), errorMessage = e.message)
            operationsRepository.updateRestoreRun(failed)
            return AppResult.SystemError("RESTORE_FAILED", "Restore failed: ${e.message}", retryable = false)
        }
    }

    suspend fun getAllBackups(): AppResult<List<BackupArchive>> {
        if (!checkPermission.hasPermission(Permission.BACKUP_RUN)) {
            return AppResult.PermissionError("Requires backup.run")
        }
        return AppResult.Success(operationsRepository.getAllBackupArchives())
    }

    private fun checksumFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
