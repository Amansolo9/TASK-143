package com.learnmart.app.domain.usecase.commerce

import com.learnmart.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class OrderLifecycleTest {

    @Test
    fun `order state machine - PLACED_UNPAID transitions`() {
        assertThat(OrderStatus.PLACED_UNPAID.canTransitionTo(OrderStatus.PARTIALLY_PAID)).isTrue()
        assertThat(OrderStatus.PLACED_UNPAID.canTransitionTo(OrderStatus.PAID)).isTrue()
        assertThat(OrderStatus.PLACED_UNPAID.canTransitionTo(OrderStatus.AUTO_CANCELLED)).isTrue()
        assertThat(OrderStatus.PLACED_UNPAID.canTransitionTo(OrderStatus.DELIVERED)).isFalse()
    }

    @Test
    fun `order state machine - PAID transitions`() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FULFILLMENT_IN_PROGRESS)).isTrue()
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUND_IN_PROGRESS)).isTrue()
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.DELIVERED)).isFalse()
    }

    @Test
    fun `order state machine - DELIVERED transitions`() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CLOSED)).isTrue()
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.RETURN_REQUESTED)).isTrue()
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.EXCHANGE_IN_PROGRESS)).isTrue()
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PAID)).isFalse()
    }

    @Test
    fun `order state machine - terminal states`() {
        assertThat(OrderStatus.AUTO_CANCELLED.isTerminal()).isTrue()
        assertThat(OrderStatus.MANUAL_CANCELLED.isTerminal()).isTrue()
        assertThat(OrderStatus.RETURNED.isTerminal()).isTrue()
        assertThat(OrderStatus.EXCHANGED.isTerminal()).isTrue()
        assertThat(OrderStatus.PAID.isTerminal()).isFalse()
    }

    @Test
    fun `order state machine - cannot fulfill before PAID`() {
        assertThat(OrderStatus.PLACED_UNPAID.canTransitionTo(OrderStatus.FULFILLMENT_IN_PROGRESS)).isFalse()
        assertThat(OrderStatus.PARTIALLY_PAID.canTransitionTo(OrderStatus.FULFILLMENT_IN_PROGRESS)).isFalse()
    }

    @Test
    fun `payment state machine - RECORDED transitions`() {
        assertThat(PaymentStatus.RECORDED.canTransitionTo(PaymentStatus.ALLOCATED)).isTrue()
        assertThat(PaymentStatus.RECORDED.canTransitionTo(PaymentStatus.VOIDED)).isTrue()
        assertThat(PaymentStatus.RECORDED.canTransitionTo(PaymentStatus.DISCREPANCY_FLAGGED)).isTrue()
        assertThat(PaymentStatus.RECORDED.canTransitionTo(PaymentStatus.REFUNDED)).isFalse()
    }

    @Test
    fun `payment state machine - VOIDED is terminal`() {
        assertThat(PaymentStatus.VOIDED.allowedTransitions()).isEmpty()
    }

    @Test
    fun `payment state machine - REFUNDED is terminal`() {
        assertThat(PaymentStatus.REFUNDED.allowedTransitions()).isEmpty()
    }

    @Test
    fun `money utils - round half up`() {
        assertThat(MoneyUtils.round(BigDecimal("10.005"))).isEqualTo(BigDecimal("10.01"))
        assertThat(MoneyUtils.round(BigDecimal("10.004"))).isEqualTo(BigDecimal("10.00"))
        assertThat(MoneyUtils.round(BigDecimal("10.125"))).isEqualTo(BigDecimal("10.13"))
    }

    @Test
    fun `money utils - tax calculation`() {
        val tax = MoneyUtils.calculateTax(BigDecimal("100.00"), BigDecimal("0.0825"))
        assertThat(tax).isEqualTo(BigDecimal("8.25"))
    }

    @Test
    fun `money utils - service fee calculation`() {
        val fee = MoneyUtils.calculateServiceFee(BigDecimal("200.00"), BigDecimal("0.015"))
        assertThat(fee).isEqualTo(BigDecimal("3.00"))
    }

    @Test
    fun `minimum refund amount enforced`() {
        assertThat(MoneyUtils.MIN_REFUND).isEqualTo(BigDecimal("0.01"))
    }

    @Test
    fun `minimum order total enforced`() {
        assertThat(MoneyUtils.MIN_ORDER_TOTAL).isEqualTo(BigDecimal("25.00"))
    }
}
