package com.learnmart.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "carts",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"])
    ]
)
data class CartEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "checkout_policy")
    val checkoutPolicy: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "cart_line_items",
    foreignKeys = [
        ForeignKey(
            entity = CartEntity::class,
            parentColumns = ["id"],
            childColumns = ["cart_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cart_id"])
    ]
)
data class CartLineItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "cart_id")
    val cartId: String,
    @ColumnInfo(name = "item_type")
    val itemType: String,
    @ColumnInfo(name = "reference_id")
    val referenceId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String? = null,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "quantity")
    val quantity: Int,
    @ColumnInfo(name = "unit_price")
    val unitPrice: String,
    @ColumnInfo(name = "line_total")
    val lineTotal: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "quote_snapshots",
    indices = [
        Index(value = ["cart_id"])
    ]
)
data class QuoteSnapshotEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "cart_id")
    val cartId: String,
    @ColumnInfo(name = "subtotal")
    val subtotal: String,
    @ColumnInfo(name = "discount_total")
    val discountTotal: String,
    @ColumnInfo(name = "tax_amount")
    val taxAmount: String,
    @ColumnInfo(name = "service_fee")
    val serviceFee: String,
    @ColumnInfo(name = "packaging_fee")
    val packagingFee: String,
    @ColumnInfo(name = "grand_total")
    val grandTotal: String,
    @ColumnInfo(name = "tax_rate")
    val taxRate: String,
    @ColumnInfo(name = "service_fee_rate")
    val serviceFeeRate: String,
    @ColumnInfo(name = "quoted_at")
    val quotedAt: Long,
    @ColumnInfo(name = "valid_until")
    val validUntil: Long
)

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "created_at"]),
        Index(value = ["status"])
    ]
)
data class OrderEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "idempotency_token")
    val idempotencyToken: String? = null,
    @ColumnInfo(name = "subtotal")
    val subtotal: String,
    @ColumnInfo(name = "discount_total")
    val discountTotal: String,
    @ColumnInfo(name = "tax_amount")
    val taxAmount: String,
    @ColumnInfo(name = "service_fee")
    val serviceFee: String,
    @ColumnInfo(name = "packaging_fee")
    val packagingFee: String,
    @ColumnInfo(name = "grand_total")
    val grandTotal: String,
    @ColumnInfo(name = "paid_amount")
    val paidAmount: String,
    @ColumnInfo(name = "placed_at")
    val placedAt: Long? = null,
    @ColumnInfo(name = "cancelled_at")
    val cancelledAt: Long? = null,
    @ColumnInfo(name = "cancel_reason")
    val cancelReason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

@Entity(
    tableName = "order_line_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"])
    ]
)
data class OrderLineItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "item_type")
    val itemType: String,
    @ColumnInfo(name = "reference_id")
    val referenceId: String,
    @ColumnInfo(name = "class_offering_id")
    val classOfferingId: String? = null,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "quantity")
    val quantity: Int,
    @ColumnInfo(name = "unit_price")
    val unitPrice: String,
    @ColumnInfo(name = "line_total")
    val lineTotal: String
)

@Entity(
    tableName = "order_price_components",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"])
    ]
)
data class OrderPriceComponentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "component_type")
    val componentType: String,
    @ColumnInfo(name = "amount")
    val amount: String,
    @ColumnInfo(name = "rate")
    val rate: String? = null,
    @ColumnInfo(name = "description")
    val description: String
)

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["material_id"]),
        Index(value = ["sku"], unique = true)
    ]
)
data class InventoryItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "material_id")
    val materialId: String,
    @ColumnInfo(name = "sku")
    val sku: String,
    @ColumnInfo(name = "total_stock")
    val totalStock: Int,
    @ColumnInfo(name = "reserved_stock")
    val reservedStock: Int,
    @ColumnInfo(name = "available_stock")
    val availableStock: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

