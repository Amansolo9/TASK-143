package com.learnmart.app.security

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SettlementSignatureVerifierTest {

    private lateinit var verifier: SettlementSignatureVerifier

    @Before
    fun setUp() {
        verifier = SettlementSignatureVerifier()
    }

    @Test
    fun `valid signature passes verification`() {
        val content = "test settlement data".toByteArray()
        val secret = "my-secret-key-123"
        val signature = verifier.computeSignature(content, secret)

        val result = verifier.verify(content, signature, secret)
        assertThat(result).isEqualTo(SignatureResult.Valid)
    }

    @Test
    fun `invalid signature fails verification`() {
        val content = "test settlement data".toByteArray()
        val secret = "my-secret-key-123"

        val result = verifier.verify(content, "0000deadbeef", secret)
        assertThat(result).isInstanceOf(SignatureResult.Invalid::class.java)
    }

    @Test
    fun `tampered content fails verification`() {
        val originalContent = "original data".toByteArray()
        val tamperedContent = "tampered data".toByteArray()
        val secret = "my-secret-key-123"
        val signature = verifier.computeSignature(originalContent, secret)

        val result = verifier.verify(tamperedContent, signature, secret)
        assertThat(result).isInstanceOf(SignatureResult.Invalid::class.java)
    }

    @Test
    fun `wrong secret fails verification`() {
        val content = "test data".toByteArray()
        val signature = verifier.computeSignature(content, "correct-secret")

        val result = verifier.verify(content, signature, "wrong-secret")
        assertThat(result).isInstanceOf(SignatureResult.Invalid::class.java)
    }

    @Test
    fun `empty secret returns NoSecretConfigured`() {
        val content = "test data".toByteArray()
        val result = verifier.verify(content, "some-sig", "")
        assertThat(result).isEqualTo(SignatureResult.NoSecretConfigured)
    }

    @Test
    fun `computeSignature is deterministic`() {
        val content = "same content".toByteArray()
        val secret = "same-secret"
        val sig1 = verifier.computeSignature(content, secret)
        val sig2 = verifier.computeSignature(content, secret)
        assertThat(sig1).isEqualTo(sig2)
    }

    @Test
    fun `different content produces different signatures`() {
        val secret = "test-secret"
        val sig1 = verifier.computeSignature("content A".toByteArray(), secret)
        val sig2 = verifier.computeSignature("content B".toByteArray(), secret)
        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `signature is case insensitive for hex comparison`() {
        val content = "test".toByteArray()
        val secret = "secret"
        val sig = verifier.computeSignature(content, secret)

        val resultUpper = verifier.verify(content, sig.uppercase(), secret)
        assertThat(resultUpper).isEqualTo(SignatureResult.Valid)
    }
}
