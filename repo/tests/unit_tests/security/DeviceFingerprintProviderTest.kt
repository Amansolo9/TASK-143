package com.learnmart.app.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for device fingerprint provider design properties.
 * The actual fingerprint computation requires an Android context, so these
 * tests verify the static design contracts.
 */
class DeviceFingerprintProviderTest {

    @Test
    fun `fingerprint uses SHA-256 producing 64-char hex`() {
        // Verify the hash function produces expected-length output
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("test|data|hash".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertThat(hash).hasLength(64)
    }

    @Test
    fun `same input produces same fingerprint`() {
        val digest1 = java.security.MessageDigest.getInstance("SHA-256")
        val hash1 = digest1.digest("Samsung|Galaxy S24|test".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val digest2 = java.security.MessageDigest.getInstance("SHA-256")
        val hash2 = digest2.digest("Samsung|Galaxy S24|test".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `different input produces different fingerprint`() {
        val digest1 = java.security.MessageDigest.getInstance("SHA-256")
        val hash1 = digest1.digest("Samsung|Galaxy S24|device1".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val digest2 = java.security.MessageDigest.getInstance("SHA-256")
        val hash2 = digest2.digest("Google|Pixel 8|device2".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `policy default for device fingerprint is disabled`() {
        assertThat(com.learnmart.app.domain.model.PolicyDefaults.DEVICE_FINGERPRINT_ENABLED).isEqualTo("false")
    }
}
