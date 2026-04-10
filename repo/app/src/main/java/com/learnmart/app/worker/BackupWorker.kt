package com.learnmart.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.AppResult
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dedicated worker for backup jobs. Executes real AES-256-GCM encrypted backup
 * of the Room database. Scheduled with idle+charging constraints.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val operationsRepository: OperationsRepository,
    private val policyRepository: PolicyRepository,
    private val auditRepository: AuditRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val now = TimeUtils.nowUtc()
        val archiveId = IdGenerator.newId()

        return try {
            // Get passphrase - fail if not configured
            val passphrase = policyRepository.getPolicyValue(
                PolicyType.BACKUP, "backup_passphrase", ""
            )
            if (passphrase.isBlank()) {
                auditRepository.logEvent(AuditEvent(
                    id = IdGenerator.newId(), actorId = "SYSTEM", actorUsername = "SYSTEM",
                    actionType = AuditActionType.BACKUP_STARTED,
                    targetEntityType = "BackupWorker", targetEntityId = archiveId,
                    beforeSummary = null, afterSummary = "FAILED: No backup passphrase configured",
                    reason = "backup_passphrase policy not set", sessionId = null,
                    outcome = AuditOutcome.FAILURE, timestamp = now, metadata = null
                ))
                return Result.failure()
            }

            val dbFile = applicationContext.getDatabasePath("learnmart_encrypted.db")
            if (!dbFile.exists()) return Result.failure()

            val backupDir = File(applicationContext.filesDir, "backups")
            backupDir.mkdirs()
            val backupFile = File(backupDir, "learnmart_backup_$archiveId.enc")

            // PBKDF2 key derivation
            val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 120_000, 256)
            val secretKey = SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES"
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            FileOutputStream(backupFile).use { fos ->
                fos.write(salt.size)
                fos.write(salt)
                fos.write(iv.size)
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(dbFile).use { fis -> fis.copyTo(cos, 8192) }
                }
            }

            // Checksum
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(backupFile).use { fis ->
                val buf = ByteArray(8192); var n: Int
                while (fis.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }

            val archive = BackupArchive(
                id = archiveId, status = BackupStatus.SUCCEEDED,
                schemaVersion = 5, appVersion = "1.0.0",
                backupTimestamp = now, filePath = backupFile.absolutePath,
                fileSizeBytes = backupFile.length(), checksumManifest = checksum,
                encryptionMethod = "AES-256-GCM/PBKDF2",
                createdBy = "SYSTEM", createdAt = now, updatedAt = now
            )
            operationsRepository.createBackupArchive(archive)

            auditRepository.logEvent(AuditEvent(
                id = IdGenerator.newId(), actorId = "SYSTEM", actorUsername = "SYSTEM",
                actionType = AuditActionType.BACKUP_COMPLETED,
                targetEntityType = "BackupArchive", targetEntityId = archiveId,
                beforeSummary = null, afterSummary = "size=${backupFile.length()}, checksum=${checksum.take(16)}",
                reason = null, sessionId = null,
                outcome = AuditOutcome.SUCCESS, timestamp = now, metadata = null
            ))

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}
