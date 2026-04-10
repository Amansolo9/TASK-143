package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.UserDao
import com.learnmart.app.data.local.entity.UserEntity
import com.learnmart.app.domain.model.CredentialType
import com.learnmart.app.domain.model.User
import com.learnmart.app.domain.model.UserStatus
import com.learnmart.app.domain.repository.UserRepository
import com.learnmart.app.security.CredentialManager
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val credentialManager: CredentialManager
) : UserRepository {

    override suspend fun createUser(
        username: String,
        displayName: String,
        credential: String,
        credentialType: String
    ): User {
        val salt = credentialManager.generateSalt()
        val hash = credentialManager.hashCredential(credential, salt)
        val now = TimeUtils.nowUtc()

        val entity = UserEntity(
            id = IdGenerator.newId(),
            username = username,
            displayName = displayName,
            credentialHash = credentialManager.hashToHex(hash),
            credentialSalt = credentialManager.saltToHex(salt),
            credentialType = credentialType,
            status = UserStatus.ACTIVE.name,
            failedLoginAttempts = 0,
            lockedUntil = null,
            lastLoginAt = null,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
            version = 1
        )

        userDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun getUserById(id: String): User? =
        userDao.getById(id)?.toDomain()

    override suspend fun getUserByUsername(username: String): User? =
        userDao.getByUsername(username)?.toDomain()

    override fun getAllActiveUsers(): Flow<List<User>> =
        userDao.getAllActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllUsersPaged(limit: Int, offset: Int): List<User> =
        userDao.getAllPaged(limit, offset).map { it.toDomain() }

    override suspend fun updateUser(user: User): Boolean {
        val existing = userDao.getById(user.id) ?: return false
        val updated = existing.copy(
            displayName = user.displayName,
            status = user.status.name,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            version = existing.version + 1
        )
        userDao.update(updated)
        return true
    }

    override suspend fun updateLoginAttempts(
        userId: String,
        attempts: Int,
        lockedUntil: Instant?,
        currentVersion: Int
    ): Boolean {
        val rows = userDao.updateLoginAttempts(
            userId = userId,
            attempts = attempts,
            lockedUntil = lockedUntil?.toEpochMilli(),
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            currentVersion = currentVersion
        )
        return rows > 0
    }

    override suspend fun recordSuccessfulLogin(userId: String) {
        val now = TimeUtils.nowUtc().toEpochMilli()
        userDao.recordSuccessfulLogin(userId, now, now)
    }

    override suspend fun updateStatus(userId: String, status: String, currentVersion: Int): Boolean {
        val rows = userDao.updateStatus(userId, status, TimeUtils.nowUtc().toEpochMilli(), currentVersion)
        return rows > 0
    }

    override suspend fun searchUsers(query: String): List<User> =
        userDao.search(query).map { it.toDomain() }

    override suspend fun verifyCredential(userId: String, credential: String): Boolean {
        val entity = userDao.getById(userId) ?: return false
        val salt = credentialManager.hexToSalt(entity.credentialSalt)
        val expectedHash = credentialManager.hexToHash(entity.credentialHash)
        return credentialManager.verifyCredential(credential, salt, expectedHash)
    }

    private fun UserEntity.toDomain() = User(
        id = id,
        username = username,
        displayName = displayName,
        credentialType = CredentialType.valueOf(credentialType),
        status = UserStatus.valueOf(status),
        failedLoginAttempts = failedLoginAttempts,
        lockedUntil = lockedUntil?.let { Instant.ofEpochMilli(it) },
        lastLoginAt = lastLoginAt?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )
}
