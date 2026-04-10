package com.learnmart.app.domain.model

import java.time.Instant

enum class UserStatus {
    ACTIVE,
    LOCKED,
    DISABLED,
    ARCHIVED
}

enum class CredentialType {
    PIN,
    PASSWORD
}

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val credentialType: CredentialType,
    val status: UserStatus,
    val failedLoginAttempts: Int,
    val lockedUntil: Instant?,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Int
)

data class SessionRecord(
    val id: String,
    val userId: String,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val expiresAt: Instant,
    val isActive: Boolean,
    val terminatedReason: String?
)

data class LoginRequest(
    val username: String,
    val credential: String
)

data class LoginResult(
    val user: User,
    val session: SessionRecord,
    val roles: List<RoleType>
)