@Entity(
    tableName = "inventory_locks",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventory_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["inventory_item_id", "expires_at", "status"]),
        Index(value = ["order_id"])
    ]
)
data class InventoryLockEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "inventory_item_id")
    val inventoryItemId: String,
    @ColumnInfo(name = "order_id")
    val orderId: String? = null,
    @ColumnInfo(name = "cart_id")
    val cartId: String? = null,
    @ColumnInfo(name = "quantity")
    val quantity: Int,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "acquired_at")
    val acquiredAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    @ColumnInfo(name = "released_at")
    val releasedAt: Long? = null
)

@Entity(
    tableName = "fulfillment_records",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"])
    ]
)
data class FulfillmentRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "fulfilled_by")
    val fulfilledBy: String,
    @ColumnInfo(name = "fulfilled_at")
    val fulfilledAt: Long,
    @ColumnInfo(name = "notes")
    val notes: String? = null
)

@Entity(
    tableName = "delivery_confirmations",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"])
    ]
)
data class DeliveryConfirmationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "delivery_type")
    val deliveryType: String,
    @ColumnInfo(name = "confirmed_by")
    val confirmedBy: String,
    @ColumnInfo(name = "confirmed_at")
    val confirmedAt: Long,
    @ColumnInfo(name = "notes")
    val notes: String? = null
)

@Entity(
    tableName = "return_exchange_records",
    indices = [
        Index(value = ["original_order_id"])
    ]
)
data class ReturnExchangeRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "original_order_id")
    val originalOrderId: String,
    @ColumnInfo(name = "replacement_order_id")
    val replacementOrderId: String? = null,
    @ColumnInfo(name = "record_type")
    val recordType: String,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "processed_by")
    val processedBy: String,
    @ColumnInfo(name = "processed_at")
    val processedAt: Long,
    @ColumnInfo(name = "notes")
    val notes: String? = null
)

@Entity(
    tableName = "payment_records",
    indices = [
        Index(value = ["order_id", "created_at"])
    ]
)
data class PaymentRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "amount")
    val amount: String,
    @ColumnInfo(name = "tender_type")
    val tenderType: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "external_reference")
    val externalReference: String? = null,
    @ColumnInfo(name = "received_by")
    val receivedBy: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "version")
    val version: Int
)

@Entity(
    tableName = "payment_allocations",
    indices = [
        Index(value = ["payment_id"]),
        Index(value = ["order_id"])
    ]
)
data class PaymentAllocationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "payment_id")
    val paymentId: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "amount")
    val amount: String,
    @ColumnInfo(name = "allocated_at")
    val allocatedAt: Long
)

@Entity(
    tableName = "refund_records",
    indices = [
        Index(value = ["learner_id", "created_at"])
    ]
)
data class RefundRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String,
    @ColumnInfo(name = "payment_id")
    val paymentId: String,
    @ColumnInfo(name = "learner_id")
    val learnerId: String,
    @ColumnInfo(name = "amount")
    val amount: String,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "refund_method")
    val refundMethod: String,
    @ColumnInfo(name = "external_reference")
    val externalReference: String? = null,
    @ColumnInfo(name = "processed_by")
    val processedBy: String,
    @ColumnInfo(name = "processed_at")
    val processedAt: Long,
    @ColumnInfo(name = "override_used")
    val overrideUsed: Boolean,
    @ColumnInfo(name = "override_note")
    val overrideNote: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["created_at"])
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "order_id")
    val orderId: String? = null,
    @ColumnInfo(name = "payment_id")
    val paymentId: String? = null,
    @ColumnInfo(name = "refund_id")
    val refundId: String? = null,
    @ColumnInfo(name = "entry_type")
    val entryType: String,
    @ColumnInfo(name = "amount")
    val amount: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "idempotency_tokens",
    indices = [
        Index(value = ["expires_at"])
    ]
)
data class IdempotencyTokenEntity(
    @PrimaryKey
    val token: String,
    @ColumnInfo(name = "request_hash")
    val requestHash: String,
    @ColumnInfo(name = "result_reference")
    val resultReference: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)
