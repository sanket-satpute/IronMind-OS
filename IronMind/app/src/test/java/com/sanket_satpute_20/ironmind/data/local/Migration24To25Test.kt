package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration24To25Test {

    private lateinit var context: Context
    private val dbName = "migration_24_25_test.db"

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
    fun testRealMigration24To25_altersOutbox_backfillsUserId_createsSyncConflicts() = runBlocking {
        // Step 1: Construct a v1 database using the real initial schema
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

        // Step 2: Apply production migrations MIGRATION_1_2 through MIGRATION_23_24 sequentially to reach v24
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
            IronMindDatabase.MIGRATION_23_24
        )

        for (migration in historicalMigrations) {
            migration.migrate(rawDb)
        }
        rawDb.version = 24

        // Seed pre-migration v24 outbox records (before userId column exists)
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-valid-1", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"user-abc","title":"Goal 1"}""", 1000L, 0, null, "PENDING")
        )
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-valid-2", "Plan", "p-1", "UPSERT", """{"id":"p-1","userId":"  user-xyz  ","title":"Plan 1"}""", 1001L, 0, null, "PENDING")
        )
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-missing-uid", "Task", "t-1", "UPSERT", """{"id":"t-1","title":"Task without userId"}""", 1002L, 0, null, "PENDING")
        )
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-null-uid", "Goal", "g-2", "UPSERT", """{"id":"g-2","userId":null,"title":"Null UID"}""", 1003L, 0, null, "PENDING")
        )
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-empty-uid", "Goal", "g-3", "UPSERT", """{"id":"g-3","userId":"   ","title":"Empty UID"}""", 1004L, 0, null, "PENDING")
        )
        rawDb.execSQL(
            """
            INSERT INTO `outbox` (`operationId`, `entityType`, `entityId`, `operationType`, `payload`, `createdAt`, `retryCount`, `lastAttemptAt`, `status`)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("op-corrupt", "Task", "t-2", "UPSERT", "NOT_A_VALID_JSON_STRING", 1005L, 0, null, "PENDING")
        )

        // Verify sync_conflicts table does NOT exist prior to v25
        val preMigrationCursor = rawDb.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_conflicts'")
        assertFalse("Table sync_conflicts must not exist prior to v25", preMigrationCursor.moveToFirst())
        preMigrationCursor.close()

        rawDb.close()
        helper.close()

        // Step 3: Open database through Room with production migration chain including MIGRATION_24_25
        val roomDb = Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            dbName
        ).allowMainThreadQueries()
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
        ).build()

        val outboxDao = roomDb.outboxDao()
        val syncConflictDao = roomDb.syncConflictDao()

        // Step 4: Verify backfill in outbox
        val entryValid1 = outboxDao.getEntryByOperationId("op-valid-1")
        assertNotNull(entryValid1)
        assertEquals("user-abc", entryValid1?.userId)

        val entryValid2 = outboxDao.getEntryByOperationId("op-valid-2")
        assertNotNull(entryValid2)
        assertEquals("user-xyz", entryValid2?.userId)

        val entryMissing = outboxDao.getEntryByOperationId("op-missing-uid")
        assertNotNull(entryMissing)
        assertNull("Missing userId in payload should backfill as null", entryMissing?.userId)

        val entryNull = outboxDao.getEntryByOperationId("op-null-uid")
        assertNotNull(entryNull)
        assertNull("Null userId in payload should backfill as null", entryNull?.userId)

        val entryEmpty = outboxDao.getEntryByOperationId("op-empty-uid")
        assertNotNull(entryEmpty)
        assertNull("Blank userId in payload should backfill as null", entryEmpty?.userId)

        val entryCorrupt = outboxDao.getEntryByOperationId("op-corrupt")
        assertNotNull(entryCorrupt)
        assertNull("Malformed JSON payload should backfill as null without crashing", entryCorrupt?.userId)
        assertEquals("NOT_A_VALID_JSON_STRING", entryCorrupt?.payload)

        // Step 5: Verify sync_conflicts table works via DAO
        val conflict = SyncConflictEntity(
            conflictId = "c-1",
            operationId = "op-valid-1",
            userId = "user-abc",
            entityType = "Goal",
            entityId = "g-1",
            localPayload = """{"id":"g-1","title":"Local"}""",
            remotePayload = """{"id":"g-1","title":"Remote"}""",
            conflictReason = "VERSION_MISMATCH",
            createdAt = 2000L,
            updatedAt = 2000L,
            resolutionStatus = "PENDING"
        )
        syncConflictDao.insertConflict(conflict)

        val retrievedConflict = syncConflictDao.getConflictById("c-1")
        assertNotNull(retrievedConflict)
        assertEquals("op-valid-1", retrievedConflict?.operationId)
        assertEquals("user-abc", retrievedConflict?.userId)
        assertEquals("PENDING", retrievedConflict?.resolutionStatus)

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
