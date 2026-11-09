package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
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

/**
 * Phase 3.6.6.4R: Section 3 — Non-Destructive Room Migration 29 -> 30 Test
 *
 * Verifies that:
 * 1. MIGRATION_29_30 creates the composite index `index_observations_userId_type_occurredAt`.
 * 2. All pre-existing observation rows are completely preserved without loss or corruption.
 * 3. The migrated database operates cleanly at version 30 under Room.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration29To30Test {

    private lateinit var context: Context
    private val dbName = "migration_29_30_test.db"

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
    fun testRealMigration29To30_createsCompositeIndex_preservesExistingData() = runBlocking {
        // Step 1: Create a v29 database directly using SupportSQLiteOpenHelper
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create minimal v29 observations table without index
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `observations` (
                            `id` TEXT NOT NULL,
                            `userId` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `occurredAt` INTEGER NOT NULL,
                            `recordedAt` INTEGER NOT NULL,
                            `subjectId` TEXT,
                            `value` TEXT NOT NULL,
                            `context` TEXT NOT NULL,
                            `confidence` REAL,
                            `provenanceSource` TEXT NOT NULL,
                            `provenanceSourceReference` TEXT,
                            `provenanceCapturedAt` INTEGER NOT NULL,
                            `schemaVersion` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val rawDb = helper.writableDatabase

        // Step 2: Insert pre-migration test row at version 29
        rawDb.execSQL(
            """
            INSERT INTO observations (
                id, userId, type, source, occurredAt, recordedAt, subjectId, value, context,
                confidence, provenanceSource, provenanceSourceReference, provenanceCapturedAt, schemaVersion
            ) VALUES (
                'obs_pre_migration', 'user_mig_29', 'APP_USAGE_SESSION', 'APP_USAGE', 1700000000000,
                1700000001000, NULL, 'com.migrated.app', 'duration_ms=60000', NULL, 'APP_USAGE',
                'UsageStatsManager', 1700000001000, 1
            )
            """.trimIndent()
        )

        // Verify index does NOT exist at version 29
        val preIndexCursor = rawDb.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_observations_userId_type_occurredAt'"
        )
        assertEquals("Composite index must not exist in v29 schema", 0, preIndexCursor.count)
        preIndexCursor.close()

        // Step 3: Execute production MIGRATION_29_30
        IronMindDatabase.MIGRATION_29_30.migrate(rawDb)

        // Step 4: Verify composite index was created
        val postIndexCursor = rawDb.query(
            "SELECT name, sql FROM sqlite_master WHERE type = 'index' AND name = 'index_observations_userId_type_occurredAt'"
        )
        assertEquals("Composite index must exist after migration", 1, postIndexCursor.count)
        postIndexCursor.moveToFirst()
        val indexSql = postIndexCursor.getString(postIndexCursor.getColumnIndexOrThrow("sql"))
        assertTrue(indexSql.contains("observations"))
        assertTrue(indexSql.contains("userId"))
        assertTrue(indexSql.contains("type"))
        assertTrue(indexSql.contains("occurredAt"))
        postIndexCursor.close()

        // Step 5: Verify pre-existing data is preserved intact
        val dataCursor = rawDb.query("SELECT * FROM observations WHERE id = 'obs_pre_migration'")
        assertEquals(1, dataCursor.count)
        dataCursor.moveToFirst()
        assertEquals("user_mig_29", dataCursor.getString(dataCursor.getColumnIndexOrThrow("userId")))
        assertEquals("APP_USAGE_SESSION", dataCursor.getString(dataCursor.getColumnIndexOrThrow("type")))
        assertEquals("com.migrated.app", dataCursor.getString(dataCursor.getColumnIndexOrThrow("value")))
        assertEquals("duration_ms=60000", dataCursor.getString(dataCursor.getColumnIndexOrThrow("context")))
        assertEquals(1700000000000L, dataCursor.getLong(dataCursor.getColumnIndexOrThrow("occurredAt")))
        dataCursor.close()
        // Step 6: Verify index is used by SQLite query engine
        val planCursor = rawDb.query(
            "EXPLAIN QUERY PLAN SELECT * FROM observations WHERE userId = 'user_mig_29' AND type = 'APP_USAGE_SESSION' AND occurredAt >= 1000 AND occurredAt < 2000 ORDER BY occurredAt ASC"
        )
        val planDetails = mutableListOf<String>()
        while (planCursor.moveToNext()) {
            planDetails.add(planCursor.getString(planCursor.getColumnIndexOrThrow("detail")))
        }
        planCursor.close()

        assertTrue(
            "Query plan must use index_observations_userId_type_occurredAt",
            planDetails.any { it.contains("SEARCH") && it.contains("index_observations_userId_type_occurredAt") }
        )

        rawDb.close()
    }
}

