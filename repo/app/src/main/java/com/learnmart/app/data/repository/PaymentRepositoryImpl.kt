package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.PaymentDao
import com.learnmart.app.data.local.entity.LedgerEntryEntity
import com.learnmart.app.data.local.entity.PaymentAllocationEntity
import com.learnmart.app.data.local.entity.PaymentRecordEntity
import com.learnmart.app.data.local.entity.RefundRecordEntity
import com.learnmart.app.domain.model.DeliveryConfirmation
import com.learnmart.app.domain.model.DeliveryType
import com.learnmart.app.domain.model.FulfillmentRecord
import com.learnmart.app.domain.model.LedgerEntry
import com.learnmart.app.domain.model.LedgerEntryType
import com.learnmart.app.domain.model.PaymentAllocation
import com.learnmart.app.domain.model.PaymentRecord
import com.learnmart.app.domain.model.PaymentStatus
import com.learnmart.app.domain.model.RefundRecord
import com.learnmart.app.domain.model.ReturnExchangeRecord
import com.learnmart.app.domain.model.ReturnExchangeType
import com.learnmart.app.domain.model.TenderType
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.data.local.dao.CommerceDao
import com.learnmart.app.data.local.entity.DeliveryConfirmationEntity
import com.learnmart.app.data.local.entity.FulfillmentRecordEntity
import com.learnmart.app.data.local.entity.ReturnExchangeRecordEntity
import com.learnmart.app.util.TimeUtils
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val paymentDao: PaymentDao,
    private val commerceDao: CommerceDao
) : PaymentRepository {

    // ==================== PaymentRecord ====================

    override suspend fun recordPayment(payment: PaymentRecord): PaymentRecord {
        val entity = payment.toEntity()
        paymentDao.insertPaymentRecord(entity)
        return entity.toDomain()
    }

    override suspend fun updatePayment(payment: PaymentRecord): Boolean {
        val existing = paymentDao.getPaymentRecordById(payment.id) ?: return false
        paymentDao.updatePaymentRecord(payment.toEntity())
        return true
    }

    override suspend fun getPaymentById(id: String): PaymentRecord? =
        paymentDao.getPaymentRecordById(id)?.toDomain()

    override suspend fun getPaymentsForOrder(orderId: String): List<PaymentRecord> =
        paymentDao.getPaymentRecordsByOrderId(orderId).map { it.toDomain() }

    override suspend fun updatePaymentStatus(id: String, status: PaymentStatus, currentVersion: Int): Boolean {
        val rows = paymentDao.updatePaymentRecordStatus(id, status.name, currentVersion)
        return rows > 0
    }

    // ==================== PaymentAllocation ====================

    override suspend fun createAllocation(allocation: PaymentAllocation) {
        paymentDao.insertPaymentAllocation(allocation.toEntity())
    }

    override suspend fun getAllocationsForOrder(orderId: String): List<PaymentAllocation> =
        paymentDao.getPaymentAllocationsByOrderId(orderId).map { it.toDomain() }

    override suspend fun getTotalAllocatedForOrder(orderId: String): BigDecimal {
        val sumString = paymentDao.sumAllocatedAmountForOrder(orderId)
        return BigDecimal(sumString ?: "0.00")
    }

    // ==================== RefundRecord ====================

    override suspend fun recordRefund(refund: RefundRecord): RefundRecord {
        val entity = refund.toEntity()
        paymentDao.insertRefundRecord(entity)
        return entity.toDomain()
    }

    override suspend fun getRefundsForOrder(orderId: String): List<RefundRecord> =
        paymentDao.getRefundRecordsByOrderId(orderId).map { it.toDomain() }

    override suspend fun countRefundsForLearnerToday(learnerId: String, dayStartMs: Long, dayEndMs: Long): Int =
        paymentDao.countRefundsForLearnerOnDate(learnerId, dayStartMs, dayEndMs)

    override suspend fun getRefundsForLearner(learnerId: String): List<RefundRecord> =
        paymentDao.getRefundRecordsByLearnerId(learnerId).map { it.toDomain() }

    // ==================== LedgerEntry ====================

    override suspend fun createLedgerEntry(entry: LedgerEntry) {
        paymentDao.insertLedgerEntry(entry.toEntity())
    }

    override suspend fun getLedgerEntriesForOrder(orderId: String): List<LedgerEntry> =
        paymentDao.getLedgerEntriesByOrderId(orderId).map { it.toDomain() }

    override suspend fun getAllLedgerEntries(limit: Int, offset: Int): List<LedgerEntry> =
        paymentDao.getAllLedgerEntriesPaged(limit, offset).map { it.toDomain() }

    // ==================== FulfillmentRecord ====================

    override suspend fun createFulfillment(record: FulfillmentRecord) {
        commerceDao.insertFulfillmentRecord(record.toEntity())
    }

    override suspend fun getFulfillmentForOrder(orderId: String): FulfillmentRecord? =
        commerceDao.getFulfillmentRecordsByOrderId(orderId).firstOrNull()?.toDomain()

    // ==================== DeliveryConfirmation ====================

    override suspend fun createDeliveryConfirmation(confirmation: DeliveryConfirmation) {
        commerceDao.insertDeliveryConfirmation(confirmation.toEntity())
    }

    override suspend fun getDeliveryConfirmation(orderId: String): DeliveryConfirmation? =
        commerceDao.getDeliveryConfirmationsByOrderId(orderId).firstOrNull()?.toDomain()

    // ==================== ReturnExchangeRecord ====================

    override suspend fun createReturnExchange(record: ReturnExchangeRecord) {
        commerceDao.insertReturnExchangeRecord(record.toEntity())
    }

    override suspend fun getReturnExchangesForOrder(orderId: String): List<ReturnExchangeRecord> =
        commerceDao.getReturnExchangeRecordsByOriginalOrderId(orderId).map { it.toDomain() }

    // ==================== Entity <-> Domain Mapping ====================

    private fun PaymentRecordEntity.toDomain() = PaymentRecord(
        id = id,
        orderId = orderId,
        amount = BigDecimal(amount),
        tenderType = TenderType.valueOf(tenderType),
        status = PaymentStatus.valueOf(status),
        externalReference = externalReference,
        receivedBy = receivedBy,
        receivedAt = Instant.ofEpochMilli(receivedAt),
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
        version = version
    )

    private fun PaymentRecord.toEntity() = PaymentRecordEntity(
        id = id,
        orderId = orderId,
        amount = amount.toPlainString(),
        tenderType = tenderType.name,
        status = status.name,
        externalReference = externalReference,
        receivedBy = receivedBy,
        receivedAt = receivedAt.toEpochMilli(),
        notes = notes,
        createdAt = createdAt.toEpochMilli(),
        version = version
    )

    private fun PaymentAllocationEntity.toDomain() = PaymentAllocation(
        id = id,
        paymentId = paymentId,
        orderId = orderId,
        amount = BigDecimal(amount),
        allocatedAt = Instant.ofEpochMilli(allocatedAt)
    )

    private fun PaymentAllocation.toEntity() = PaymentAllocationEntity(
        id = id,
        paymentId = paymentId,
        orderId = orderId,
        amount = amount.toPlainString(),
        allocatedAt = allocatedAt.toEpochMilli()
    )

    private fun RefundRecordEntity.toDomain() = RefundRecord(
        id = id,
        orderId = orderId,
        paymentId = paymentId,
        learnerId = learnerId,
        amount = BigDecimal(amount),
        reason = reason,
        refundMethod = TenderType.valueOf(refundMethod),
        externalReference = externalReference,
        processedBy = processedBy,
        processedAt = Instant.ofEpochMilli(processedAt),
        overrideUsed = overrideUsed,
        overrideNote = overrideNote,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun RefundRecord.toEntity() = RefundRecordEntity(
        id = id,
        orderId = orderId,
        paymentId = paymentId,
        learnerId = learnerId,
        amount = amount.toPlainString(),
        reason = reason,
        refundMethod = refundMethod.name,
        externalReference = externalReference,
        processedBy = processedBy,
        processedAt = processedAt.toEpochMilli(),
        overrideUsed = overrideUsed,
        overrideNote = overrideNote,
        createdAt = createdAt.toEpochMilli()
    )

    private fun LedgerEntryEntity.toDomain() = LedgerEntry(
        id = id,
        orderId = orderId,
        paymentId = paymentId,
        refundId = refundId,
        entryType = LedgerEntryType.valueOf(entryType),
        amount = BigDecimal(amount),
        description = description,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun LedgerEntry.toEntity() = LedgerEntryEntity(
        id = id,
        orderId = orderId,
        paymentId = paymentId,
        refundId = refundId,
        entryType = entryType.name,
        amount = amount.toPlainString(),
        description = description,
        createdAt = createdAt.toEpochMilli()
    )

    private fun FulfillmentRecordEntity.toDomain() = FulfillmentRecord(
        id = id,
        orderId = orderId,
        fulfilledBy = fulfilledBy,
        fulfilledAt = Instant.ofEpochMilli(fulfilledAt),
        notes = notes
    )

    private fun FulfillmentRecord.toEntity() = FulfillmentRecordEntity(
        id = id,
        orderId = orderId,
        fulfilledBy = fulfilledBy,
        fulfilledAt = fulfilledAt.toEpochMilli(),
        notes = notes
    )

    private fun DeliveryConfirmationEntity.toDomain() = DeliveryConfirmation(
        id = id,
        orderId = orderId,
        deliveryType = DeliveryType.valueOf(deliveryType),
        confirmedBy = confirmedBy,
        confirmedAt = Instant.ofEpochMilli(confirmedAt),
        notes = notes
    )

    private fun DeliveryConfirmation.toEntity() = DeliveryConfirmationEntity(
        id = id,
        orderId = orderId,
        deliveryType = deliveryType.name,
        confirmedBy = confirmedBy,
        confirmedAt = confirmedAt.toEpochMilli(),
        notes = notes
    )

    private fun ReturnExchangeRecordEntity.toDomain() = ReturnExchangeRecord(
        id = id,
        originalOrderId = originalOrderId,
        replacementOrderId = replacementOrderId,
        recordType = ReturnExchangeType.valueOf(recordType),
        reason = reason,
        processedBy = processedBy,
        processedAt = Instant.ofEpochMilli(processedAt),
        notes = notes
    )

    private fun ReturnExchangeRecord.toEntity() = ReturnExchangeRecordEntity(
        id = id,
        originalOrderId = originalOrderId,
        replacementOrderId = replacementOrderId,
        recordType = recordType.name,
        reason = reason,
        processedBy = processedBy,
        processedAt = processedAt.toEpochMilli(),
        notes = notes
    )
}
