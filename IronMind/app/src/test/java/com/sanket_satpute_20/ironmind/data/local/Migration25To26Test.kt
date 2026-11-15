package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration25To26Test {

    private lateinit var context: Context
    private val dbName = "migration_25_26_test.db"

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
    fun testRealMigration25To26_addsBaseVersionAndLeaseColumns_preservesExistingData() = runBlocking {
        // Step 1: Initialize database at v1
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createV1Tables(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val rawDb = helper.writableDatabase

        // Step 2: Apply migrations 1 through 25 sequentially
        val historicalMigrations = listOf(
            IronMindDatabase.MIGRATION_1_2,
            IronMindDatabase.MIGRATION_2_3,
            IronMindDatabase.MIGRATION_3_4,
            IronMindDatabase.MIGRATION_4_5,
            IronMindDatabase.MIGRATION_5_6,
            IronMindDatabase.MIGRATION_6_7,
            IronMindDatabase.MIGRATION_7_8,
            IronMindDatabase.MIGRATION_8_9,
            IronMindDatabase.MIGRATION_9_10,
            IronMindDatabase.MIGRATION_10_11,
            IronMindDatabase.MIGRATION_11_12,
            IronMindDatabase.MIGRATION_12_13,
            IronMindDatabase.MIGRATION_13_14,
            IronMindDatabase.MIGRATION_14_15,
            IronMindDatabase.MIGRATION_15_16,
            IronMindDatabase.MIGRATION_16_17,
            IronMindDatabase.MIGRATION_17_18,
            IronMindDatabase.MIGRATION_18_19,
            IronMindDatabase.MIGRATION_19_20,
            IronMindDatabase.MIGRATION_20_21,
            IronMindDatabase.MIGRATION_21_22,
            IronMindDatabase.MIGRATION_22_23,
            IronMindDatabase.MIGRATION_23_24,
            IronMindDatabase.MIGRATION_24_25
        )

        for (migration in historicalMigrations) {
            migration.migrate(rawDb)
        }
        rawDb.version = 25

        // Seed v25 outbox record (has userId, but no baseVersion or lease columns)
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`, `userId`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-legacy-1", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"user-test-1"}""", 1000L, 0, null, "PENDING", "user-test-1")
        )

        // Step 3: Apply MIGRATION_25_26
        IronMindDatabase.MIGRATION_25_26.migrate(rawDb)
        rawDb.version = 26

        // Step 4: Verify schema alterations
        val cursor = rawDb.query("SELECT `operationId`, `userId`, `baseVersion`, `leaseToken`, `leaseExpiresAt` FROM `outbox` WHERE `operationId` = 'op-legacy-1'")
        cursor.use {
            assertTrue("Expected existing record to be present", it.moveToFirst())
            assertEquals("op-legacy-1", it.getString(0))
            assertEquals("user-test-1", it.getString(1))
            assertTrue("baseVersion should be NULL for pre-existing row", it.isNull(2))
            assertTrue("leaseToken should be NULL for pre-existing row", it.isNull(3))
            assertTrue("leaseExpiresAt should be NULL for pre-existing row", it.isNull(4))
        }

        // Verify index_outbox_leaseToken exists
        val indexCursor = rawDb.query("PRAGMA index_list('outbox')")
        val indexNames = mutableListOf<String>()
        indexCursor.use {
            while (it.moveToNext()) {
                indexNames.add(it.getString(1))
            }
        }
        assertTrue("Expected index_outbox_leaseToken in index list", indexNames.contains("index_outbox_leaseToken"))

        rawDb.close()

        // Step 5: Verify RoomDatabase can open database at v26
        val roomDb = Room.databaseBuilder(context, IronMindDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(
                IronMindDatabase.MIGRATION_1_2,
                IronMindDatabase.MIGRATION_2_3,
                IronMindDatabase.MIGRATION_3_4,
                IronMindDatabase.MIGRATION_4_5,
                IronMindDatabase.MIGRATION_5_6,
                IronMindDatabase.MIGRATION_6_7,
                IronMindDatabase.MIGRATION_7_8,
                IronMindDatabase.MIGRATION_8_9,
                IronMindDatabase.MIGRATION_9_10,
                IronMindDatabase.MIGRATION_10_11,
                IronMindDatabase.MIGRATION_11_12,
                IronMindDatabase.MIGRATION_12_13,
                IronMindDatabase.MIGRATION_13_14,
                IronMindDatabase.MIGRATION_14_15,
                IronMindDatabase.MIGRATION_15_16,
                IronMindDatabase.MIGRATION_16_17,
                IronMindDatabase.MIGRATION_17_18,
                IronMindDatabase.MIGRATION_18_19,
                IronMindDatabase.MIGRATION_19_20,
                IronMindDatabase.MIGRATION_20_21,
                IronMindDatabase.MIGRATION_21_22,
                IronMindDatabase.MIGRATION_22_23,
                IronMindDatabase.MIGRATION_23_24,
                IronMindDatabase.MIGRATION_24_25,
                IronMindDatabase.MIGRATION_25_26,
                IronMindDatabase.MIGRATION_26_27,
                IronMindDatabase.MIGRATION_27_28,
                IronMindDatabase.MIGRATION_28_29,
                IronMindDatabase.MIGRATION_29_30
            )
            .build()

        val outboxDao = roomDb.outboxDao()
        val legacyEntry = outboxDao.getEntryByOperationIdForUser("op-legacy-1", "user-test-1")
        assertNotNull("Legacy entry should be retrieved via Room", legacyEntry)
        assertEquals("op-legacy-1", legacyEntry?.operationId)
        assertNull(legacyEntry?.baseVersion)
        assertNull(legacyEntry?.leaseToken)
        assertNull(legacyEntry?.leaseExpiresAt)

        // Insert new entry with version and lease fields
        val newEntry = OutboxEntity(
            operationId = "op-v26-new",
            entityType = "Plan",
            entityId = "p-1",
            operationType = "UPSERT",
            payload = """{"id":"p-1","userId":"user-test-1"}""",
            createdAt = 2000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING",
            userId = "user-test-1",
            baseVersion = 5L,
            leaseToken = "lease-token-abc",
            leaseExpiresAt = 3000L
        )
        outboxDao.insertEntry(newEntry)

        val retrievedNew = outboxDao.getEntryByOperationIdForUser("op-v26-new", "user-test-1")
        assertNotNull(retrievedNew)
        assertEquals(5L, retrievedNew?.baseVersion)
        assertEquals("lease-token-abc", retrievedNew?.leaseToken)
        assertEquals(3000L, retrievedNew?.leaseExpiresAt)

        roomDb.close()
    }

    private fun createV1Tables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_profile` (
                `id` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `displayName` TEXT NOT NULL,
                `timezone` TEXT NOT NULL,
                `createdFrom` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `schemaVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

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
                `goalId` TEXT,
                `ambitionId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `source` TEXT NOT NULL,
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
                `goalId` TEXT,
                `planId` TEXT,
                `parentTaskId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                `estimatedDurationMinutes` INTEGER,
                `scheduledAt` INTEGER,
                `dueAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `completedAt` INTEGER,
                `postponedAt` INTEGER,
                `source` TEXT NOT NULL,
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
                `goalId` TEXT,
                `planId` TEXT,
                `taskId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `committedAt` INTEGER NOT NULL,
                `scheduledStartAt` INTEGER,
                `scheduledEndAt` INTEGER,
                `status` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `postponedAt` INTEGER,
                `missedAt` INTEGER,
                `recoveredAt` INTEGER,
                `parentCommitmentId` TEXT,
                `schemaVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `outcome` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `sourceEntityId` TEXT NOT NULL,
                `sourceEntityType` TEXT NOT NULL,
                `resultStatus` TEXT NOT NULL,
                `actualDurationMinutes` INTEGER,
                `completedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
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
                `targetEntityId` TEXT,
                `targetEntityType` TEXT,
                `content` TEXT NOT NULL,
                `sentiment` TEXT,
                `createdAt` INTEGER NOT NULL,
                `schemaVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `protection_rules` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `targetPackage` TEXT,
                `targetCategory` TEXT,
                `triggerType` TEXT NOT NULL,
                `conditions` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL,
                `priority` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `protection_sessions` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `ruleId` TEXT,
                `commitmentId` TEXT,
                `taskId` TEXT,
                `startedAt` INTEGER NOT NULL,
                `scheduledEndAt` INTEGER,
                `endedAt` INTEGER,
                `status` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `overrideAllowed` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `events` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `entityType` TEXT,
                `entityId` TEXT,
                `occurredAt` INTEGER NOT NULL,
                `recordedAt` INTEGER NOT NULL,
                `processedAt` INTEGER,
                `source` TEXT NOT NULL,
                `previousState` TEXT,
                `newState` TEXT,
                `metadata` TEXT,
                `correlationId` TEXT,
                `causationId` TEXT,
                `schemaVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
