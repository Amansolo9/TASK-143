package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun createUser(
        username: String,
        displayName: String,
        credential: String,
        credentialType: String
    ): User

    suspend fun getUserById(id: String): User?
    suspend fun getUserByUsername(username: String): User?
    fun getAllActiveUsers(): Flow<List<User>>
    suspend fun getAllUsersPaged(limit: Int, offset: Int): List<User>
    suspend fun updateUser(user: User): Boolean
    suspend fun updateLoginAttempts(userId: String, attempts: Int, lockedUntil: java.time.Instant?, currentVersion: Int): Boolean
    suspend fun recordSuccessfulLogin(userId: String)
    suspend fun updateStatus(userId: String, status: String, currentVersion: Int): Boolean
    suspend fun searchUsers(query: String): List<User>
    suspend fun verifyCredential(userId: String, credential: String): Boolean
}
