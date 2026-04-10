package com.learnmart.app.security

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class CredentialManagerTest {

    private lateinit var credentialManager: CredentialManager

    @Before
    fun setUp() {
        credentialManager = CredentialManager()
    }

    @Test
    fun `generateSalt returns 32 byte array`() {
        val salt = credentialManager.generateSalt()
        assertThat(salt.size).isEqualTo(32)
    }

    @Test
    fun `generateSalt returns unique salts`() {
        val salt1 = credentialManager.generateSalt()
        val salt2 = credentialManager.generateSalt()
        assertThat(salt1).isNotEqualTo(salt2)
    }

    @Test
    fun `hashCredential produces consistent hash for same input`() {
        val salt = credentialManager.generateSalt()
        val hash1 = credentialManager.hashCredential("password123", salt)
        val hash2 = credentialManager.hashCredential("password123", salt)
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `hashCredential produces different hash for different passwords`() {
        val salt = credentialManager.generateSalt()
        val hash1 = credentialManager.hashCredential("password123", salt)
        val hash2 = credentialManager.hashCredential("password456", salt)
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `hashCredential produces different hash for different salts`() {
        val salt1 = credentialManager.generateSalt()
        val salt2 = credentialManager.generateSalt()
        val hash1 = credentialManager.hashCredential("password123", salt1)
        val hash2 = credentialManager.hashCredential("password123", salt2)
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `verifyCredential returns true for matching credential`() {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential("myPassword", salt)
        assertThat(credentialManager.verifyCredential("myPassword", salt, hash)).isTrue()
    }

    @Test
    fun `verifyCredential returns false for non-matching credential`() {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential("myPassword", salt)
        assertThat(credentialManager.verifyCredential("wrongPassword", salt, hash)).isFalse()
    }

    @Test
    fun `hex conversion roundtrips correctly for salt`() {
        val salt = credentialManager.generateSalt()
        val hex = credentialManager.saltToHex(salt)
        val recovered = credentialManager.hexToSalt(hex)
        assertThat(recovered).isEqualTo(salt)
    }

    @Test
    fun `hex conversion roundtrips correctly for hash`() {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential("test", salt)
        val hex = credentialManager.hashToHex(hash)
        val recovered = credentialManager.hexToHash(hex)
        assertThat(recovered).isEqualTo(hash)
    }

    @Test
    fun `validatePasswordStrength rejects short password`() {
        val errors = credentialManager.validatePasswordStrength("short")
        assertThat(errors).isNotEmpty()
        assertThat(errors.first()).contains("at least 8 characters")
    }

    @Test
    fun `validatePasswordStrength accepts valid password`() {
        val errors = credentialManager.validatePasswordStrength("validpass")
        assertThat(errors).isEmpty()
    }

    @Test
    fun `validatePinStrength rejects short pin`() {
        val errors = credentialManager.validatePinStrength("12")
        assertThat(errors).isNotEmpty()
    }

    @Test
    fun `validatePinStrength rejects non-numeric pin`() {
        val errors = credentialManager.validatePinStrength("12ab")
        assertThat(errors).isNotEmpty()
        assertThat(errors.first()).contains("only digits")
    }

    @Test
    fun `validatePinStrength accepts valid pin`() {
        val errors = credentialManager.validatePinStrength("1234")
        assertThat(errors).isEmpty()
    }
}
