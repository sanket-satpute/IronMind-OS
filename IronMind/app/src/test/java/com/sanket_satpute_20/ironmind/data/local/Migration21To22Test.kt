package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.runners.model.FrameworkMethod
import org.robolectric.annotation.Config

class Jdk25CompatibleRobolectricTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun finallyAfterTest(method: FrameworkMethod) {
        try {
            super.finallyAfterTest(method)
        } catch (e: RuntimeException) {
            if (e.message?.contains("Some resetters failed") == true) {
                // Robolectric 4.13 + JDK 25 bytecode instrumenter webkit resetter workaround
            } else {
                throw e
            }
        }
    }
}

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Migration21To22Test {

    private lateinit var context: Context
    private val dbName = "migration_21_22_test.db"

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
    fun testRealMigration21To22_validatesSchema_preservesData_exercisesDao() = runBlocking {
        // Step 1: Construct a genuine version-1 database using the real initial schema
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

        // Step 2: Apply production migrations MIGRATION_1_2 through MIGRATION_20_21 sequentially
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
            IronMindDatabase.MIGRATION_20_21
        )

        for (migration in historicalMigrations) {
            migration.migrate(rawDb)
        }
        rawDb.version = 21

        // Seed representative rows in events and intervention_recommendations
        rawDb.execSQL(
            """
            INSERT INTO `events` (
                `id`, `userId`, `type`, `entityType`, `entityId`, `occurredAt`, `recordedAt`,
                `processedAt`, `source`, `previousState`, `newState`, `metadata`, `correlationId`, `causationId`, `schemaVersion`
            ) VALUES (
                'event-legacy-1', 'user-123', 'INTERVENTION_OVERRIDDEN', 'Intervention', 'rec-legacy-1', 1000000, 1000000,
                NULL, 'SYSTEM', NULL, NULL, 'scope=INTERVENTION_CORRECTION,type=HABIT,recommendation=Drink water,correctedText=Take medication', NULL, 'rec-legacy-1', 1
            )
            """.trimIndent()
        )

        rawDb.execSQL(
            """
            INSERT INTO `intervention_recommendations` (
                `id`, `userId`, `interventionType`, `targetEntityId`, `targetEntityType`,
                `rationale`, `suggestedAction`, `status`, `createdAt`, `expiresAt`, `objective`
            ) VALUES (
                'rec-legacy-1', 'user-123', 'HABIT', 'task-1', 'Task',
                'Hydration prompt', 'Drink water', 'PENDING', 1000000, 2000000, 'Stay hydrated'
            )
            """.trimIndent()
        )

        rawDb.close()
        helper.close()

        // Step 3: Open the database through Room with production migration chain including MIGRATION_21_22
        val roomDb = Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            dbName
        ).addMigrations(
            *historicalMigrations.toTypedArray(),
            IronMindDatabase.MIGRATION_21_22,
            IronMindDatabase.MIGRATION_22_23
        ).build()

        // Step 4: Exercise Room's normal schema-validation path (RoomOpenHelper.onValidateSchema)
        val writableDb = roomDb.openHelper.writableDatabase

        // Verify version is 23
        val versionCursor = writableDb.query("PRAGMA user_version")
        assertTrue(versionCursor.moveToFirst())
        assertEquals(23, versionCursor.getInt(0))
        versionCursor.close()

        // Step 5: Verify the version-22 schema and all three indexes in sqlite_master
        val indexCursor = writableDb.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'learner_event_status'")
        val foundIndexes = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            foundIndexes.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue("index_learner_event_status_poll must exist", foundIndexes.contains("index_learner_event_status_poll"))
        assertTrue("index_learner_event_status_lease must exist", foundIndexes.contains("index_learner_event_status_lease"))
        assertTrue("index_learner_event_status_user must exist", foundIndexes.contains("index_learner_event_status_user"))

        // Step 6: Verify seeded legacy data remains intact
        val eventCursor = writableDb.query("SELECT id, userId, type, causationId FROM `events` WHERE id = 'event-legacy-1'")
        assertTrue("Seeded event must be preserved", eventCursor.moveToFirst())
        assertEquals("user-123", eventCursor.getString(1))
        assertEquals("INTERVENTION_OVERRIDDEN", eventCursor.getString(2))
        assertEquals("rec-legacy-1", eventCursor.getString(3))
        eventCursor.close()

        val recCursor = writableDb.query("SELECT id, objective, status FROM `intervention_recommendations` WHERE id = 'rec-legacy-1'")
        assertTrue("Seeded recommendation must be preserved", recCursor.moveToFirst())
        assertEquals("Stay hydrated", recCursor.getString(1))
        assertEquals("PENDING", recCursor.getString(2))
        recCursor.close()

        // Step 7: Exercise DAO insert/read and token-fenced CAS behavior
        val dao = roomDb.learnerEventStatusDao()

        val initialStatus = LearnerEventStatusEntity(
            eventId = "event-test-1",
            userId = "user-123",
            status = "PENDING",
            claimCount = 0,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = null,
            leaseExpiresAt = null,
            lastAttemptAt = null,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(initialStatus)

        val loaded = dao.getEventStatus("event-test-1")
        assertNotNull(loaded)
        assertEquals("PENDING", loaded?.status)
        assertEquals(0, loaded?.claimCount)

        // Claim pending event
        val claimResult = dao.claimPendingEvent(
            eventId = "event-test-1",
            now = 2000L,
            leaseExpiresAt = 5000L,
            newLeaseToken = "token-abc"
        )
        assertEquals(1, claimResult)

        val claimed = dao.getEventStatus("event-test-1")
        assertEquals("PROCESSING", claimed?.status)
        assertEquals(1, claimed?.claimCount)
        assertEquals("token-abc", claimed?.leaseToken)
        assertEquals(5000L, claimed?.leaseExpiresAt)

        // Stale lease token attempt must affect 0 rows
        val staleCompleteResult = dao.completeEvent(
            eventId = "event-test-1",
            myLeaseToken = "stale-token",
            now = 3000L
        )
        assertEquals(0, staleCompleteResult)

        // Valid lease token completes successfully
        val validCompleteResult = dao.completeEvent(
            eventId = "event-test-1",
            myLeaseToken = "token-abc",
            now = 3000L
        )
        assertEquals(1, validCompleteResult)

        val completed = dao.getEventStatus("event-test-1")
        assertEquals("SUCCEEDED", completed?.status)
        assertNull(completed?.leaseToken)

        // Test second entity for failure and cancellation paths
        val status2 = LearnerEventStatusEntity(
            eventId = "event-test-2",
            userId = "user-123",
            status = "PENDING",
            claimCount = 0,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = null,
            leaseExpiresAt = null,
            lastAttemptAt = null,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(status2)

        dao.claimPendingEvent("event-test-2", 2000L, 5000L, "token-2")
        // Cooperative cancellation releases lease to PENDING without incrementing failureCount
        val releaseResult = dao.releaseLease("event-test-2", "token-2", 3000L)
        assertEquals(1, releaseResult)

        val released = dao.getEventStatus("event-test-2")
        assertEquals("PENDING", released?.status)
        assertEquals(1, released?.claimCount)
        assertEquals(0, released?.failureCount)
        assertNull(released?.leaseToken)

        // Claim again, then record retryable failure
        dao.claimPendingEvent("event-test-2", 4000L, 7000L, "token-2-retry")
        val failRetryResult = dao.failRetryable("event-test-2", "token-2-retry", 10000L, "TRANSIENT_ERROR", 4500L)
        assertEquals(1, failRetryResult)

        val failedRetry = dao.getEventStatus("event-test-2")
        assertEquals("FAILED_RETRYABLE", failedRetry?.status)
        assertEquals(2, failedRetry?.claimCount)
        assertEquals(1, failedRetry?.failureCount)
        assertEquals(10000L, failedRetry?.nextAttemptAt)
        assertEquals("TRANSIENT_ERROR", failedRetry?.errorMessage)

        // Test claim exhaustion sweep protection:
        // Set claimCount to maxAttempts on an active unexpired lease
        val status3 = LearnerEventStatusEntity(
            eventId = "event-test-3",
            userId = "user-123",
            status = "PROCESSING",
            claimCount = 5,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = "active-token",
            leaseExpiresAt = 50000L, // unexpired
            lastAttemptAt = 1000L,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(status3)

        // Sweeper must NOT touch active unexpired lease
        val sweepResultActive = dao.sweepClaimExhaustion(now = 20000L)
        assertEquals(0, sweepResultActive)
        assertEquals("PROCESSING", dao.getEventStatus("event-test-3")?.status)

        // Sweeper MUST terminally transition expired lease when claimCount >= maxAttempts
        val sweepResultExpired = dao.sweepClaimExhaustion(now = 60000L)
        assertEquals(1, sweepResultExpired)
        val swept = dao.getEventStatus("event-test-3")
        assertEquals("FAILED_PERMANENT", swept?.status)
        assertEquals("CLAIM_LIMIT_EXHAUSTED", swept?.errorMessage)
        assertNull(swept?.leaseToken)

        // -------------------------------------------------------------------------
        // Case 1: Claim boundary (claimCount = maxAttempts - 1)
        // Set claimCount = 4 with maxAttempts = 5. Claiming must succeed and reach exactly 5.
        // -------------------------------------------------------------------------
        val statusBoundary = LearnerEventStatusEntity(
            eventId = "event-test-boundary",
            userId = "user-123",
            status = "PENDING",
            claimCount = 4,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = null,
            leaseExpiresAt = null,
            lastAttemptAt = null,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusBoundary)

        val boundaryClaimResult = dao.claimPendingEvent(
            eventId = "event-test-boundary",
            now = 2000L,
            leaseExpiresAt = 5000L,
            newLeaseToken = "token-boundary"
        )
        assertEquals(1, boundaryClaimResult)
        val claimedBoundary = dao.getEventStatus("event-test-boundary")
        assertEquals("PROCESSING", claimedBoundary?.status)
        assertEquals(5, claimedBoundary?.claimCount)
        assertEquals("token-boundary", claimedBoundary?.leaseToken)

        // -------------------------------------------------------------------------
        // Case 2: Claim exhaustion (claimCount = maxAttempts)
        // Verify both pending claim and expired-lease reclamation return 0 affected rows.
        // -------------------------------------------------------------------------
        val statusExhaustedPending = LearnerEventStatusEntity(
            eventId = "event-test-exhausted-pending",
            userId = "user-123",
            status = "PENDING",
            claimCount = 5,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = null,
            leaseExpiresAt = null,
            lastAttemptAt = 1000L,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusExhaustedPending)

        val exhaustedPendingClaimResult = dao.claimPendingEvent(
            eventId = "event-test-exhausted-pending",
            now = 2000L,
            leaseExpiresAt = 5000L,
            newLeaseToken = "token-attempt"
        )
        assertEquals(0, exhaustedPendingClaimResult)
        assertEquals("PENDING", dao.getEventStatus("event-test-exhausted-pending")?.status)
        assertEquals(5, dao.getEventStatus("event-test-exhausted-pending")?.claimCount)

        val statusExhaustedExpired = LearnerEventStatusEntity(
            eventId = "event-test-exhausted-expired",
            userId = "user-123",
            status = "PROCESSING",
            claimCount = 5,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = "old-token-exhausted",
            leaseExpiresAt = 1000L,
            lastAttemptAt = 1000L,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusExhaustedExpired)

        val exhaustedExpiredClaimResult = dao.claimExpiredProcessingEvent(
            eventId = "event-test-exhausted-expired",
            now = 2000L,
            leaseExpiresAt = 5000L,
            newLeaseToken = "token-attempt"
        )
        assertEquals(0, exhaustedExpiredClaimResult)
        assertEquals("PROCESSING", dao.getEventStatus("event-test-exhausted-expired")?.status)
        assertEquals(5, dao.getEventStatus("event-test-exhausted-expired")?.claimCount)

        // -------------------------------------------------------------------------
        // Case 3: Expired lease reclamation
        // Create PROCESSING with expired lease. Reclamation succeeds, establishes new lease.
        // -------------------------------------------------------------------------
        val statusReclamation = LearnerEventStatusEntity(
            eventId = "event-test-reclamation",
            userId = "user-123",
            status = "PROCESSING",
            claimCount = 1,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = "old-stale-token",
            leaseExpiresAt = 1500L,
            lastAttemptAt = 1000L,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusReclamation)

        val reclaimResult = dao.claimExpiredProcessingEvent(
            eventId = "event-test-reclamation",
            now = 2000L,
            leaseExpiresAt = 6000L,
            newLeaseToken = "new-active-token"
        )
        assertEquals(1, reclaimResult)
        val reclaimed = dao.getEventStatus("event-test-reclamation")
        assertEquals("PROCESSING", reclaimed?.status)
        assertEquals(2, reclaimed?.claimCount)
        assertEquals("new-active-token", reclaimed?.leaseToken)
        assertEquals(6000L, reclaimed?.leaseExpiresAt)

        // -------------------------------------------------------------------------
        // Case 4: Stale-token fencing after reclamation
        // Verify completion, retryable failure, permanent failure, and lease release
        // using the old lease token each affect 0 rows, and new lease remains unchanged.
        // -------------------------------------------------------------------------
        val staleComplete = dao.completeEvent("event-test-reclamation", "old-stale-token", now = 3000L)
        assertEquals(0, staleComplete)

        val staleRetry = dao.failRetryable("event-test-reclamation", "old-stale-token", 10000L, "err", now = 3000L)
        assertEquals(0, staleRetry)

        val stalePerm = dao.failPermanent("event-test-reclamation", "old-stale-token", "err", now = 3000L)
        assertEquals(0, stalePerm)

        val staleRelease = dao.releaseLease("event-test-reclamation", "old-stale-token", now = 3000L)
        assertEquals(0, staleRelease)

        val afterStaleAttempts = dao.getEventStatus("event-test-reclamation")
        assertEquals("PROCESSING", afterStaleAttempts?.status)
        assertEquals("new-active-token", afterStaleAttempts?.leaseToken)
        assertEquals(6000L, afterStaleAttempts?.leaseExpiresAt)
        assertEquals(0, afterStaleAttempts?.failureCount)
        assertEquals(2, afterStaleAttempts?.claimCount)

        // -------------------------------------------------------------------------
        // Case 5: Permanent failure and terminal state overwrite protection
        // Current valid token transitions to FAILED_PERMANENT. Subsequent worker writes return 0.
        // -------------------------------------------------------------------------
        val statusPermFail = LearnerEventStatusEntity(
            eventId = "event-test-perm-fail",
            userId = "user-123",
            status = "PROCESSING",
            claimCount = 1,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = "perm-valid-token",
            leaseExpiresAt = 10000L,
            lastAttemptAt = 1000L,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusPermFail)

        val permFailResult = dao.failPermanent("event-test-perm-fail", "perm-valid-token", "FATAL_ERROR", now = 4000L)
        assertEquals(1, permFailResult)

        val loadedPerm = dao.getEventStatus("event-test-perm-fail")
        assertEquals("FAILED_PERMANENT", loadedPerm?.status)
        assertEquals(1, loadedPerm?.failureCount)
        assertEquals("FATAL_ERROR", loadedPerm?.errorMessage)
        assertNull(loadedPerm?.leaseToken)

        // Subsequent worker-owned mutations cannot overwrite terminal state
        assertEquals(0, dao.completeEvent("event-test-perm-fail", "perm-valid-token", now = 5000L))
        assertEquals(0, dao.failRetryable("event-test-perm-fail", "perm-valid-token", 20000L, "retry", now = 5000L))
        assertEquals(0, dao.failPermanent("event-test-perm-fail", "perm-valid-token", "perm", now = 5000L))
        assertEquals(0, dao.releaseLease("event-test-perm-fail", "perm-valid-token", now = 5000L))
        assertEquals(0, dao.claimPendingEvent("event-test-perm-fail", now = 5000L, leaseExpiresAt = 10000L, newLeaseToken = "token"))
        assertEquals(0, dao.claimExpiredProcessingEvent("event-test-perm-fail", now = 5000L, leaseExpiresAt = 10000L, newLeaseToken = "token"))

        val stillPerm = dao.getEventStatus("event-test-perm-fail")
        assertEquals("FAILED_PERMANENT", stillPerm?.status)
        assertEquals(1, stillPerm?.failureCount)

        // -------------------------------------------------------------------------
        // Case 6: Failure-count integrity
        // Failed CAS does not increment failureCount; successful retryable failure increments it exactly once.
        // -------------------------------------------------------------------------
        val statusIntegrity = LearnerEventStatusEntity(
            eventId = "event-test-failure-integrity",
            userId = "user-123",
            status = "PROCESSING",
            claimCount = 1,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = "valid-integrity-token",
            leaseExpiresAt = 10000L,
            lastAttemptAt = 1000L,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusIntegrity)

        // Failed CAS with invalid token must not increment failureCount
        val failedCasResult = dao.failRetryable("event-test-failure-integrity", "invalid-token", 15000L, "err", now = 2000L)
        assertEquals(0, failedCasResult)
        assertEquals(0, dao.getEventStatus("event-test-failure-integrity")?.failureCount)

        // Successful CAS with valid token increments failureCount exactly once
        val successCasResult = dao.failRetryable("event-test-failure-integrity", "valid-integrity-token", 15000L, "TRANSIENT_ERR", now = 2000L)
        assertEquals(1, successCasResult)
        val loadedIntegrity = dao.getEventStatus("event-test-failure-integrity")
        assertEquals("FAILED_RETRYABLE", loadedIntegrity?.status)
        assertEquals(1, loadedIntegrity?.failureCount)
        assertEquals(15000L, loadedIntegrity?.nextAttemptAt)

        // -------------------------------------------------------------------------
        // Case 7: Concurrency
        // Issue concurrent claims against the same eligible event.
        // Exactly one claim succeeds and claimCount never exceeds maxAttempts.
        // -------------------------------------------------------------------------
        val statusConcurrent = LearnerEventStatusEntity(
            eventId = "event-test-concurrent",
            userId = "user-123",
            status = "PENDING",
            claimCount = 4,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = null,
            leaseExpiresAt = null,
            lastAttemptAt = null,
            nextAttemptAt = 1000L,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insert(statusConcurrent)

        val concurrentDeferreds = (1..10).map { workerId ->
            async(Dispatchers.Default) {
                dao.claimPendingEvent(
                    eventId = "event-test-concurrent",
                    now = 2000L,
                    leaseExpiresAt = 5000L,
                    newLeaseToken = "concurrent-token-$workerId"
                )
            }
        }
        val results = concurrentDeferreds.awaitAll()
        assertEquals("Exactly one worker must succeed in claiming the event", 1, results.sum())
        assertEquals(1, results.count { it == 1 })
        assertEquals(9, results.count { it == 0 })

        val finalConcurrent = dao.getEventStatus("event-test-concurrent")
        assertEquals("PROCESSING", finalConcurrent?.status)
        assertEquals("claimCount must reach exactly maxAttempts and never exceed it", 5, finalConcurrent?.claimCount)

        // Step 8: Close database safely
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
