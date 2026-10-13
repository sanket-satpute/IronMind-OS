package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import com.sanket_satpute_20.ironmind.data.repository.toDomain
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration22To23Test {

    private lateinit var context: Context
    private val dbName = "migration_22_23_test.db"

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
    fun testRealMigration22To23_addsColumns_preservesLegacyData_verifiesDao() = runBlocking {
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

        // Step 2: Apply production migrations MIGRATION_1_2 through MIGRATION_21_22 sequentially
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
            IronMindDatabase.MIGRATION_21_22
        )

        for (migration in historicalMigrations) {
            migration.migrate(rawDb)
        }
        rawDb.version = 22

        // Seed representative legacy row in intervention_recommendations (v22 schema without causal columns)
        rawDb.execSQL(
            """
            INSERT INTO `intervention_recommendations` (
                `id`, `userId`, `interventionType`, `targetEntityId`, `targetEntityType`,
                `rationale`, `suggestedAction`, `status`, `createdAt`, `expiresAt`, `objective`
            ) VALUES (
                'rec-v22-1', 'user-123', 'BREAK_DOWN', 'goal-1', 'GOAL',
                'Break task down', 'Split into 3 steps', 'PENDING', 1000000, 2000000, 'INITIATE_ACTION'
            )
            """.trimIndent()
        )

        rawDb.close()
        helper.close()

        // Step 3: Open database through Room with production migration chain including MIGRATION_22_23
        val roomDb = Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            dbName
        ).addMigrations(
            *historicalMigrations.toTypedArray(),
            IronMindDatabase.MIGRATION_22_23
        ).allowMainThreadQueries()
        .build()

        val writableDb = roomDb.openHelper.writableDatabase

        // Step 4: Verify schema version is 23
        val versionCursor = writableDb.query("PRAGMA user_version")
        assertTrue(versionCursor.moveToFirst())
        assertEquals(23, versionCursor.getInt(0))
        versionCursor.close()

        // Step 5: Verify columns causalHypothesisId and causalHypothesisType exist in intervention_recommendations
        val tableInfoCursor = writableDb.query("PRAGMA table_info(intervention_recommendations)")
        val columnNames = mutableSetOf<String>()
        while (tableInfoCursor.moveToNext()) {
            val nameIndex = tableInfoCursor.getColumnIndex("name")
            columnNames.add(tableInfoCursor.getString(nameIndex))
        }
        tableInfoCursor.close()

        assertTrue("Column causalHypothesisId must exist", columnNames.contains("causalHypothesisId"))
        assertTrue("Column causalHypothesisType must exist", columnNames.contains("causalHypothesisType"))

        // Step 6: Verify seeded legacy row preserved and has null causal hypothesis columns
        val legacyCursor = writableDb.query("SELECT id, objective, status, causalHypothesisId, causalHypothesisType FROM `intervention_recommendations` WHERE id = 'rec-v22-1'")
        assertTrue("Seeded legacy row must be preserved", legacyCursor.moveToFirst())
        assertEquals("rec-v22-1", legacyCursor.getString(0))
        assertEquals("INITIATE_ACTION", legacyCursor.getString(1))
        assertEquals("PENDING", legacyCursor.getString(2))
        assertNull("Legacy row causalHypothesisId must be null", legacyCursor.getString(3))
        assertNull("Legacy row causalHypothesisType must be null", legacyCursor.getString(4))
        legacyCursor.close()

        // Step 7: Exercise Room DAO on legacy row and map to domain
        val dao = roomDb.interventionRecommendationDao()
        val legacyEntity = dao.getById("rec-v22-1")
        assertNotNull("Legacy entity must be loaded by DAO", legacyEntity)
        assertEquals("rec-v22-1", legacyEntity?.id)
        assertNull(legacyEntity?.causalHypothesisId)
        assertNull(legacyEntity?.causalHypothesisType)
        val legacyDomain = legacyEntity?.toDomain()
        assertNotNull(legacyDomain)
        assertNull("Legacy domain recommendation must have null causalHypothesis", legacyDomain?.causalHypothesis)

        // Step 8: Exercise Room DAO inserting entity with non-null provenance
        val provenanceEntity = InterventionRecommendationEntity(
            id = "rec-v23-provenance",
            userId = "user-123",
            interventionType = "REDIRECT",
            objective = "REDUCE_FRICTION",
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "Pattern matched",
            suggestedAction = "Take a break",
            status = "PENDING",
            createdAt = 1500000L,
            expiresAt = 2500000L,
            causalHypothesisId = "pat-focus-drop",
            causalHypothesisType = "PATTERN"
        )
        dao.insert(provenanceEntity)

        val retrievedProvenanceEntity = dao.getById("rec-v23-provenance")
        assertNotNull(retrievedProvenanceEntity)
        assertEquals("pat-focus-drop", retrievedProvenanceEntity?.causalHypothesisId)
        assertEquals("PATTERN", retrievedProvenanceEntity?.causalHypothesisType)

        val retrievedProvenanceDomain = retrievedProvenanceEntity?.toDomain()
        assertNotNull(retrievedProvenanceDomain)
        assertEquals(
            CausalHypothesisReference(id = "pat-focus-drop", type = CausalHypothesisType.PATTERN),
            retrievedProvenanceDomain?.causalHypothesis
        )

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
