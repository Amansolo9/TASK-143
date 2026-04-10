package com.learnmart.app.di

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SqlDelightModule {

    @Provides
    @Singleton
    @Named("auditSqlDriver")
    fun provideAuditSqlDriver(@ApplicationContext context: Context): SqlDriver {
        // Empty schema - we manage tables via raw SQL below
        val emptySchema = object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 1
            override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Value(Unit)
            override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): QueryResult.Value<Unit> = QueryResult.Value(Unit)
        }
        val driver = AndroidSqliteDriver(
            schema = emptySchema,
            context = context,
            name = "learnmart_queries.db"
        )
        // Create audit table if not exists
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS audit_events (
                id TEXT NOT NULL PRIMARY KEY,
                actor_id TEXT,
                actor_username TEXT,
                action_type TEXT NOT NULL,
                target_entity_type TEXT,
                target_entity_id TEXT,
                before_summary TEXT,
                after_summary TEXT,
                reason TEXT,
                session_id TEXT,
                outcome TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                metadata TEXT
            )
        """.trimIndent(), 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_events(timestamp)", 0)
        driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_events(actor_id)", 0)
        return driver
    }
}
