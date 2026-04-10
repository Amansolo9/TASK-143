package com.learnmart.app.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.learnmart.app.data.local.LearnMartRoomDatabase
import com.learnmart.app.data.local.dao.AssessmentDao
import com.learnmart.app.data.local.dao.OperationsDao
import com.learnmart.app.data.local.dao.AuditDao
import com.learnmart.app.data.local.dao.BlacklistDao
import com.learnmart.app.data.local.dao.CommerceDao
import com.learnmart.app.data.local.dao.CourseDao
import com.learnmart.app.data.local.dao.EnrollmentDao
import com.learnmart.app.data.local.dao.PaymentDao
import com.learnmart.app.data.local.dao.PolicyDao
import com.learnmart.app.data.local.dao.RoleDao
import com.learnmart.app.data.local.dao.SessionDao
import com.learnmart.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "DatabaseModule"
    private const val KEYSTORE_ALIAS = "learnmart_db_key"
    private const val PREFS_NAME = "learnmart_db_config"
    private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_db_passphrase"
    private const val PREF_PASSPHRASE_IV = "db_passphrase_iv"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LearnMartRoomDatabase {
        val passphrase = SQLiteDatabase.getBytes(getOrCreateDatabaseKey(context).toCharArray())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            LearnMartRoomDatabase::class.java,
            "learnmart_encrypted.db"
        )
            .openHelperFactory(factory)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    // This callback fires if a destructive migration were to occur.
                    // We do NOT enable destructive migration on downgrade. If a
                    // schema downgrade is detected, Room will throw an
                    // IllegalStateException rather than silently dropping tables.
                    // Operator action: restore the database from a backup that
                    // matches the expected schema version, or run the matching
                    // app version.
                    Log.w(
                        TAG,
                        "Destructive migration triggered. Data may have been lost. " +
                            "Restore from a recent backup if this was unexpected."
                    )
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Log.d(TAG, "Database opened, version: ${db.version}")
                }
            })
            .build()
    }

    /**
     * Retrieves or creates a database encryption passphrase backed by Android Keystore.
     *
     * On first launch a random passphrase is generated, encrypted with an AES key held
     * in Android Keystore, and the cipher-text + IV are persisted in SharedPreferences.
     * On subsequent launches the cipher-text is decrypted using the same Keystore key.
     *
     * **Production gating**: The fallback path is only permitted when the app is
     * running in a debuggable build or under a test instrumentation runner. Release
     * builds throw immediately if the Keystore is unavailable, preventing the app
     * from silently downgrading to an insecure SharedPreferences-only key.
     */
    private fun getOrCreateDatabaseKey(context: Context): String {
        return try {
            getOrCreateKeystoreBackedKey(context)
        } catch (e: Exception) {
            // Only allow the insecure fallback in debug / test builds.
            if (!isTestOrDebugEnvironment(context)) {
                throw IllegalStateException(
                    "Android Keystore is required for database encryption in production builds. " +
                        "Cannot fall back to insecure derived key. Cause: ${e.message}",
                    e
                )
            }
            Log.w(
                TAG,
                "Android Keystore unavailable; falling back to derived key. " +
                    "This is permitted ONLY in debug/test environments. Error: ${e.message}"
            )
            getFallbackDerivedKey(context)
        }
    }

    /**
     * Returns true when the current build is debuggable or running under an
     * Android test instrumentation runner. Release builds return false, which
     * causes the caller to refuse the insecure fallback.
     */
    private fun isTestOrDebugEnvironment(context: Context): Boolean {
        // Check debuggable flag set by buildType in Gradle
        val isDebuggable = (context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) return true

        // Detect test instrumentation runner (Robolectric, AndroidJUnitRunner)
        return try {
            Class.forName("org.robolectric.RobolectricTestRunner")
            true
        } catch (_: ClassNotFoundException) {
            try {
                Class.forName("androidx.test.runner.AndroidJUnitRunner")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    private fun getOrCreateKeystoreBackedKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existingEncrypted = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val existingIv = prefs.getString(PREF_PASSPHRASE_IV, null)

        if (existingEncrypted != null && existingIv != null) {
            // Decrypt the stored passphrase using the Keystore key
            val keystoreKey = getKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val iv = Base64.decode(existingIv, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decrypted = cipher.doFinal(Base64.decode(existingEncrypted, Base64.NO_WRAP))
            return String(decrypted, Charsets.UTF_8)
        }

        // First launch: generate a random passphrase, encrypt it, and store it
        val rawPassphrase = java.util.UUID.randomUUID().toString() +
            java.util.UUID.randomUUID().toString()

        val keystoreKey = getOrCreateKeystoreAesKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)

        val encryptedBytes = cipher.doFinal(rawPassphrase.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        prefs.edit()
            .putString(PREF_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
            .putString(PREF_PASSPHRASE_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return rawPassphrase
    }

    private fun getKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Keystore key '$KEYSTORE_ALIAS' not found")
        return entry.secretKey
    }

    private fun getOrCreateKeystoreAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Fallback for environments where Android Keystore is not available (e.g. Docker,
     * Robolectric). Only reachable in debug/test builds — [getOrCreateDatabaseKey]
     * throws in release builds before reaching this path.
     *
     * Derives a key from the package name + a per-install random salt so it is not
     * trivially predictable, but this is NOT equivalent to Keystore-backed encryption.
     */
    private fun getFallbackDerivedKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingKey = prefs.getString("fallback_db_key_v2", null)
        if (existingKey != null) return existingKey

        // Derive from package + random salt so different apps/installs diverge
        val salt = java.util.UUID.randomUUID().toString()
        val raw = "${context.packageName}:$salt:${java.util.UUID.randomUUID()}"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val key = digest.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        prefs.edit().putString("fallback_db_key_v2", key).apply()
        Log.w(TAG, "DEBUG/TEST-ONLY fallback DB key generated. Not for production use.")
        return key
    }

    @Provides
    fun provideUserDao(database: LearnMartRoomDatabase): UserDao = database.userDao()

    @Provides
    fun provideRoleDao(database: LearnMartRoomDatabase): RoleDao = database.roleDao()

    @Provides
    fun provideSessionDao(database: LearnMartRoomDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideAuditDao(database: LearnMartRoomDatabase): AuditDao = database.auditDao()

    @Provides
    fun providePolicyDao(database: LearnMartRoomDatabase): PolicyDao = database.policyDao()

    @Provides
    fun provideBlacklistDao(database: LearnMartRoomDatabase): BlacklistDao = database.blacklistDao()

    @Provides
    fun provideCourseDao(database: LearnMartRoomDatabase): CourseDao = database.courseDao()

    @Provides
    fun provideEnrollmentDao(database: LearnMartRoomDatabase): EnrollmentDao = database.enrollmentDao()

    @Provides
    fun provideCommerceDao(database: LearnMartRoomDatabase): CommerceDao = database.commerceDao()

    @Provides
    fun providePaymentDao(database: LearnMartRoomDatabase): PaymentDao = database.paymentDao()

    @Provides
    fun provideAssessmentDao(database: LearnMartRoomDatabase): AssessmentDao = database.assessmentDao()

    @Provides
    fun provideOperationsDao(database: LearnMartRoomDatabase): OperationsDao = database.operationsDao()
}
