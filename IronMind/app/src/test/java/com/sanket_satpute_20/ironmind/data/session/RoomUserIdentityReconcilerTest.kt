package com.sanket_satpute_20.ironmind.data.session

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.PatternFingerprintCollisionException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class RoomUserIdentityReconcilerTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var reconciler: RoomUserIdentityReconciler

    private val legacyUser = "user-1"
    private val targetUser = "firebase-anon-test-uid-789"

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        reconciler = RoomUserIdentityReconciler(db)
    }

    @After
    fun tearDown() {
        try {
            db.close()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    // ----- DAO-based insert helpers (matching proven pattern from CompleteUserDataErasureTest) -----

    private fun insertProfile(userId: String, name: String = "User $userId", tz: String = "Asia/Kolkata") {
        db.ironMindDao().insertUserProfile(
            UserProfileEntity(
                id = userId, createdAt = 1000L, updatedAt = 1000L,
                displayName = name, timezone = tz,
                createdFrom = "LOCAL", status = "ACTIVE", schemaVersion = 1
            )
        )
    }

    private fun insertGoal(id: String, userId: String) {
        db.ironMindDao().insertGoal(
            GoalEntity(
                id = id, userId = userId, ambitionId = null,
                title = "Fitness", description = "Get fit", why = "Health",
                importance = 1, status = "ACTIVE",
                targetAt = null, startedAt = null, completedAt = null,
                createdAt = 1000L, updatedAt = 1000L, schemaVersion = 1
            )
        )
    }

    private suspend fun insertGlobalAutonomyState(userId: String, pauseActive: Boolean = false) {
        db.globalAutonomyStateDao().upsertState(
            GlobalAutonomyStateEntity(
                userId = userId, isGlobalPauseActive = pauseActive, updatedAt = 1000L
            )
        )
    }

    private fun insertAutonomySetting(userId: String, capability: String, level: String) {
        db.autonomySettingsDao().upsertSetting(
            AutonomySettingsEntity(
                userId = userId, capability = capability,
                level = level, updatedAt = 1000L
            )
        )
    }

    private fun insertRecommendation(id: String, userId: String) {
        db.interventionRecommendationDao().insert(
            InterventionRecommendationEntity(
                id = id, userId = userId, interventionType = "BREAK_DOWN",
                objective = "INITIATE_ACTION", targetEntityId = "g1",
                targetEntityType = "GOAL", rationale = "Why",
                suggestedAction = "Action", status = "PENDING",
                createdAt = 1000L, expiresAt = 5000L,
                causalHypothesisId = null, causalHypothesisType = null
            )
        )
    }

    private suspend fun insertBarrier(id: String, userId: String) {
        db.barrierDao().insert(
            BarrierHypothesisEntity(
                id = id, userId = userId, category = "ENERGY",
                description = "Low energy in afternoon",
                confirmationState = "UNCONFIRMED", status = "ACTIVE",
                sourceReflectionId = "r1",
                firstObservedAt = 1000L, lastObservedAt = 1000L
            )
        )
    }

    private fun insertPattern(id: String, userId: String, fingerprint: String) {
        db.patternDao().insertPattern(
            PatternEntity(
                id = id,
                userId = userId,
                fingerprint = fingerprint,
                type = "TEMPORAL",
                description = "Late night work",
                conditions = null,
                predictedBehavior = null,
                confidence = 0.85f,
                evidenceCount = 3,
                evidenceReferences = null,
                firstObservedAt = 1000L,
                lastObservedAt = 1000L,
                status = "ACTIVE",
                confirmationState = "CONFIRMED",
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
    }

    // ----- Read helpers using Room's db.query (not openHelper.readableDatabase) -----

    private fun goalCount(userId: String): Int {
        return db.ironMindDao().getGoalsForUser(userId).size
    }

    private fun profileExists(userId: String): Boolean {
        return db.ironMindDao().getUserProfile(userId) != null
    }

    private fun tableRowCount(table: String, column: String, value: String): Int {
        val cursor = db.query(
            SimpleSQLiteQuery("SELECT COUNT(*) FROM $table WHERE $column = ?", arrayOf(value))
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private data class TableColumn(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val primaryKeyPosition: Int
    )

    private fun tableNames(): Set<String> = db.query(
        SimpleSQLiteQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata')"
        )
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun tableColumns(table: String): List<TableColumn> = db.query(
        SimpleSQLiteQuery("PRAGMA table_info(`$table`)")
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TableColumn(
                        name = cursor.getString(1),
                        type = cursor.getString(2).uppercase(),
                        notNull = cursor.getInt(3) != 0,
                        primaryKeyPosition = cursor.getInt(5)
                    )
                )
            }
        }
    }

    private fun insertInventoryRow(
        table: String,
        userId: String,
        suffix: String,
        overrides: Map<String, Any?> = emptyMap()
    ) {
        val columns = tableColumns(table).filter { it.notNull || it.primaryKeyPosition > 0 || it.name == "userId" }
        val values = columns.map { column ->
            overrides[column.name] ?: when {
                column.name == "userId" -> userId
                table == "user_profile" && column.name == "id" -> userId
                column.name == "id" || column.name == "eventId" || column.name == "operationId" || column.name == "conflictId" -> "$table-$userId-$suffix"
                column.name == "fingerprint" -> "$table-$userId-$suffix-fingerprint"
                column.name == "capability" -> "SHARED_CAPABILITY"
                column.type.contains("INT") -> 1L
                column.type.contains("REAL") || column.type.contains("FLOA") || column.type.contains("DOUB") -> 1.0
                column.type.contains("BLOB") -> byteArrayOf(1)
                else -> "audit-$table-$userId-$suffix-${column.name}"
            }
        }
        val columnSql = columns.joinToString(", ") { "`${it.name}`" }
        val placeholders = List(columns.size) { "?" }.joinToString(", ")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `$table` ($columnSql) VALUES ($placeholders)",
            values.toTypedArray()
        )
    }

    private fun scalarString(sql: String, args: Array<Any?> = emptyArray()): String = db.query(
        SimpleSQLiteQuery(sql, args)
    ).use { cursor ->
        assertTrue("Expected one row for query: $sql", cursor.moveToFirst())
        cursor.getString(0)
    }

    // =================== Tests ===================

    @Test
    fun `schema inventory covers every user-scoped entity and exercises every migration table`() = runBlocking {
        val tables = tableNames()
        val scopedTables = tables.filterTo(mutableSetOf()) { table ->
            val names = tableColumns(table).map { it.name }.toSet()
            "userId" in names || (table == "user_profile" && "id" in names)
        }

        assertEquals("Room entity table inventory changed; review migration coverage", 35, tables.size)
        assertEquals("The schema inventory must find the 31 currently scoped entities", 31, scopedTables.size)
        assertEquals(RoomUserIdentityReconciler.MIGRATED_USER_SCOPED_TABLES + "outbox", scopedTables)
        assertEquals(
            setOf("decision_records", "notification_records", "app_session_state", "sync_worker_leases"),
            tables - scopedTables
        )
        assertTrue("Outbox has a userId column as of version 25", "userId" in tableColumns("outbox").map { it.name })

        for (table in scopedTables) {
            if (table == "autonomy_settings") {
                insertInventoryRow(table, legacyUser, "shared-legacy", mapOf("capability" to "SHARED_CAPABILITY", "level" to "LEGACY_LEVEL"))
                insertInventoryRow(table, legacyUser, "legacy-only", mapOf("capability" to "LEGACY_ONLY", "level" to "LEGACY_ONLY_LEVEL"))
                insertInventoryRow(table, targetUser, "shared-target", mapOf("capability" to "SHARED_CAPABILITY", "level" to "TARGET_LEVEL"))
            } else {
                val legacyOverrides = when (table) {
                    "user_profile" -> mapOf(
                        "displayName" to "Legacy display", "timezone" to "Asia/Kolkata",
                        "createdAt" to 1_000L, "createdFrom" to "LEGACY_CREATED_FROM",
                        "status" to "LEGACY_STATUS", "schemaVersion" to 1L
                    )
                    "global_autonomy_state" -> mapOf("isGlobalPauseActive" to 1L)
                    "app_usage_observation_settings", "notification_observation_settings",
                    "calendar_observation_settings", "location_observation_settings",
                    "activity_observation_settings" -> mapOf("isEnabled" to 1L)
                    else -> emptyMap()
                }
                val targetOverrides = when (table) {
                    "user_profile" -> mapOf(
                        "displayName" to "", "timezone" to "", "createdAt" to 2_000L,
                        "createdFrom" to "TARGET_CREATED_FROM", "status" to "TARGET_STATUS",
                        "schemaVersion" to 2L
                    )
                    "global_autonomy_state" -> mapOf("isGlobalPauseActive" to 0L)
                    "app_usage_observation_settings", "notification_observation_settings",
                    "calendar_observation_settings", "location_observation_settings",
                    "activity_observation_settings" -> mapOf("isEnabled" to 0L)
                    else -> emptyMap()
                }
                insertInventoryRow(table, legacyUser, "legacy", legacyOverrides)
                insertInventoryRow(table, targetUser, "target", targetOverrides)
            }
        }

        val result = reconciler.reconcile(legacyUser, targetUser)
        assertTrue("Every inventoried user-scoped table must reconcile", result is Result.Success)

        for (table in scopedTables) {
            val ownerColumn = if (table == "user_profile") "id" else "userId"
            assertEquals("Legacy ownership remains in $table", 0, tableRowCount(table, ownerColumn, legacyUser))
            val expectedTargetRows = when (table) {
                "goal", "plan", "task", "commitment", "outcome", "reflection", "protection_rules",
                "protection_sessions", "events", "memory", "observations", "intervention_records",
                "experiment_records", "barrier_hypotheses", "intervention_recommendations",
                "learner_event_status", "patterns", "correction_reviews", "sync_conflicts", "outbox",
                "sync_checkpoints", "local_tombstones" -> 2
                "autonomy_settings" -> 2
                else -> 1
            }
            assertEquals("Unexpected merged or lost rows in $table", expectedTargetRows, tableRowCount(table, ownerColumn, targetUser))
        }

        assertEquals("Legacy profile fills blank target fields", "Legacy display", scalarString(
            "SELECT displayName FROM user_profile WHERE id = ?", arrayOf(targetUser)
        ))
        assertEquals("Legacy profile timezone fills blank target field", "Asia/Kolkata", scalarString(
            "SELECT timezone FROM user_profile WHERE id = ?", arrayOf(targetUser)
        ))
        assertEquals(2_000L, db.query(SimpleSQLiteQuery(
            "SELECT createdAt FROM user_profile WHERE id = ?", arrayOf(targetUser)
        )).use { it.moveToFirst(); it.getLong(0) })
        assertEquals("TARGET_CREATED_FROM", scalarString(
            "SELECT createdFrom FROM user_profile WHERE id = ?", arrayOf(targetUser)
        ))
        assertEquals("TARGET_STATUS", scalarString(
            "SELECT status FROM user_profile WHERE id = ?", arrayOf(targetUser)
        ))
        assertEquals(2L, db.query(SimpleSQLiteQuery(
            "SELECT schemaVersion FROM user_profile WHERE id = ?", arrayOf(targetUser)
        )).use { it.moveToFirst(); it.getLong(0) })
        assertEquals(1, tableRowCount("global_autonomy_state", "userId", targetUser))
        assertEquals(1, db.query(SimpleSQLiteQuery(
            "SELECT isGlobalPauseActive FROM global_autonomy_state WHERE userId = ?", arrayOf(targetUser)
        )).use { it.moveToFirst(); it.getInt(0) })
        assertEquals("Existing target autonomy setting wins", "TARGET_LEVEL", scalarString(
            "SELECT level FROM autonomy_settings WHERE userId = ? AND capability = 'SHARED_CAPABILITY'",
            arrayOf(targetUser)
        ))
        assertEquals("Unique legacy autonomy setting is retained", "LEGACY_ONLY_LEVEL", scalarString(
            "SELECT level FROM autonomy_settings WHERE userId = ? AND capability = 'LEGACY_ONLY'",
            arrayOf(targetUser)
        ))
        for (table in listOf(
            "app_usage_observation_settings", "notification_observation_settings",
            "calendar_observation_settings", "location_observation_settings", "activity_observation_settings"
        )) {
            assertEquals("Legacy enabled setting is preserved by merge policy in $table", 1, db.query(
                SimpleSQLiteQuery("SELECT isEnabled FROM `$table` WHERE userId = ?", arrayOf(targetUser))
            ).use { it.moveToFirst(); it.getInt(0) })
        }

        val secondRun = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(secondRun is Result.Success)
        assertEquals("Full-schema reconciliation must be idempotent", 0, (secondRun as Result.Success).data.recordsUpdated)
    }

    @Test
    fun `reconcile returns success immediately when legacy and target user IDs are identical`() = runBlocking {
        val result = reconciler.reconcile(targetUser, targetUser)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertTrue(data.isReconciled)
        assertEquals(0, data.recordsUpdated)
    }

    @Test
    fun `reconcile returns success immediately when either ID is blank`() = runBlocking {
        val result1 = reconciler.reconcile("", targetUser)
        assertTrue(result1 is Result.Success)
        assertEquals(0, (result1 as Result.Success).data.recordsUpdated)

        val result2 = reconciler.reconcile(legacyUser, "   ")
        assertTrue(result2 is Result.Success)
        assertEquals(0, (result2 as Result.Success).data.recordsUpdated)
    }

    @Test
    fun `reconcile migrates domain tables and profile without data loss`() = runBlocking {
        // Insert data via DAO
        insertProfile(legacyUser)
        insertGoal("g1", legacyUser)
        insertGlobalAutonomyState(legacyUser)
        insertAutonomySetting(legacyUser, "INTERVENTION_GENERATION", "SUGGEST_ONLY")
        insertRecommendation("rec1", legacyUser)
        insertBarrier("bar1", legacyUser)

        // Verify initial state
        assertTrue(profileExists(legacyUser))
        assertEquals(1, goalCount(legacyUser))
        assertEquals(0, goalCount(targetUser))

        // Execute reconciliation
        val result = reconciler.reconcile(legacyUser, targetUser)
        assertTrue("Reconciliation must succeed", result is Result.Success)
        val recResult = (result as Result.Success).data
        assertTrue(recResult.isReconciled)
        assertTrue("Must migrate at least 1 record", recResult.recordsUpdated > 0)

        // Verify legacy user has no data
        assertFalse(profileExists(legacyUser))
        assertEquals(0, goalCount(legacyUser))
        assertEquals(0, tableRowCount("global_autonomy_state", "userId", legacyUser))
        assertEquals(0, tableRowCount("autonomy_settings", "userId", legacyUser))

        // Verify target user has the data
        assertTrue(profileExists(targetUser))
        assertEquals(1, goalCount(targetUser))
        assertEquals(1, tableRowCount("global_autonomy_state", "userId", targetUser))
        assertEquals(1, tableRowCount("autonomy_settings", "userId", targetUser))
        assertEquals(1, tableRowCount("intervention_recommendations", "userId", targetUser))
        assertEquals(1, tableRowCount("barrier_hypotheses", "userId", targetUser))
    }

    @Test
    fun `reconcile handles existing settings under target UID without constraint violation`() = runBlocking {
        // Both legacy and target already have a profile row
        insertProfile(legacyUser, name = "Custom Name", tz = "Asia/Kolkata")
        insertProfile(targetUser, name = "", tz = "")

        // Both have autonomy_settings for capability C1, and legacy has unique capability C2
        insertAutonomySetting(legacyUser, "INTERVENTION_GENERATION", "SUGGEST_ONLY")
        insertAutonomySetting(legacyUser, "PROTECTION_AUTOMATION", "FULL_AUTO")
        insertAutonomySetting(targetUser, "INTERVENTION_GENERATION", "ASK_BEFORE_ACTION")

        // Both have global_autonomy_state (legacy had pause active)
        insertGlobalAutonomyState(legacyUser, pauseActive = true)
        insertGlobalAutonomyState(targetUser, pauseActive = false)

        // Execute reconciliation
        val result = reconciler.reconcile(legacyUser, targetUser)
        assertTrue("Reconciliation must succeed without SQLite constraint violation", result is Result.Success)

        // Assert user_profile merged: legacy custom displayName merged into target, legacy profile deleted
        assertFalse(profileExists(legacyUser))
        assertTrue(profileExists(targetUser))
        val mergedProfile = db.ironMindDao().getUserProfile(targetUser)!!
        assertEquals("Custom Name", mergedProfile.displayName)
        assertEquals("Asia/Kolkata", mergedProfile.timezone)

        // Assert autonomy_settings merged: target retains its own C1, adopts C2 from legacy, legacy deleted
        assertEquals(0, tableRowCount("autonomy_settings", "userId", legacyUser))
        assertEquals(2, tableRowCount("autonomy_settings", "userId", targetUser))

        // Assert global_autonomy_state preserved pause active from legacy
        assertEquals(0, tableRowCount("global_autonomy_state", "userId", legacyUser))
        assertEquals(1, tableRowCount("global_autonomy_state", "userId", targetUser))
        val cursor = db.query(
            SimpleSQLiteQuery(
                "SELECT isGlobalPauseActive FROM global_autonomy_state WHERE userId = ?",
                arrayOf(targetUser)
            )
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun `reconcile only updates userId in outbox payload without mutating title containing legacy ID`() = runBlocking {
        val payloadWithUserIdAndTitle = """{"entityId":"g1","userId":"user-1","title":"Follow up with user-1 on project"}"""
        val payloadOther = """{"entityId":"g2","userId":"another-user","title":"user-1 in another user title"}"""

        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO outbox (operationId, entityType, entityId, operationType, payload, createdAt, retryCount, lastAttemptAt, status, uploadedLocalRevision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("op1", "GOAL", "g1", "CREATE", payloadWithUserIdAndTitle, 1000L, 0, null, "PENDING", 1L)
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO outbox (operationId, entityType, entityId, operationType, payload, createdAt, retryCount, lastAttemptAt, status, uploadedLocalRevision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("op2", "GOAL", "g2", "CREATE", payloadOther, 1000L, 0, null, "PENDING", 1L)
        )

        val result = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(result is Result.Success)

        val cursor = db.query(
            SimpleSQLiteQuery("SELECT operationId, payload FROM outbox ORDER BY operationId ASC")
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("op1", it.getString(0))
            val json1 = JSONObject(it.getString(1))
            assertEquals(targetUser, json1.getString("userId"))
            // Critical check: title containing "user-1" must NOT have been mutated!
            assertEquals("Follow up with user-1 on project", json1.getString("title"))

            assertTrue(it.moveToNext())
            assertEquals("op2", it.getString(0))
            val json2 = JSONObject(it.getString(1))
            // Other user's payload must remain completely untouched
            assertEquals("another-user", json2.getString("userId"))
            assertEquals("user-1 in another user title", json2.getString("title"))
        }
    }

    @Test
    fun `reconcile preserves whitespace, escaped characters and nested values in outbox payloads`() = runBlocking {
        val complexPayload = """
        {
            "userId" : "user-1",
            "escapedText" : "Hello \"World\"\nNew line\tTabbed",
            "metadata" : {
                "active" : true,
                "count" : 42,
                "tags" : ["tag1", "tag2"]
            }
        }
        """.trimIndent()

        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO outbox (operationId, entityType, entityId, operationType, payload, createdAt, retryCount, lastAttemptAt, status, uploadedLocalRevision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("op_complex", "GOAL", "g3", "CREATE", complexPayload, 1000L, 0, null, "PENDING", 1L)
        )

        val result = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(result is Result.Success)

        val cursor = db.query(
            SimpleSQLiteQuery("SELECT payload FROM outbox WHERE operationId = 'op_complex'")
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            val json = JSONObject(it.getString(0))
            assertEquals(targetUser, json.getString("userId"))
            assertEquals("Hello \"World\"\nNew line\tTabbed", json.getString("escapedText"))
            val meta = json.getJSONObject("metadata")
            assertTrue(meta.getBoolean("active"))
            assertEquals(42, meta.getInt("count"))
            assertEquals(2, meta.getJSONArray("tags").length())
        }
    }

    @Test
    fun `reconcile preserves malformed or non-object outbox payload without discarding record`() = runBlocking {
        val malformedPayload = "THIS IS NOT A VALID JSON {{{[ user-1"

        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO outbox (operationId, entityType, entityId, operationType, payload, createdAt, retryCount, lastAttemptAt, status, uploadedLocalRevision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("op_malformed", "GOAL", "g4", "CREATE", malformedPayload, 1000L, 0, null, "PENDING", 1L)
        )

        val result = reconciler.reconcile(legacyUser, targetUser)
        assertTrue("Reconciliation should still succeed and preserve malformed outbox payload", result is Result.Success)

        val cursor = db.query(
            SimpleSQLiteQuery("SELECT payload, status FROM outbox WHERE operationId = 'op_malformed'")
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Malformed payload must remain completely intact", malformedPayload, it.getString(0))
            assertEquals("Status must remain unchanged", "PENDING", it.getString(1))
        }
    }

    @Test
    fun `reconcile detects pattern fingerprint collision and rolls back transaction preserving both records`() = runBlocking {
        // Drop unique index temporarily to insert both colliding records across legacy and target users
        db.openHelper.writableDatabase.execSQL("DROP INDEX IF EXISTS index_patterns_fingerprint")
        try {
            insertProfile(legacyUser, name = "Pattern User")
            insertPattern("p1", legacyUser, "COLLIDING_FINGERPRINT_100")
            insertPattern("p2", targetUser, "COLLIDING_FINGERPRINT_100")

            val result = reconciler.reconcile(legacyUser, targetUser)
            assertTrue("Must fail explicitly on pattern fingerprint collision", result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(
                "Error must be PatternFingerprintCollisionException",
                error is PatternFingerprintCollisionException
            )
            val collisionEx = error as PatternFingerprintCollisionException
            assertTrue(
                "Conflicting fingerprint must be reported",
                collisionEx.conflictingFingerprints.contains("COLLIDING_FINGERPRINT_100")
            )

            // Critical safety verification: full rollback occurred!
            // Both patterns must remain intact with their original user IDs
            val p1 = db.patternDao().getPatternById("p1")
            assertNotNull(p1)
            assertEquals(legacyUser, p1!!.userId)

            val p2 = db.patternDao().getPatternById("p2")
            assertNotNull(p2)
            assertEquals(targetUser, p2!!.userId)

            // Legacy profile must also remain intact due to atomic rollback
            assertTrue("Legacy profile must be restored on rollback", profileExists(legacyUser))
            assertFalse("Target profile must not have been created/merged", profileExists(targetUser))
        } finally {
            db.openHelper.writableDatabase.execSQL("DELETE FROM patterns")
            db.openHelper.writableDatabase.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_patterns_fingerprint ON patterns(fingerprint)")
        }
    }

    @Test
    fun `reconcile migrates patterns when no fingerprint collision exists and is idempotent`() = runBlocking {
        insertPattern("p1", legacyUser, "UNIQUE_FINGERPRINT_A")
        insertPattern("p2", targetUser, "UNIQUE_FINGERPRINT_B")

        val run1 = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(run1 is Result.Success)

        // Both patterns now belong to targetUser
        assertEquals(targetUser, db.patternDao().getPatternById("p1")?.userId)
        assertEquals(targetUser, db.patternDao().getPatternById("p2")?.userId)

        // Repeat run is idempotent
        val run2 = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(run2 is Result.Success)
        assertEquals(0, (run2 as Result.Success).data.recordsUpdated)
        assertEquals(targetUser, db.patternDao().getPatternById("p1")?.userId)
    }

    @Test
    fun `reconcile is idempotent on repeated execution`() = runBlocking {
        insertGoal("g1", legacyUser)

        val run1 = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(run1 is Result.Success)
        assertTrue((run1 as Result.Success).data.recordsUpdated > 0)
        assertEquals(1, goalCount(targetUser))
        assertEquals(0, goalCount(legacyUser))

        // Second run with same parameters: no remaining legacy records to update
        val run2 = reconciler.reconcile(legacyUser, targetUser)
        assertTrue(run2 is Result.Success)
        assertEquals(0, (run2 as Result.Success).data.recordsUpdated)
        assertEquals(1, goalCount(targetUser))
    }

    @Test
    fun `reconcile rolls back all database changes completely if a failure occurs mid-transaction`() = runBlocking {
        insertProfile(legacyUser, name = "Rollback User")
        insertGoal("g1", legacyUser)

        // Install a temporary SQLite trigger to intentionally abort the transaction when updating goals
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER test_abort BEFORE UPDATE ON goal BEGIN SELECT RAISE(ABORT, 'Simulated mid-transaction failure'); END;"
        )

        try {
            val result = reconciler.reconcile(legacyUser, targetUser)
            assertTrue("Reconciliation must fail and return Result.Failure", result is Result.Failure)

            // Verify full rollback: legacy profile and goal must still exist, target must have nothing
            assertTrue("Legacy profile must be restored on rollback", profileExists(legacyUser))
            assertFalse("Target profile must not exist after rollback", profileExists(targetUser))
            assertEquals("Legacy goal must remain after rollback", 1, goalCount(legacyUser))
            assertEquals("Target goal must not exist after rollback", 0, goalCount(targetUser))
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS test_abort")
        }
    }
}
