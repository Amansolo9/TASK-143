package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.PolicyRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class PricingEngineTest {

    private lateinit var policyRepository: PolicyRepository
    private lateinit var pricingEngine: PricingEngine

    @Before
    fun setUp() {
        policyRepository = mockk()
        coEvery { policyRepository.getPolicyValue(PolicyType.TAX, "default_tax_rate", any()) } returns "0.08"
        coEvery { policyRepository.getPolicyValue(PolicyType.TAX, "default_service_fee_rate", any()) } returns "0.02"
        coEvery { policyRepository.getPolicyValue(PolicyType.COMMERCE, "packaging_fee", any()) } returns "1.50"
        coEvery { policyRepository.getPolicyValue(PolicyType.COMMERCE, "minimum_order_total", any()) } returns "25.00"
        coEvery { policyRepository.getPolicyValue(PolicyType.COMMERCE, "checkout_policy", any()) } returns "SAME_CLASS_ONLY"
        pricingEngine = PricingEngine(policyRepository)
    }

    @Test
    fun `empty items returns zero pricing`() = runTest {
        val result = pricingEngine.calculatePricing(emptyList())
        assertThat(result.grandTotal).isEqualTo(MoneyUtils.ZERO)
    }

    @Test
    fun `course fee only - no packaging fee`() = runTest {
        val items = listOf(
            CartLineItem("1", "cart1", LineItemType.COURSE_FEE, "course1", "class1",
                "Course", 1, BigDecimal("100.00"), BigDecimal("100.00"), Instant.now())
        )
        val result = pricingEngine.calculatePricing(items)

        assertThat(result.subtotal).isEqualTo(BigDecimal("100.00"))
        assertThat(result.taxAmount).isEqualTo(BigDecimal("8.00"))
        assertThat(result.serviceFee).isEqualTo(BigDecimal("2.00"))
        assertThat(result.packagingFee).isEqualTo(MoneyUtils.ZERO)
        assertThat(result.grandTotal).isEqualTo(BigDecimal("110.00"))
        assertThat(result.hasPhysicalItems).isFalse()
    }

    @Test
    fun `physical material includes packaging fee`() = runTest {
        val items = listOf(
            CartLineItem("1", "cart1", LineItemType.PHYSICAL_MATERIAL, "mat1", "class1",
                "Textbook", 2, BigDecimal("30.00"), BigDecimal("60.00"), Instant.now())
        )
        val result = pricingEngine.calculatePricing(items)

        assertThat(result.subtotal).isEqualTo(BigDecimal("60.00"))
        assertThat(result.taxAmount).isEqualTo(BigDecimal("4.80"))
        assertThat(result.serviceFee).isEqualTo(BigDecimal("1.20"))
        assertThat(result.packagingFee).isEqualTo(BigDecimal("1.50"))
        assertThat(result.grandTotal).isEqualTo(BigDecimal("67.50"))
        assertThat(result.hasPhysicalItems).isTrue()
    }

    @Test
    fun `mixed items include packaging fee`() = runTest {
        val items = listOf(
            CartLineItem("1", "cart1", LineItemType.COURSE_FEE, "course1", "class1",
                "Course", 1, BigDecimal("200.00"), BigDecimal("200.00"), Instant.now()),
            CartLineItem("2", "cart1", LineItemType.PHYSICAL_MATERIAL, "mat1", "class1",
                "Textbook", 1, BigDecimal("50.00"), BigDecimal("50.00"), Instant.now())
        )
        val result = pricingEngine.calculatePricing(items)

        assertThat(result.subtotal).isEqualTo(BigDecimal("250.00"))
        assertThat(result.taxAmount).isEqualTo(BigDecimal("20.00"))
        assertThat(result.serviceFee).isEqualTo(BigDecimal("5.00"))
        assertThat(result.packagingFee).isEqualTo(BigDecimal("1.50"))
        assertThat(result.grandTotal).isEqualTo(BigDecimal("276.50"))
    }

    @Test
    fun `money rounding uses half up`() {
        // 33.33 * 0.08 = 2.6664 -> rounds to 2.67
        val tax = MoneyUtils.calculateTax(BigDecimal("33.33"), BigDecimal("0.08"))
        assertThat(tax).isEqualTo(BigDecimal("2.67"))
    }

    @Test
    fun `minimum order total from policy`() = runTest {
        val min = pricingEngine.getMinimumOrderTotal()
        assertThat(min).isEqualTo(BigDecimal("25.00"))
    }

    @Test
    fun `checkout policy from policy`() = runTest {
        val policy = pricingEngine.getCheckoutPolicy()
        assertThat(policy).isEqualTo(CheckoutPolicy.SAME_CLASS_ONLY)
    }
}
