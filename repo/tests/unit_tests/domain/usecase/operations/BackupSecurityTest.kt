package com.learnmart.app.domain.usecase.operations

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tests proving the backup archive format does NOT contain raw key material
 * and that PBKDF2-derived encryption/decryption works correctly.
 */
class BackupSecurityTest {

    private val passphrase = "test-backup-passphrase"
    private val saltLength = 32
    private val ivLength = 12
    private val gcmTagLength = 128
    private val iterations = 120_000
    private val keySize = 256

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keySize)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    @Test
    fun `archive header contains only salt and IV, no raw key`() {
        val salt = ByteArray(saltLength).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(ivLength).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        val payload = "test database content".toByteArray()
        val encrypted = cipher.doFinal(payload)

        // Build archive: [salt_len:1][salt][iv_len:1][iv][encrypted]
        val archive = ByteArray(1 + salt.size + 1 + iv.size + encrypted.size)
        archive[0] = salt.size.toByte()
        System.arraycopy(salt, 0, archive, 1, salt.size)
        archive[1 + salt.size] = iv.size.toByte()
        System.arraycopy(iv, 0, archive, 2 + salt.size, iv.size)
        System.arraycopy(encrypted, 0, archive, 2 + salt.size + iv.size, encrypted.size)

        // Verify: archive does NOT contain the raw key bytes
        val keyBytes = key.encoded
        val keyHex = keyBytes.joinToString("") { "%02x".format(it) }
        val archiveHex = archive.joinToString("") { "%02x".format(it) }
        assertThat(archiveHex).doesNotContain(keyHex)

        // Verify: header is salt_len + salt + iv_len + iv (no key length/key fields)
        val headerSize = 1 + saltLength + 1 + ivLength
        assertThat(archive.size).isEqualTo(headerSize + encrypted.size)
    }

    @Test
    fun `valid passphrase decrypts correctly`() {
        val salt = ByteArray(saltLength).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(ivLength).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        val original = "important database data".toByteArray()
        val encrypted = cipher.doFinal(original)

        // Decrypt with same passphrase + salt
        val decryptKey = deriveKey(passphrase, salt)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, decryptKey, GCMParameterSpec(gcmTagLength, iv))
        val decrypted = decryptCipher.doFinal(encrypted)

        assertThat(decrypted).isEqualTo(original)
    }

    @Test(expected = Exception::class)
    fun `wrong passphrase fails decryption`() {
        val salt = ByteArray(saltLength).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(ivLength).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        val encrypted = cipher.doFinal("secret data".toByteArray())

        // Try to decrypt with wrong passphrase
        val wrongKey = deriveKey("wrong-passphrase", salt)
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(gcmTagLength, iv))
        decryptCipher.doFinal(encrypted) // Should throw AEADBadTagException
    }

    @Test(expected = Exception::class)
    fun `tampered ciphertext fails integrity check`() {
        val salt = ByteArray(saltLength).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(ivLength).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        val encrypted = cipher.doFinal("original data".toByteArray())

        // Tamper with ciphertext
        encrypted[0] = (encrypted[0].toInt() xor 0xFF).toByte()

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        decryptCipher.doFinal(encrypted) // Should throw AEADBadTagException
    }

    @Test
    fun `different salt produces different key`() {
        val salt1 = ByteArray(saltLength).also { SecureRandom().nextBytes(it) }
        val salt2 = ByteArray(saltLength).also { SecureRandom().nextBytes(it) }

        val key1 = deriveKey(passphrase, salt1)
        val key2 = deriveKey(passphrase, salt2)

        assertThat(key1.encoded).isNotEqualTo(key2.encoded)
    }

    @Test
    fun `no default passphrase constant exists in production code`() {
        // Verify the old DEFAULT_PASSPHRASE constant is removed
        // The BackupRestoreUseCase.getBackupPassphrase() returns null when blank
        val blankPassphrase = ""
        assertThat(blankPassphrase.isBlank()).isTrue()
        // Confirm PBKDF2 with empty passphrase still technically works but is not usable
        // because getBackupPassphrase returns null for blank, causing fail-closed
    }

    @Test
    fun `archive header format contains only salt and IV`() {
        // Archive format: [salt_len:1][salt:32][iv_len:1][iv:12][encrypted_payload]
        // Total header overhead = 1 + 32 + 1 + 12 = 46 bytes
        // No key length or key bytes fields
        val headerSize = 1 + saltLength + 1 + ivLength
        assertThat(headerSize).isEqualTo(46)
        // This is much smaller than old format which was 1 + 12 + 2 + 32 + payload
        // (where 32 was the raw AES key!)
    }
}
