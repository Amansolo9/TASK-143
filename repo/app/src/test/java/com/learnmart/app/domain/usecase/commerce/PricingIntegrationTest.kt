package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.PolicyRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Integration-style tests for PricingEngine using real calculation logic
 * with only the PolicyRepository mocked (minimal mocking).
 */
class PricingIntegrationTest {
    private lateinit var policyRepository: PolicyRepository
    private lateinit var engine: PricingEngine

    @Before
    fun setUp() {
        policyRepository = mockk()
        coEvery { policyRepository.getPolicyValue(PolicyType.TAX, "default_tax_rate", any()) } returns "0.0825"
        coEvery { policyRepository.getPolicyValue(PolicyType.TAX, "default_service_fee_rate", any()) } returns "0.03"
        coEvery { policyRepository.getPolicyValue(PolicyType.COMMERCE, "packaging_fee", any()) } returns "1.50"
        coEvery { policyRepository.getPolicyValue(PolicyType.COMMERCE, "minimum_order_total", any()) } returns "25.00"
        coEvery { policyRepository.getPolicyValue(PolicyType.COMMERCE, "checkout_policy", any()) } returns "SAME_CLASS_ONLY"
        engine = PricingEngine(policyRepository)
    }

    @Test
    fun `pricing calculates correct totals for single course fee`() = runTest {
        val items = listOf(CartLineItem(
            "i1", "cart-1", LineItemType.COURSE_FEE, "course-1", "class-1",
            "Kotlin Course", 1, BigDecimal("100.00"), BigDecimal("100.00"), Instant.now()
        ))
        val result = engine.calculatePricing(items)
        assertThat(result.subtotal).isEqualTo(BigDecimal("100.00"))
        assertThat(result.taxAmount.compareTo(BigDecimal.ZERO)).isGreaterThan(0)
        assertThat(result.grandTotal.compareTo(result.subtotal)).isGreaterThan(0)
    }

    @Test
    fun `pricing includes packaging fee for physical items`() = runTest {
        val items = listOf(CartLineItem(
            "i1", "cart-1", LineItemType.PHYSICAL_MATERIAL, "mat-1", "class-1",
            "Workbook", 2, BigDecimal("15.00"), BigDecimal("30.00"), Instant.now()
        ))
        val result = engine.calculatePricing(items)
        assertThat(result.hasPhysicalItems).isTrue()
        assertThat(result.packagingFee.compareTo(BigDecimal.ZERO)).isGreaterThan(0)
    }

    @Test
    fun `pricing has no packaging fee for digital-only orders`() = runTest {
        val items = listOf(CartLineItem(
            "i1", "cart-1", LineItemType.COURSE_FEE, "course-1", "class-1",
            "Course", 1, BigDecimal("50.00"), BigDecimal("50.00"), Instant.now()
        ))
        val result = engine.calculatePricing(items)
        assertThat(result.hasPhysicalItems).isFalse()
    }

    @Test
    fun `minimum order total matches policy`() = runTest {
        val minTotal = engine.getMinimumOrderTotal()
        assertThat(minTotal).isEqualTo(BigDecimal("25.00"))
    }

    @Test
    fun `checkout policy reads from config`() = runTest {
        val policy = engine.getCheckoutPolicy()
        assertThat(policy).isEqualTo(CheckoutPolicy.SAME_CLASS_ONLY)
    }

    @Test
    fun `grand total equals subtotal plus tax plus service fee plus packaging`() = runTest {
        val items = listOf(CartLineItem(
            "i1", "cart-1", LineItemType.PHYSICAL_MATERIAL, "mat-1", "class-1",
            "Book", 1, BigDecimal("40.00"), BigDecimal("40.00"), Instant.now()
        ))
        val result = engine.calculatePricing(items)
        val expected = result.subtotal
            .subtract(result.discountTotal)
            .add(result.taxAmount)
            .add(result.serviceFee)
            .add(result.packagingFee)
        assertThat(result.grandTotal.compareTo(expected)).isEqualTo(0)
    }
}
