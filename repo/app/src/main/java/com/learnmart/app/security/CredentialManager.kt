package com.learnmart.app.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialManager @Inject constructor() {

    companion object {
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun hashCredential(credential: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(credential.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    fun verifyCredential(credential: String, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val computedHash = hashCredential(credential, salt)
        return computedHash.contentEquals(expectedHash)
    }

    fun saltToHex(salt: ByteArray): String = salt.joinToString("") { "%02x".format(it) }

    fun hexToSalt(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun hashToHex(hash: ByteArray): String = hash.joinToString("") { "%02x".format(it) }

    fun hexToHash(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun validatePasswordStrength(password: String): List<String> {
        val errors = mutableListOf<String>()
        if (password.length < 8) errors.add("Password must be at least 8 characters")
        return errors
    }

    fun validatePinStrength(pin: String): List<String> {
        val errors = mutableListOf<String>()
        if (pin.length < 4) errors.add("PIN must be at least 4 digits")
        if (!pin.all { it.isDigit() }) errors.add("PIN must contain only digits")
        return errors
    }
}
