package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.PolicyRepository
import java.math.BigDecimal
import javax.inject.Inject

data class PricingResult(
    val subtotal: BigDecimal,
    val discountTotal: BigDecimal,
    val taxAmount: BigDecimal,
    val serviceFee: BigDecimal,
    val packagingFee: BigDecimal,
    val grandTotal: BigDecimal,
    val taxRate: BigDecimal,
    val serviceFeeRate: BigDecimal,
    val hasPhysicalItems: Boolean
)

class PricingEngine @Inject constructor(
    private val policyRepository: PolicyRepository
) {
    suspend fun calculatePricing(lineItems: List<CartLineItem>): PricingResult {
        if (lineItems.isEmpty()) {
            return PricingResult(
                subtotal = MoneyUtils.ZERO,
                discountTotal = MoneyUtils.ZERO,
                taxAmount = MoneyUtils.ZERO,
                serviceFee = MoneyUtils.ZERO,
                packagingFee = MoneyUtils.ZERO,
                grandTotal = MoneyUtils.ZERO,
                taxRate = MoneyUtils.ZERO,
                serviceFeeRate = MoneyUtils.ZERO,
                hasPhysicalItems = false
            )
        }

        // Get policy rates
        val taxRateStr = policyRepository.getPolicyValue(PolicyType.TAX, "default_tax_rate", PolicyDefaults.DEFAULT_TAX_RATE)
        val serviceFeeRateStr = policyRepository.getPolicyValue(PolicyType.TAX, "default_service_fee_rate", PolicyDefaults.DEFAULT_SERVICE_FEE_RATE)
        val packagingFeeStr = policyRepository.getPolicyValue(PolicyType.COMMERCE, "packaging_fee", PolicyDefaults.PACKAGING_FEE)

        val taxRate = BigDecimal(taxRateStr)
        val serviceFeeRate = BigDecimal(serviceFeeRateStr)
        val defaultPackagingFee = BigDecimal(packagingFeeStr)

        // Calculate subtotal
        val subtotal = lineItems.fold(MoneyUtils.ZERO) { acc, item ->
            acc.add(item.lineTotal)
        }

        // Discounts (placeholder for future discount engine)
        val discountTotal = MoneyUtils.ZERO

        val afterDiscount = subtotal.subtract(discountTotal)

        // Tax on after-discount amount
        val taxAmount = MoneyUtils.calculateTax(afterDiscount, taxRate)

        // Service fee on after-discount amount
        val serviceFee = MoneyUtils.calculateServiceFee(afterDiscount, serviceFeeRate)

        // Packaging fee only if physical items present
        val hasPhysicalItems = lineItems.any { it.itemType == LineItemType.PHYSICAL_MATERIAL }
        val packagingFee = if (hasPhysicalItems) MoneyUtils.round(defaultPackagingFee) else MoneyUtils.ZERO

        // Grand total
        val grandTotal = MoneyUtils.round(
            afterDiscount.add(taxAmount).add(serviceFee).add(packagingFee)
        )

        return PricingResult(
            subtotal = MoneyUtils.round(subtotal),
            discountTotal = MoneyUtils.round(discountTotal),
            taxAmount = taxAmount,
            serviceFee = serviceFee,
            packagingFee = packagingFee,
            grandTotal = grandTotal,
            taxRate = taxRate,
            serviceFeeRate = serviceFeeRate,
            hasPhysicalItems = hasPhysicalItems
        )
    }

    suspend fun getMinimumOrderTotal(): BigDecimal {
        val minStr = policyRepository.getPolicyValue(
            PolicyType.COMMERCE, "minimum_order_total", PolicyDefaults.MINIMUM_ORDER_TOTAL
        )
        return BigDecimal(minStr)
    }

    suspend fun getCheckoutPolicy(): CheckoutPolicy {
        val policyStr = policyRepository.getPolicyValue(
            PolicyType.COMMERCE, "checkout_policy", PolicyDefaults.CHECKOUT_POLICY
        )
        return try {
            CheckoutPolicy.valueOf(policyStr)
        } catch (e: IllegalArgumentException) {
            CheckoutPolicy.SAME_CLASS_ONLY
        }
    }
}
