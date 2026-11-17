package com.sanket_satpute_20.ironmind.data.local

import android.content.ContentValues
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration27To28Test {

    private lateinit var context: Context
    private val dbName = "migration_27_28_test.db"

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
    fun testRealMigration27To28_createsCheckpointsTombstonesAndUpgradesDomainAndOutbox() = runBlocking {
        // Step 1: Initialize database at v27
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create base domain tables at v27 schema
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `goal` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `ambitionId` TEXT,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `why` TEXT NOT NULL,
                            `importance` INTEGER NOT NULL,
                            `status` TEXT NOT NULL,
                            `targetAt` INTEGER,
                            `startedAt` INTEGER,
                            `completedAt` INTEGER,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `schemaVersion` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `plan` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `goalId` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `startedAt` INTEGER,
                            `targetAt` INTEGER,
                            `completedAt` INTEGER,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `schemaVersion` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `task` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `planId` TEXT,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `priority` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `dueDate` INTEGER,
                            `estimatedMinutes` INTEGER NOT NULL,
                            `completedAt` INTEGER,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `schemaVersion` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `commitment` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `committedAt` INTEGER NOT NULL,
                            `completedAt` INTEGER,
                            `failedAt` INTEGER,
                            `updatedAt` INTEGER NOT NULL,
                            `schemaVersion` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `reflection` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `sentiment` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `memory` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `confidence` REAL NOT NULL,
                            `status` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `schemaVersion` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )

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

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                            `conflictId` TEXT NOT NULL,
                            `operationId` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `entityType` TEXT NOT NULL,
                            `entityId` TEXT NOT NULL,
                            `localPayload` TEXT NOT NULL,
                            `remotePayload` TEXT,
                            `conflictReason` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `resolutionStatus` TEXT NOT NULL,
                            `resolvedAt` INTEGER,
                            `resolvedBy` TEXT,
                            PRIMARY KEY(`conflictId`)
                        )
                        """.trimIndent()
                    )

                    // Seed pre-existing records to verify migration data retention and transformation
                    val goalValues = ContentValues().apply {
                        put("id", "goal-1")
                        put("userId", "user-123")
                        put("ambitionId", null as String?)
                        put("title", "Run Marathon")
                        put("description", "Train for 42km")
                        put("why", "Health")
                        put("importance", 5)
                        put("status", "ACTIVE")
                        put("createdAt", 1000L)
                        put("updatedAt", 1000L)
                        put("schemaVersion", 1)
                    }
                    db.insert("goal", SQLiteDatabaseConflictAlgorithm.NONE, goalValues)

                    // Seed outbox records:
                    // 1. Stalled SYNCING entry (should be reset to PENDING with null lease)
                    val outboxStalled = ContentValues().apply {
                        put("operationId", "op-stalled")
                        put("entityType", "Goal")
                        put("entityId", "goal-1")
                        put("operationType", "UPDATE")
                        put("payload", "{}")
                        put("createdAt", 1000L)
                        put("retryCount", 1)
                        put("status", "SYNCING")
                        put("userId", "user-123")
                        put("leaseToken", "token-abandoned")
                        put("leaseExpiresAt", 2000L)
                    }
                    db.insert("outbox", SQLiteDatabaseConflictAlgorithm.NONE, outboxStalled)

                    // Seed conflict records:
                    // Two unresolved conflicts for same entity: older one should become SUPERSEDED
                    val conflictOlder = ContentValues().apply {
                        put("conflictId", "conf-1")
                        put("operationId", "op-conf-1")
                        put("userId", "user-123")
                        put("entityType", "Goal")
                        put("entityId", "goal-1")
                        put("localPayload", "{}")
                        put("remotePayload", "{}")
                        put("conflictReason", "reason 1")
                        put("createdAt", 1000L)
                        put("updatedAt", 1000L)
                        put("resolutionStatus", "UNRESOLVED")
                    }
                    db.insert("sync_conflicts", SQLiteDatabaseConflictAlgorithm.NONE, conflictOlder)

                    val conflictNewer = ContentValues().apply {
                        put("conflictId", "conf-2")
                        put("operationId", "op-conf-2")
                        put("userId", "user-123")
                        put("entityType", "Goal")
                        put("entityId", "goal-1")
                        put("localPayload", "{}")
                        put("remotePayload", "{}")
                        put("conflictReason", "reason 2")
                        put("createdAt", 2000L)
                        put("updatedAt", 2000L)
                        put("resolutionStatus", "UNRESOLVED")
                    }
                    db.insert("sync_conflicts", SQLiteDatabaseConflictAlgorithm.NONE, conflictNewer)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val rawDb = helper.writableDatabase

        // Step 2: Execute MIGRATION_27_28
        IronMindDatabase.MIGRATION_27_28.migrate(rawDb)

        // Step 3: Verify sync_checkpoints table
        val cpCursor = rawDb.query("PRAGMA table_info(`sync_checkpoints`)")
        val cpColumns = mutableMapOf<String, String>()
        while (cpCursor.moveToNext()) {
            val name = cpCursor.getString(cpCursor.getColumnIndexOrThrow("name"))
            val type = cpCursor.getString(cpCursor.getColumnIndexOrThrow("type"))
            cpColumns[name] = type
        }
        cpCursor.close()

        assertTrue("sync_checkpoints must contain userId", cpColumns.containsKey("userId"))
        assertTrue("sync_checkpoints must contain entityType", cpColumns.containsKey("entityType"))
        assertTrue("sync_checkpoints must contain traversalType", cpColumns.containsKey("traversalType"))
        assertTrue("sync_checkpoints must contain lastTimestamp", cpColumns.containsKey("lastTimestamp"))
        assertTrue("sync_checkpoints must contain lastDocumentId", cpColumns.containsKey("lastDocumentId"))
        assertTrue("sync_checkpoints must contain cycleCount", cpColumns.containsKey("cycleCount"))
        assertTrue("sync_checkpoints must contain lastCycleCompletedAt", cpColumns.containsKey("lastCycleCompletedAt"))
        assertTrue("sync_checkpoints must contain updatedAt", cpColumns.containsKey("updatedAt"))

        // Step 4: Verify local_tombstones table and index
        val ltCursor = rawDb.query("PRAGMA table_info(`local_tombstones`)")
        val ltColumns = mutableMapOf<String, String>()
        while (ltCursor.moveToNext()) {
            val name = ltCursor.getString(ltCursor.getColumnIndexOrThrow("name"))
            ltColumns[name] = ltCursor.getString(ltCursor.getColumnIndexOrThrow("type"))
        }
        ltCursor.close()

        assertTrue("local_tombstones must contain userId", ltColumns.containsKey("userId"))
        assertTrue("local_tombstones must contain entityType", ltColumns.containsKey("entityType"))
        assertTrue("local_tombstones must contain entityId", ltColumns.containsKey("entityId"))
        assertTrue("local_tombstones must contain status", ltColumns.containsKey("status"))
        assertTrue("local_tombstones must contain deletionOperationId", ltColumns.containsKey("deletionOperationId"))
        assertTrue("local_tombstones must contain baseServerVersion", ltColumns.containsKey("baseServerVersion"))
        assertTrue("local_tombstones must contain canonicalServerVersion", ltColumns.containsKey("canonicalServerVersion"))
        assertTrue("local_tombstones must contain localRevision", ltColumns.containsKey("localRevision"))
        assertTrue("local_tombstones must contain deletedAt", ltColumns.containsKey("deletedAt"))
        assertTrue("local_tombstones must contain updatedAt", ltColumns.containsKey("updatedAt"))

        val ltIndexCursor = rawDb.query("PRAGMA index_list(`local_tombstones`)")
        val ltIndices = mutableListOf<String>()
        while (ltIndexCursor.moveToNext()) {
            ltIndices.add(ltIndexCursor.getString(ltIndexCursor.getColumnIndexOrThrow("name")))
        }
        ltIndexCursor.close()
        assertTrue("Must contain index_local_tombstones_userId", ltIndices.contains("index_local_tombstones_userId"))
        assertTrue("Must contain index_local_tombstones_deletedAt", ltIndices.contains("index_local_tombstones_deletedAt"))

        // Step 5: Verify domain tables have new columns and pre-existing data has defaults
        val goalInfoCursor = rawDb.query("PRAGMA table_info(`goal`)")
        val goalColumns = mutableMapOf<String, String>()
        while (goalInfoCursor.moveToNext()) {
            val name = goalInfoCursor.getString(goalInfoCursor.getColumnIndexOrThrow("name"))
            goalColumns[name] = goalInfoCursor.getString(goalInfoCursor.getColumnIndexOrThrow("type"))
        }
        goalInfoCursor.close()
        assertTrue("goal must have localRevision", goalColumns.containsKey("localRevision"))
        assertTrue("goal must have serverVersion", goalColumns.containsKey("serverVersion"))
        assertTrue("goal must have legacyBaseHash", goalColumns.containsKey("legacyBaseHash"))

        val goalDataCursor = rawDb.query("SELECT id, title, localRevision, serverVersion, legacyBaseHash FROM goal WHERE id = 'goal-1'")
        assertTrue(goalDataCursor.moveToFirst())
        assertEquals("goal-1", goalDataCursor.getString(0))
        assertEquals("Run Marathon", goalDataCursor.getString(1))
        assertEquals(1L, goalDataCursor.getLong(2))
        assertTrue(goalDataCursor.isNull(3))
        assertTrue(goalDataCursor.isNull(4))
        goalDataCursor.close()

        // Step 6: Verify outbox schema
        val outboxInfoCursor = rawDb.query("PRAGMA table_info(`outbox`)")
        val outboxColumns = mutableMapOf<String, String>()
        while (outboxInfoCursor.moveToNext()) {
            val name = outboxInfoCursor.getString(outboxInfoCursor.getColumnIndexOrThrow("name"))
            outboxColumns[name] = outboxInfoCursor.getString(outboxInfoCursor.getColumnIndexOrThrow("type"))
        }
        outboxInfoCursor.close()
        assertTrue("outbox must have baseServerVersion", outboxColumns.containsKey("baseServerVersion"))
        assertTrue("outbox must have uploadedLocalRevision", outboxColumns.containsKey("uploadedLocalRevision"))
        assertTrue("outbox must have chainedFrom", outboxColumns.containsKey("chainedFrom"))
        assertTrue("outbox must have legacyBaseHash", outboxColumns.containsKey("legacyBaseHash"))
        assertTrue("outbox must have leaseGeneration", outboxColumns.containsKey("leaseGeneration"))

        // Step 7: Verify stalled SYNCING entry was reset to PENDING with null lease
        val stalledCursor = rawDb.query("SELECT status, leaseToken, leaseExpiresAt, leaseGeneration FROM outbox WHERE operationId = 'op-stalled'")
        assertTrue(stalledCursor.moveToFirst())
        assertEquals("PENDING", stalledCursor.getString(0))
        assertTrue("leaseToken should be null", stalledCursor.isNull(1))
        assertTrue("leaseExpiresAt should be null", stalledCursor.isNull(2))
        assertTrue("leaseGeneration should be null", stalledCursor.isNull(3))
        stalledCursor.close()

        // Step 8: Verify duplicate sync_conflicts resolution (conf-1 -> SUPERSEDED, conf-2 -> UNRESOLVED)
        val conf1Cursor = rawDb.query("SELECT resolutionStatus FROM sync_conflicts WHERE conflictId = 'conf-1'")
        assertTrue(conf1Cursor.moveToFirst())
        assertEquals("SUPERSEDED", conf1Cursor.getString(0))
        conf1Cursor.close()

        val conf2Cursor = rawDb.query("SELECT resolutionStatus FROM sync_conflicts WHERE conflictId = 'conf-2'")
        assertTrue(conf2Cursor.moveToFirst())
        assertEquals("UNRESOLVED", conf2Cursor.getString(0))
        conf2Cursor.close()

        val conflictIndicesCursor = rawDb.query("PRAGMA index_list(`sync_conflicts`)")
        val conflictIndices = mutableListOf<String>()
        while (conflictIndicesCursor.moveToNext()) {
            conflictIndices.add(conflictIndicesCursor.getString(conflictIndicesCursor.getColumnIndexOrThrow("name")))
        }
        conflictIndicesCursor.close()
        assertTrue("Must contain index_sync_conflicts_unresolved", conflictIndices.contains("index_sync_conflicts_unresolved"))

        rawDb.close()
        helper.close()
    }

    private object SQLiteDatabaseConflictAlgorithm {
        const val NONE = 0
    }
}
