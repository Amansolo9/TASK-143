package com.learnmart.app.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies HMAC-SHA256 signatures on settlement import files.
 * The shared secret is configured locally by the administrator.
 * Signature = HMAC-SHA256(secret, fileContent)
 */
@Singleton
class SettlementSignatureVerifier @Inject constructor() {

    companion object {
        private const val ALGORITHM = "HmacSHA256"
    }

    fun verify(fileContent: ByteArray, signatureHex: String, sharedSecret: String): SignatureResult {
        if (sharedSecret.isBlank()) {
            return SignatureResult.NoSecretConfigured
        }
        return try {
            val mac = Mac.getInstance(ALGORITHM)
            val keySpec = SecretKeySpec(sharedSecret.toByteArray(Charsets.UTF_8), ALGORITHM)
            mac.init(keySpec)
            val computed = mac.doFinal(fileContent)
            val computedHex = computed.joinToString("") { "%02x".format(it) }
            if (computedHex.equals(signatureHex, ignoreCase = true)) {
                SignatureResult.Valid
            } else {
                SignatureResult.Invalid(expected = computedHex, actual = signatureHex)
            }
        } catch (e: Exception) {
            SignatureResult.Error(e.message ?: "Signature verification failed")
        }
    }

    fun computeSignature(fileContent: ByteArray, sharedSecret: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(sharedSecret.toByteArray(Charsets.UTF_8), ALGORITHM)
        mac.init(keySpec)
        return mac.doFinal(fileContent).joinToString("") { "%02x".format(it) }
    }
}

sealed interface SignatureResult {
    data object Valid : SignatureResult
    data class Invalid(val expected: String, val actual: String) : SignatureResult
    data class Error(val message: String) : SignatureResult
    data object NoSecretConfigured : SignatureResult
}
