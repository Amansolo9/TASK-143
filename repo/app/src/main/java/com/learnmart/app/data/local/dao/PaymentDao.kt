package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.learnmart.app.data.local.entity.LedgerEntryEntity
import com.learnmart.app.data.local.entity.PaymentAllocationEntity
import com.learnmart.app.data.local.entity.PaymentRecordEntity
import com.learnmart.app.data.local.entity.RefundRecordEntity

@Dao
interface PaymentDao {

    // ──────────────────────────────────────────────
    // PaymentRecord
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPaymentRecord(record: PaymentRecordEntity)

    @Update
    suspend fun updatePaymentRecord(record: PaymentRecordEntity)

    @Query("SELECT * FROM payment_records WHERE id = :id")
    suspend fun getPaymentRecordById(id: String): PaymentRecordEntity?

    @Query("SELECT * FROM payment_records WHERE order_id = :orderId ORDER BY created_at DESC")
    suspend fun getPaymentRecordsByOrderId(orderId: String): List<PaymentRecordEntity>

    @Query("""
        UPDATE payment_records SET status = :status, version = version + 1
        WHERE id = :id AND version = :currentVersion
    """)
    suspend fun updatePaymentRecordStatus(id: String, status: String, currentVersion: Int): Int

    @Query("SELECT * FROM payment_records ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaymentRecordsPaged(limit: Int, offset: Int): List<PaymentRecordEntity>

    // ──────────────────────────────────────────────
    // PaymentAllocation
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPaymentAllocation(allocation: PaymentAllocationEntity)

    @Query("SELECT * FROM payment_allocations WHERE payment_id = :paymentId")
    suspend fun getPaymentAllocationsByPaymentId(paymentId: String): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations WHERE order_id = :orderId")
    suspend fun getPaymentAllocationsByOrderId(orderId: String): List<PaymentAllocationEntity>

    @Query("SELECT COALESCE(SUM(amount), '0') FROM payment_allocations WHERE order_id = :orderId")
    suspend fun sumAllocatedAmountForOrder(orderId: String): String

    // ──────────────────────────────────────────────
    // RefundRecord
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRefundRecord(record: RefundRecordEntity)

    @Query("SELECT * FROM refund_records WHERE order_id = :orderId")
    suspend fun getRefundRecordsByOrderId(orderId: String): List<RefundRecordEntity>

    @Query("SELECT * FROM refund_records WHERE learner_id = :learnerId ORDER BY created_at DESC")
    suspend fun getRefundRecordsByLearnerId(learnerId: String): List<RefundRecordEntity>

    @Query("""
        SELECT COUNT(*) FROM refund_records
        WHERE learner_id = :learnerId AND created_at >= :dayStart AND created_at < :dayEnd
    """)
    suspend fun countRefundsForLearnerOnDate(learnerId: String, dayStart: Long, dayEnd: Long): Int

    @Query("SELECT * FROM refund_records WHERE payment_id = :paymentId")
    suspend fun getRefundRecordsByPaymentId(paymentId: String): List<RefundRecordEntity>

    // ──────────────────────────────────────────────
    // LedgerEntry
    // ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntry(entry: LedgerEntryEntity)

    @Query("SELECT * FROM ledger_entries WHERE order_id = :orderId ORDER BY created_at ASC")
    suspend fun getLedgerEntriesByOrderId(orderId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllLedgerEntriesPaged(limit: Int, offset: Int): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE payment_id = :paymentId ORDER BY created_at ASC")
    suspend fun getLedgerEntriesByPaymentId(paymentId: String): List<LedgerEntryEntity>
}
