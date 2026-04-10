package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.CommerceDao
import com.learnmart.app.data.local.entity.InventoryItemEntity
import com.learnmart.app.data.local.entity.InventoryLockEntity
import com.learnmart.app.domain.model.InventoryItem
import com.learnmart.app.domain.model.InventoryLock
import com.learnmart.app.domain.model.InventoryLockStatus
import com.learnmart.app.domain.repository.InventoryRepository
import com.learnmart.app.util.TimeUtils
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val commerceDao: CommerceDao
) : InventoryRepository {

    override suspend fun createItem(item: InventoryItem): InventoryItem {
        val entity = item.toEntity()
        commerceDao.insertInventoryItem(entity)
        return entity.toDomain()
    }

    override suspend fun updateItem(item: InventoryItem): Boolean {
        val existing = commerceDao.getInventoryItemById(item.id) ?: return false
        commerceDao.updateInventoryItem(item.toEntity())
        return true
    }

    override suspend fun getItemById(id: String): InventoryItem? =
        commerceDao.getInventoryItemById(id)?.toDomain()

    override suspend fun getItemByMaterialId(materialId: String): InventoryItem? =
        commerceDao.getInventoryItemByMaterialId(materialId)?.toDomain()

    override suspend fun getItemBySku(sku: String): InventoryItem? =
        commerceDao.getInventoryItemBySku(sku)?.toDomain()

    override suspend fun adjustReservedStock(id: String, delta: Int, currentVersion: Int): Boolean {
        val rows = commerceDao.adjustReservedStock(id, delta, currentVersion)
        return rows > 0
    }

    override suspend fun acquireLock(lock: InventoryLock): InventoryLock {
        val entity = lock.toEntity()
        commerceDao.insertInventoryLock(entity)
        return entity.toDomain()
    }

    override suspend fun releaseLock(lockId: String, reason: String) {
        val existing = commerceDao.getInventoryLockById(lockId) ?: return
        val updated = existing.copy(
            status = InventoryLockStatus.RELEASED.name,
            releasedAt = TimeUtils.nowUtc().toEpochMilli()
        )
        commerceDao.updateInventoryLock(updated)
    }

    override suspend fun consumeLock(lockId: String) {
        commerceDao.updateInventoryLockStatus(lockId, InventoryLockStatus.CONSUMED.name)
    }

    override suspend fun getActiveLocksByInventoryItem(inventoryItemId: String): List<InventoryLock> =
        commerceDao.getActiveInventoryLocksByInventoryItemId(inventoryItemId).map { it.toDomain() }

    override suspend fun getLocksByOrderId(orderId: String): List<InventoryLock> =
        commerceDao.getInventoryLocksByOrderId(orderId).map { it.toDomain() }

    override suspend fun getExpiredLocks(): List<InventoryLock> {
        val now = TimeUtils.nowUtc().toEpochMilli()
        return commerceDao.getExpiredInventoryLocks(now).map { it.toDomain() }
    }

    // ==================== Entity <-> Domain Mapping ====================

    private fun InventoryItemEntity.toDomain() = InventoryItem(
        id = id,
        materialId = materialId,
        sku = sku,
        totalStock = totalStock,
        reservedStock = reservedStock,
        availableStock = availableStock,
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun InventoryItem.toEntity() = InventoryItemEntity(
        id = id,
        materialId = materialId,
        sku = sku,
        totalStock = totalStock,
        reservedStock = reservedStock,
        availableStock = availableStock,
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    private fun InventoryLockEntity.toDomain() = InventoryLock(
        id = id,
        inventoryItemId = inventoryItemId,
        orderId = orderId,
        cartId = cartId,
        quantity = quantity,
        status = InventoryLockStatus.valueOf(status),
        acquiredAt = Instant.ofEpochMilli(acquiredAt),
        expiresAt = Instant.ofEpochMilli(expiresAt),
        releasedAt = releasedAt?.let { Instant.ofEpochMilli(it) }
    )

    private fun InventoryLock.toEntity() = InventoryLockEntity(
        id = id,
        inventoryItemId = inventoryItemId,
        orderId = orderId,
        cartId = cartId,
        quantity = quantity,
        status = status.name,
        acquiredAt = acquiredAt.toEpochMilli(),
        expiresAt = expiresAt.toEpochMilli(),
        releasedAt = releasedAt?.toEpochMilli()
    )
}
