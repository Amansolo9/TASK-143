package com.learnmart.app.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Room's [withTransaction] extension to allow
 * unit-test mocking without requiring mockkStatic on Room internals.
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: LearnMartRoomDatabase
) : TransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return database.withTransaction { block() }
    }
}
