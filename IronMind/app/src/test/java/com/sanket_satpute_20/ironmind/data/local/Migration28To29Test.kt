package com.sanket_satpute_20.ironmind.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
class Migration28To29Test {

    private lateinit var context: Context
    private val dbName = "migration_28_29_test.db"

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
    fun testMigration28To29_createsAppUsageObservationCheckpointsTableAndPreservesData() = runBlocking {
        // Step 1: Initialize database at v28
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create observations table as it exists in v28
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

        // Step 2: Seed pre-existing observation data
        val obsValues = ContentValues().apply {
            put("id", "obs-1")
            put("userId", "user-test")
            put("type", "APP_USAGE_SESSION")
            put("source", "APP_USAGE")
            put("occurredAt", 1700000000000L)
            put("recordedAt", 1700000001000L)
            put("value", "com.example.app")
            put("context", "duration_ms=60000")
            put("provenanceSource", "APP_USAGE")
            put("provenanceCapturedAt", 1700000001000L)
            put("schemaVersion", 1)
        }
        rawDb.insert("observations", SQLiteDatabase.CONFLICT_REPLACE, obsValues)

        // Step 3: Execute production migration MIGRATION_28_29
        IronMindDatabase.MIGRATION_28_29.migrate(rawDb)

        // Step 4: Verify table `app_usage_observation_checkpoints` was created with expected columns
        val tableInfoCursor = rawDb.query("PRAGMA table_info(`app_usage_observation_checkpoints`)")
        val columns = mutableMapOf<String, String>()
        var pkColumn = ""
        while (tableInfoCursor.moveToNext()) {
            val name = tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("name"))
            val type = tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("type"))
            val pk = tableInfoCursor.getInt(tableInfoCursor.getColumnIndexOrThrow("pk"))
            columns[name] = type
            if (pk == 1) pkColumn = name
        }
        tableInfoCursor.close()

        assertTrue("Table must contain userId", columns.containsKey("userId"))
        assertTrue("Table must contain lastCollectedIntervalEndMs", columns.containsKey("lastCollectedIntervalEndMs"))
        assertTrue("Table must contain activePackageName", columns.containsKey("activePackageName"))
        assertTrue("Table must contain activeSessionStartTimeMs", columns.containsKey("activeSessionStartTimeMs"))
        assertTrue("Table must contain updatedAt", columns.containsKey("updatedAt"))
        assertTrue("Table must contain schemaVersion", columns.containsKey("schemaVersion"))
        assertEquals("userId must be primary key", "userId", pkColumn)

        // Step 5: Verify pre-existing data was preserved
        val preDataCursor = rawDb.query("SELECT id, userId, value, context FROM observations WHERE id = 'obs-1'")
        assertTrue("Pre-existing observation must be preserved", preDataCursor.moveToFirst())
        assertEquals("obs-1", preDataCursor.getString(0))
        assertEquals("user-test", preDataCursor.getString(1))
        assertEquals("com.example.app", preDataCursor.getString(2))
        assertEquals("duration_ms=60000", preDataCursor.getString(3))
        preDataCursor.close()

        // Step 6: Verify insert and query into the newly created checkpoint table
        val cpValues = ContentValues().apply {
            put("userId", "user-test")
            put("lastCollectedIntervalEndMs", 1700000060000L)
            put("activePackageName", "com.example.app")
            put("activeSessionStartTimeMs", 1700000000000L)
            put("updatedAt", 1700000060500L)
            put("schemaVersion", 1)
        }
        rawDb.insert("app_usage_observation_checkpoints", SQLiteDatabase.CONFLICT_REPLACE, cpValues)

        val cpCursor = rawDb.query("SELECT userId, lastCollectedIntervalEndMs, activePackageName, activeSessionStartTimeMs FROM app_usage_observation_checkpoints WHERE userId = 'user-test'")
        assertTrue(cpCursor.moveToFirst())
        assertEquals("user-test", cpCursor.getString(0))
        assertEquals(1700000060000L, cpCursor.getLong(1))
        assertEquals("com.example.app", cpCursor.getString(2))
        assertEquals(1700000000000L, cpCursor.getLong(3))
        cpCursor.close()

        rawDb.close()
    }
}

