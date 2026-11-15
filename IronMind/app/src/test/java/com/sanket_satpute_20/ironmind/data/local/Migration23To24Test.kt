package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.CorrectionReviewEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toDomainModel
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration23To24Test {

    private lateinit var context: Context
    private val dbName = "migration_23_24_test.db"

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
    fun testRealMigration23To24_createsCorrectionReviewsTable_verifiesDao() = runBlocking {
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

        // Step 2: Apply production migrations MIGRATION_1_2 through MIGRATION_22_23 sequentially
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
            IronMindDatabase.MIGRATION_22_23
        )

        for (migration in historicalMigrations) {
            migration.migrate(rawDb)
        }
        rawDb.version = 23

        // Verify table correction_reviews does not exist yet at v23
        val preMigrationCursor = rawDb.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'correction_reviews'")
        assertFalse("Table correction_reviews must not exist prior to v24", preMigrationCursor.moveToFirst())
        preMigrationCursor.close()

        rawDb.close()
        helper.close()

        // Step 3: Open database through Room with production migration chain including MIGRATION_23_24
        val roomDb = Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            dbName
        ).addMigrations(
            *historicalMigrations.toTypedArray(),
            IronMindDatabase.MIGRATION_23_24,
            IronMindDatabase.MIGRATION_24_25,
            IronMindDatabase.MIGRATION_25_26,
            IronMindDatabase.MIGRATION_26_27,
            IronMindDatabase.MIGRATION_27_28,
            IronMindDatabase.MIGRATION_28_29,
            IronMindDatabase.MIGRATION_29_30
        ).allowMainThreadQueries()
        .build()

        val writableDb = roomDb.openHelper.writableDatabase

        // Step 4: Verify schema version is 30
        val versionCursor = writableDb.query("PRAGMA user_version")
        assertTrue(versionCursor.moveToFirst())
        assertEquals(30, versionCursor.getInt(0))
        versionCursor.close()

        // Step 5: Verify table correction_reviews and its columns exist
        val tableInfoCursor = writableDb.query("PRAGMA table_info(correction_reviews)")
        val columnNames = mutableSetOf<String>()
        while (tableInfoCursor.moveToNext()) {
            val nameIndex = tableInfoCursor.getColumnIndex("name")
            columnNames.add(tableInfoCursor.getString(nameIndex))
        }
        tableInfoCursor.close()

        assertTrue(columnNames.contains("id"))
        assertTrue(columnNames.contains("userId"))
        assertTrue(columnNames.contains("sourceEventId"))
        assertTrue(columnNames.contains("recommendationId"))
        assertTrue(columnNames.contains("causalHypothesisId"))
        assertTrue(columnNames.contains("causalHypothesisType"))
        assertTrue(columnNames.contains("correctionScope"))
        assertTrue(columnNames.contains("correctedText"))
        assertTrue(columnNames.contains("decisionState"))
        assertTrue(columnNames.contains("reviewStatus"))
        assertTrue(columnNames.contains("reviewFeedback"))
        assertTrue(columnNames.contains("createdAt"))
        assertTrue(columnNames.contains("updatedAt"))
        assertTrue(columnNames.contains("reviewedAt"))
        assertTrue(columnNames.contains("schemaVersion"))

        // Step 6: Verify indexes exist
        val indexCursor = writableDb.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'correction_reviews'")
        val foundIndexes = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            foundIndexes.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue(foundIndexes.contains("index_correction_reviews_user_event"))
        assertTrue(foundIndexes.contains("index_correction_reviews_user_status"))
        assertTrue(foundIndexes.contains("index_correction_reviews_user_created"))

        // Step 7: Exercise Room DAO insert and read
        val dao = roomDb.correctionReviewDao()
        val testEntity = CorrectionReviewEntity(
            id = "cr_test_1",
            userId = "user-123",
            sourceEventId = "event-456",
            recommendationId = "rec-789",
            causalHypothesisId = "pat-focus",
            causalHypothesisType = "PATTERN",
            correctionScope = "INFERENCE_CORRECTION",
            correctedText = "Only morning walks",
            decisionState = "DEFERRED_FOR_CONSIDERATION",
            reviewStatus = "PENDING",
            reviewFeedback = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            reviewedAt = null,
            schemaVersion = 1
        )

        val rowId = dao.insertIgnore(testEntity)
        assertTrue(rowId > 0)

        val retrieved = dao.getById("cr_test_1", "user-123")
        assertNotNull(retrieved)
        assertEquals("cr_test_1", retrieved?.id)
        assertEquals("user-123", retrieved?.userId)
        assertEquals("Only morning walks", retrieved?.correctedText)
        assertEquals("PENDING", retrieved?.reviewStatus)

        // Step 8: Verify domain mapping
        val domainModel = retrieved?.toDomainModel()
        assertNotNull(domainModel)
        assertEquals(CorrectionReviewStatus.PENDING, domainModel?.reviewStatus)
        assertEquals(CorrectionScope.INFERENCE_CORRECTION, domainModel?.correctionScope)
        assertEquals("pat-focus", domainModel?.causalHypothesis?.id)
        assertEquals(CausalHypothesisType.PATTERN, domainModel?.causalHypothesis?.type)

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
