package com.learnmart.app.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * No-mock integration tests for CredentialManager.
 * Uses real PBKDF2 crypto — no mocking at all.
 */
class CredentialIntegrationTest {

    private val credentialManager = CredentialManager()

    @Test
    fun `hash and verify round-trips correctly`() {
        val password = "admin1234"
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential(password, salt)
        assertThat(credentialManager.verifyCredential(password, salt, hash)).isTrue()
    }

    @Test
    fun `wrong password does not verify`() {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential("correct", salt)
        assertThat(credentialManager.verifyCredential("wrong", salt, hash)).isFalse()
    }

    @Test
    fun `different salts produce different hashes`() {
        val salt1 = credentialManager.generateSalt()
        val salt2 = credentialManager.generateSalt()
        val hash1 = credentialManager.hashCredential("password", salt1)
        val hash2 = credentialManager.hashCredential("password", salt2)
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `salt hex round-trip preserves bytes`() {
        val original = credentialManager.generateSalt()
        val hex = credentialManager.saltToHex(original)
        val restored = credentialManager.hexToSalt(hex)
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `hash hex round-trip preserves bytes`() {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential("test", salt)
        val hex = credentialManager.hashToHex(hash)
        val restored = credentialManager.hexToHash(hex)
        assertThat(restored).isEqualTo(hash)
    }

    @Test
    fun `password validation enforces minimum length`() {
        assertThat(credentialManager.validatePasswordStrength("short")).isNotEmpty()
        assertThat(credentialManager.validatePasswordStrength("longenough")).isEmpty()
    }

    @Test
    fun `PIN validation enforces digits only`() {
        assertThat(credentialManager.validatePinStrength("1234")).isEmpty()
        assertThat(credentialManager.validatePinStrength("abc")).isNotEmpty()
        assertThat(credentialManager.validatePinStrength("12")).isNotEmpty() // too short
    }
}
