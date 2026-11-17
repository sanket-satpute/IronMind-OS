package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration26To27Test {

    private lateinit var context: Context
    private val dbName = "migration_26_27_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun testRealMigration26To27_createsAppSessionStateAndSyncWorkerLeasesTables() = runBlocking {
        // Step 1: Initialize database at v26
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(26) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `outbox` (
                            `operationId` TEXT NOT NULL,
                            `entityType` TEXT NOT NULL,
                            `entityId` TEXT NOT NULL,
                            `operationType` TEXT NOT NULL,
                            `payload` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `retryCount` INTEGER NOT NULL,
                            `lastAttemptAt` INTEGER,
                            `status` TEXT NOT NULL,
                            `userId` TEXT,
                            `baseVersion` INTEGER,
                            `leaseToken` TEXT,
                            `leaseExpiresAt` INTEGER,
                            PRIMARY KEY(`operationId`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val rawDb = helper.writableDatabase

        // Step 2: Execute MIGRATION_26_27
        IronMindDatabase.MIGRATION_26_27.migrate(rawDb)

        // Step 3: Verify app_session_state table exists and has expected schema
        val sessionCursor = rawDb.query("PRAGMA table_info(`app_session_state`)")
        val sessionColumns = mutableMapOf<String, String>()
        while (sessionCursor.moveToNext()) {
            val name = sessionCursor.getString(sessionCursor.getColumnIndexOrThrow("name"))
            val type = sessionCursor.getString(sessionCursor.getColumnIndexOrThrow("type"))
            sessionColumns[name] = type
        }
        sessionCursor.close()

        assertTrue("app_session_state table must have id column", sessionColumns.containsKey("id"))
        assertTrue("app_session_state table must have activeUserId column", sessionColumns.containsKey("activeUserId"))
        assertTrue("app_session_state table must have currentGeneration column", sessionColumns.containsKey("currentGeneration"))
        assertTrue("app_session_state table must have updatedAt column", sessionColumns.containsKey("updatedAt"))

        // Step 4: Verify sync_worker_leases table exists and has expected schema
        val leaseCursor = rawDb.query("PRAGMA table_info(`sync_worker_leases`)")
        val leaseColumns = mutableMapOf<String, String>()
        while (leaseCursor.moveToNext()) {
            val name = leaseCursor.getString(leaseCursor.getColumnIndexOrThrow("name"))
            val type = leaseCursor.getString(leaseCursor.getColumnIndexOrThrow("type"))
            leaseColumns[name] = type
        }
        leaseCursor.close()

        assertTrue("sync_worker_leases table must have leaseKey", leaseColumns.containsKey("leaseKey"))
        assertTrue("sync_worker_leases table must have ownerToken", leaseColumns.containsKey("ownerToken"))
        assertTrue("sync_worker_leases table must have sessionGeneration", leaseColumns.containsKey("sessionGeneration"))
        assertTrue("sync_worker_leases table must have generationId", leaseColumns.containsKey("generationId"))
        assertTrue("sync_worker_leases table must have cursor", leaseColumns.containsKey("cursor"))
        assertTrue("sync_worker_leases table must have status", leaseColumns.containsKey("status"))
        assertTrue("sync_worker_leases table must have acquiredAt", leaseColumns.containsKey("acquiredAt"))
        assertTrue("sync_worker_leases table must have expiresAt", leaseColumns.containsKey("expiresAt"))
        assertTrue("sync_worker_leases table must have updatedAt", leaseColumns.containsKey("updatedAt"))
        assertTrue("sync_worker_leases table must have lastCompletedAt", leaseColumns.containsKey("lastCompletedAt"))

        // Step 5: Verify indices
        val indexCursor = rawDb.query("PRAGMA index_list(`sync_worker_leases`)")
        val indices = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()

        assertTrue("Must contain index_sync_worker_leases_sessionGeneration", indices.contains("index_sync_worker_leases_sessionGeneration"))
        assertTrue("Must contain index_sync_worker_leases_expiresAt", indices.contains("index_sync_worker_leases_expiresAt"))

        rawDb.close()
        helper.close()
    }
}

