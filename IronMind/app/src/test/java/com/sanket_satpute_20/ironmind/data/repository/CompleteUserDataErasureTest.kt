package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.BarrierDao
import com.sanket_satpute_20.ironmind.data.local.dao.InterventionRecommendationDao
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.*
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer
import com.sanket_satpute_20.ironmind.domain.common.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class CompleteUserDataErasureTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var ironMindDao: IronMindDao
    private lateinit var recommendationDao: InterventionRecommendationDao
    private lateinit var barrierDao: BarrierDao
    private lateinit var repository: DataManagementRepositoryImpl

    private val user1 = "user-1"
    private val user2 = "user-2"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ironMindDao = db.ironMindDao()
        recommendationDao = db.interventionRecommendationDao()
        barrierDao = db.barrierDao()

        repository = DataManagementRepositoryImpl(
            ironMindDao = ironMindDao,
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            learnerEventStatusDao = db.learnerEventStatusDao(),
            database = db,
            workManager = null,
            interventionRecommendationDao = recommendationDao,
            barrierDao = barrierDao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertUserProfile(userId: String) {
        ironMindDao.insertUserProfile(
            UserProfileEntity(
                id = userId,
                createdAt = 1000L,
                updatedAt = 1000L,
                displayName = "User $userId",
                timezone = "UTC",
                createdFrom = "ONBOARDING",
                status = "ACTIVE",
                schemaVersion = 1
            )
        )
    }

    private fun createRecommendation(id: String, userId: String, status: String = "PENDING"): InterventionRecommendationEntity {
        return InterventionRecommendationEntity(
            id = id,
            userId = userId,
            interventionType = "BREAK_DOWN",
            objective = "ACTION_PROGRESSION",
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "User needs progression",
            suggestedAction = "Take smaller step",
            status = status,
            createdAt = 1000L,
            expiresAt = 9999999L,
            causalHypothesisId = "pat-1",
            causalHypothesisType = "PATTERN"
        )
    }

    private fun createBarrier(id: String, userId: String): BarrierHypothesisEntity {
        return BarrierHypothesisEntity(
            id = id,
            userId = userId,
            category = "ENERGY",
            description = "Afternoon fatigue barrier",
            confirmationState = "UNCONFIRMED",
            status = "ACTIVE",
            sourceReflectionId = "ref-1",
            firstObservedAt = 1000L,
            lastObservedAt = 2000L
        )
    }

    @Test
    fun erasingUser_removesInterventionRecommendationsForThatUser() = runBlocking {
        insertUserProfile(user1)
        recommendationDao.insert(createRecommendation("rec-1", user1))
        recommendationDao.insert(createRecommendation("rec-2", user1))

        assertNotNull(recommendationDao.getById("rec-1"))
        assertNotNull(recommendationDao.getById("rec-2"))

        val result = repository.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        assertNull(recommendationDao.getById("rec-1"))
        assertNull(recommendationDao.getById("rec-2"))
    }

    @Test
    fun erasingUser_removesBarrierHypothesesForThatUser() = runBlocking {
        insertUserProfile(user1)
        barrierDao.insert(createBarrier("bar-1", user1))
        barrierDao.insert(createBarrier("bar-2", user1))

        val preBarriers = barrierDao.getBarriersByUserId(user1)
        assertEquals(2, preBarriers.size)

        val result = repository.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        val postBarriers = barrierDao.getBarriersByUserId(user1)
        assertEquals(0, postBarriers.size)
    }

    @Test
    fun erasingUser_preservesRecordsBelongingToAnotherUser() = runBlocking {
        insertUserProfile(user1)
        insertUserProfile(user2)

        recommendationDao.insert(createRecommendation("rec-user1", user1))
        recommendationDao.insert(createRecommendation("rec-user2", user2))

        barrierDao.insert(createBarrier("bar-user1", user1))
        barrierDao.insert(createBarrier("bar-user2", user2))

        val result = repository.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        // user1 wiped
        assertNull(recommendationDao.getById("rec-user1"))
        assertEquals(0, barrierDao.getBarriersByUserId(user1).size)

        // user2 preserved
        val user2Rec = recommendationDao.getById("rec-user2")
        assertNotNull(user2Rec)
        assertEquals(user2, user2Rec?.userId)

        val user2Barriers = barrierDao.getBarriersByUserId(user2)
        assertEquals(1, user2Barriers.size)
        assertEquals("bar-user2", user2Barriers[0].id)
    }

    @Test
    fun recreatedProfileWithSameUserId_cannotRetrieveOldPendingRecommendationsOrBarriers() = runBlocking {
        insertUserProfile(user1)
        recommendationDao.insert(createRecommendation("rec-old", user1, status = "PENDING"))
        barrierDao.insert(createBarrier("bar-old", user1))

        val prePending = recommendationDao.getPendingRecommendations(user1, 5000L).first()
        assertEquals(1, prePending.size)

        // User erases account
        val eraseResult = repository.deleteAllUserData(user1)
        assertTrue(eraseResult is Result.Success)

        // Recreate profile with same userId
        insertUserProfile(user1)

        // Verify pending recommendations query returns empty
        val postPending = recommendationDao.getPendingRecommendations(user1, 5000L).first()
        assertEquals(0, postPending.size)

        // Verify active barriers query returns empty
        val postBarriers = barrierDao.getActiveBarriersForUser(user1, emptyList(), 10)
        assertEquals(0, postBarriers.size)
    }

    @Test
    fun missingRecommendationDao_producesFailure_andNeverReportsSuccess() = runBlocking {
        val mockDb = mockk<IronMindDatabase>(relaxed = true)
        every { mockDb.interventionRecommendationDao() } throws IllegalStateException("Recommendation DAO missing")
        every { mockDb.barrierDao() } returns barrierDao

        val repo = DataManagementRepositoryImpl(
            ironMindDao = ironMindDao,
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            learnerEventStatusDao = db.learnerEventStatusDao(),
            database = mockDb,
            workManager = null,
            interventionRecommendationDao = null,
            barrierDao = barrierDao
        )

        val result = repo.deleteAllUserData(user1)
        assertTrue("Missing recommendation DAO must result in failure", result is Result.Failure)
        assertFalse("Must never report success when recommendation DAO is missing", result is Result.Success)
        val errorMsg = (result as Result.Failure).error.message
        assertTrue(
            "Expected failure message regarding InterventionRecommendationDao, got: $errorMsg",
            errorMsg?.contains("InterventionRecommendationDao") == true || errorMsg?.contains("Recommendation DAO missing") == true
        )
    }

    @Test
    fun missingBarrierDao_producesFailure_andNeverReportsSuccess() = runBlocking {
        val mockDb = mockk<IronMindDatabase>(relaxed = true)
        every { mockDb.interventionRecommendationDao() } returns recommendationDao
        every { mockDb.barrierDao() } throws IllegalStateException("Barrier DAO missing")

        val repo = DataManagementRepositoryImpl(
            ironMindDao = ironMindDao,
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            learnerEventStatusDao = db.learnerEventStatusDao(),
            database = mockDb,
            workManager = null,
            interventionRecommendationDao = recommendationDao,
            barrierDao = null
        )

        val result = repo.deleteAllUserData(user1)
        assertTrue("Missing barrier DAO must result in failure", result is Result.Failure)
        assertFalse("Must never report success when barrier DAO is missing", result is Result.Success)
        val errorMsg = (result as Result.Failure).error.message
        assertTrue(
            "Expected failure message regarding BarrierDao, got: $errorMsg",
            errorMsg?.contains("BarrierDao") == true || errorMsg?.contains("Barrier DAO missing") == true
        )
    }

    @Test
    fun missingDatabase_producesFailure_andNeverReportsSuccess() = runBlocking {
        val repo = DataManagementRepositoryImpl(
            ironMindDao = ironMindDao,
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            learnerEventStatusDao = db.learnerEventStatusDao(),
            database = null,
            workManager = null,
            interventionRecommendationDao = recommendationDao,
            barrierDao = barrierDao
        )

        val result = repo.deleteAllUserData(user1)
        assertTrue("Missing database must produce Result.Failure", result is Result.Failure)
        assertFalse("Must never report success when database is missing", result is Result.Success)
        assertTrue((result as Result.Failure).error.message?.contains("Database") == true)
    }

    @Test
    fun productionWiring_viaAppContainer_succeeds() = runBlocking {
        val appContainer = DefaultAppContainer(context)
        val repo = appContainer.dataManagementRepository

        insertUserProfile(user1)
        val result = repo.deleteAllUserData(user1)
        assertTrue("Production wiring through AppContainer must succeed", result is Result.Success)
    }

    @Test
    fun transactionRollback_whenLaterDeletionFails_restoresEarlierDeletedRecords() = runBlocking {
        // 1. Seed actual database records for user1:
        // - user profile (deleted in Step 1)
        insertUserProfile(user1)
        // - goal (deleted in Step 2)
        val goal1 = GoalEntity(
            id = "goal-1",
            userId = user1,
            ambitionId = null,
            title = "Target Goal",
            description = "Goal to test rollback",
            why = "Integrity",
            importance = 1,
            status = "ACTIVE",
            targetAt = null,
            startedAt = null,
            completedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            schemaVersion = 1
        )
        ironMindDao.insertGoal(goal1)
        // - recommendation (deleted in Step 11)
        recommendationDao.insert(createRecommendation("rec-1", user1))
        // - barrier (deleted in Step 12)
        barrierDao.insert(createBarrier("bar-1", user1))

        // Also seed records for user2 to verify other-user data remains intact
        insertUserProfile(user2)
        val goal2 = goal1.copy(id = "goal-2", userId = user2)
        ironMindDao.insertGoal(goal2)
        recommendationDao.insert(createRecommendation("rec-user2", user2))
        barrierDao.insert(createBarrier("bar-user2", user2))

        // Verify initial state
        assertNotNull(ironMindDao.getUserProfile(user1))
        assertNotNull(ironMindDao.getGoal("goal-1"))
        assertNotNull(recommendationDao.getById("rec-1"))
        assertEquals(1, barrierDao.getBarriersByUserId(user1).size)

        // 2. Install a SQLite trigger on barrier_hypotheses that aborts deletion.
        // In executeAllDeletions, user_profile, goal, and intervention_recommendations are deleted BEFORE barrier_hypotheses.
        // When bDao.deleteBarriersForUser(user1) is invoked, the trigger fires and aborts inside the Room transaction.
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER abort_barrier_deletion
            BEFORE DELETE ON barrier_hypotheses
            BEGIN
                SELECT RAISE(ABORT, 'Simulated failure during barrier deletion');
            END;
            """.trimIndent()
        )

        // 3. Invoke real deleteAllUserData path
        val result = repository.deleteAllUserData(user1)

        // 4. Assert result is failure and was not silently reported as success
        assertTrue("Erasure failure must return Result.Failure", result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(
            "Failure message must reflect the trigger failure: ${error.message}",
            error.message?.contains("Simulated failure during barrier deletion") == true ||
                error.cause?.message?.contains("Simulated failure during barrier deletion") == true
        )

        // 5. Query the database after the transaction has unwound:
        // Confirm user profile (Step 1) survived due to transaction rollback
        assertNotNull("User profile must survive via rollback", ironMindDao.getUserProfile(user1))
        // Confirm goal (Step 2) survived due to transaction rollback
        assertNotNull("Goal must survive via rollback", ironMindDao.getGoal("goal-1"))
        // Confirm recommendation (Step 11) survived due to transaction rollback
        assertNotNull("Intervention recommendation must survive via rollback", recommendationDao.getById("rec-1"))
        // Confirm barrier (Step 12, trigger point) survived
        val barriersUser1 = barrierDao.getBarriersByUserId(user1)
        assertEquals(1, barriersUser1.size)
        assertEquals("bar-1", barriersUser1[0].id)

        // 6. Verify unrelated user data remains completely intact
        assertNotNull(ironMindDao.getUserProfile(user2))
        assertNotNull(ironMindDao.getGoal("goal-2"))
        assertNotNull(recommendationDao.getById("rec-user2"))
        assertEquals(1, barrierDao.getBarriersByUserId(user2).size)
    }

    @Test
    fun `erasingUser_removesOutcomesForThatUser_andPreservesOtherUsers`() = runBlocking {
        insertUserProfile(user1)
        insertUserProfile(user2)

        val outcome1 = OutcomeEntity(
            id = "out-1",
            userId = user1,
            sourceEntityId = "task-1",
            sourceEntityType = "TASK",
            resultStatus = "COMPLETED",
            actualDurationMinutes = 25,
            completedAt = 1000L,
            createdAt = 1000L,
            schemaVersion = 1
        )
        val outcome2 = outcome1.copy(id = "out-2", userId = user2)
        ironMindDao.insertOutcome(outcome1)
        ironMindDao.insertOutcome(outcome2)

        assertNotNull(ironMindDao.getOutcome("out-1"))
        assertNotNull(ironMindDao.getOutcome("out-2"))

        val result = repository.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        assertNull("user1 outcome must be erased", ironMindDao.getOutcome("out-1"))
        assertNotNull("user2 outcome must be preserved", ironMindDao.getOutcome("out-2"))
    }

    @Test
    fun `erasingUser_removesObservationSettingsForThatUser_andPreservesOtherUsers`() = runBlocking {
        insertUserProfile(user1)
        insertUserProfile(user2)

        val appDao = db.appUsageObservationSettingsDao()
        val notifDao = db.notificationObservationSettingsDao()
        val calDao = db.calendarObservationSettingsDao()
        val locDao = db.locationObservationSettingsDao()
        val actDao = db.activityObservationSettingsDao()

        appDao.upsertSettings(AppUsageObservationSettingsEntity(userId = user1, isEnabled = true, updatedAt = 1000L))
        appDao.upsertSettings(AppUsageObservationSettingsEntity(userId = user2, isEnabled = true, updatedAt = 1000L))

        notifDao.upsertSettings(NotificationObservationSettingsEntity(userId = user1, isEnabled = true, updatedAt = 1000L))
        notifDao.upsertSettings(NotificationObservationSettingsEntity(userId = user2, isEnabled = true, updatedAt = 1000L))

        calDao.insertOrUpdate(CalendarObservationSettingsEntity(userId = user1, isEnabled = true, updatedAt = 1000L))
        calDao.insertOrUpdate(CalendarObservationSettingsEntity(userId = user2, isEnabled = true, updatedAt = 1000L))

        locDao.insertOrUpdate(LocationObservationSettingsEntity(userId = user1, isEnabled = true, updatedAt = 1000L))
        locDao.insertOrUpdate(LocationObservationSettingsEntity(userId = user2, isEnabled = true, updatedAt = 1000L))

        actDao.insertOrUpdateSettings(ActivityObservationSettingsEntity(userId = user1, isEnabled = true, lastUpdatedAt = 1000L))
        actDao.insertOrUpdateSettings(ActivityObservationSettingsEntity(userId = user2, isEnabled = true, lastUpdatedAt = 1000L))

        val result = repository.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        // user1 cleared
        assertNull(appDao.getSettingsForUser(user1))
        assertNull(notifDao.getSettings(user1))
        assertNull(calDao.getSettings(user1))
        assertNull(locDao.getSettings(user1))
        assertNull(actDao.getSettings(user1))

        // user2 preserved
        assertNotNull(appDao.getSettingsForUser(user2))
        assertNotNull(notifDao.getSettings(user2))
        assertNotNull(calDao.getSettings(user2))
        assertNotNull(locDao.getSettings(user2))
        assertNotNull(actDao.getSettings(user2))
    }

    @Test
    fun `erasingUser_deletesTargetUserOutboxEntries_preservesForeignAndAmbiguous`() = runBlocking {
        insertUserProfile(user1)
        insertUserProfile(user2)

        val outboxDao = db.outboxDao()

        val opUser1 = OutboxEntity(
            operationId = "op-1",
            entityType = "Goal",
            entityId = "g-1",
            operationType = "UPSERT",
            payload = """{"id":"g-1","userId":"$user1","title":"Goal 1"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING"
        )
        val opUser2 = OutboxEntity(
            operationId = "op-2",
            entityType = "Goal",
            entityId = "g-2",
            operationType = "UPSERT",
            payload = """{"id":"g-2","userId":"$user2","title":"Goal 2"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING"
        )
        val opAmbiguous = OutboxEntity(
            operationId = "op-3",
            entityType = "Goal",
            entityId = "g-3",
            operationType = "UPSERT",
            payload = """{"id":"g-3","title":"Goal 3 without userId"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING"
        )
        val opMalformed = OutboxEntity(
            operationId = "op-4",
            entityType = "Goal",
            entityId = "g-4",
            operationType = "UPSERT",
            payload = "INVALID_CORRUPT_JSON{{{",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING"
        )

        outboxDao.insertEntry(opUser1)
        outboxDao.insertEntry(opUser2)
        outboxDao.insertEntry(opAmbiguous)
        outboxDao.insertEntry(opMalformed)

        val result = repository.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        // op-1 (user1) must be deleted
        assertFalse("op-1 must be deleted", outboxDao.entryExists("op-1"))

        // op-2 (user2) must be preserved
        assertTrue("op-2 must be preserved", outboxDao.entryExists("op-2"))

        // op-3 (ambiguous) must be preserved
        assertTrue("op-3 must be preserved", outboxDao.entryExists("op-3"))

        // op-4 (malformed) must be preserved
        assertTrue("op-4 must be preserved", outboxDao.entryExists("op-4"))
    }

    @Test
    fun erasingUser_removesTargetUserOutboxAcrossAllStatuses_includingConflictSyncingFailed_whilePreservingForeign() = runBlocking {
        insertUserProfile(user1)
        insertUserProfile(user2)

        val outboxDao = db.outboxDao()

        val statuses = listOf("LOCAL_ONLY", "PENDING", "SYNCING", "SYNCED", "FAILED", "CONFLICT")
        statuses.forEachIndexed { index, status ->
            // Insert target user entry for this status
            outboxDao.insertEntry(
                OutboxEntity(
                    operationId = "op-user1-$status",
                    entityType = "Goal",
                    entityId = "g-user1-$index",
                    operationType = "UPSERT",
                    payload = """{"id":"g-user1-$index","userId":"$user1","title":"Goal user1 $status"}""",
                    createdAt = 1000L + index,
                    retryCount = 0,
                    lastAttemptAt = null,
                    status = status
                )
            )
            // Insert foreign user entry for this status
            outboxDao.insertEntry(
                OutboxEntity(
                    operationId = "op-user2-$status",
                    entityType = "Goal",
                    entityId = "g-user2-$index",
                    operationType = "UPSERT",
                    payload = """{"id":"g-user2-$index","userId":"$user2","title":"Goal user2 $status"}""",
                    createdAt = 2000L + index,
                    retryCount = 0,
                    lastAttemptAt = null,
                    status = status
                )
            )
        }

        // Also add ambiguous and malformed in CONFLICT status
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-ambiguous-conflict",
                entityType = "Goal",
                entityId = "g-amb",
                operationType = "UPSERT",
                payload = """{"id":"g-amb","title":"No userId"}""",
                createdAt = 3000L,
                retryCount = 1,
                lastAttemptAt = null,
                status = "CONFLICT"
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-malformed-conflict",
                entityType = "Goal",
                entityId = "g-mal",
                operationType = "UPSERT",
                payload = "CORRUPT_JSON_DATA",
                createdAt = 3001L,
                retryCount = 1,
                lastAttemptAt = null,
                status = "CONFLICT"
            )
        )

        val result = repository.deleteAllUserData(user1)
        assertTrue("Erasure must succeed", result is Result.Success)

        // All target user entries across every status must be deleted
        statuses.forEach { status ->
            assertFalse(
                "Target user entry for status $status must be deleted",
                outboxDao.entryExists("op-user1-$status")
            )
        }

        // All foreign user entries across every status must be preserved
        statuses.forEach { status ->
            assertTrue(
                "Foreign user entry for status $status must be preserved",
                outboxDao.entryExists("op-user2-$status")
            )
        }

        // Ambiguous and malformed records must be preserved
        assertTrue("Ambiguous CONFLICT record must be preserved", outboxDao.entryExists("op-ambiguous-conflict"))
        assertTrue("Malformed CONFLICT record must be preserved", outboxDao.entryExists("op-malformed-conflict"))
    }

    @Test
    fun `cancelAllUserWorkers_cancelsAllUniqueWorkAndTags`() {
        val mockWorkManager = mockk<androidx.work.WorkManager>(relaxed = true)
        val repoWithWm = DataManagementRepositoryImpl(
            ironMindDao = ironMindDao,
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            learnerEventStatusDao = db.learnerEventStatusDao(),
            database = db,
            workManager = mockWorkManager
        )

        repoWithWm.cancelAllUserWorkers()

        verify { mockWorkManager.cancelUniqueWork("SyncWorker") }
        verify { mockWorkManager.cancelUniqueWork("PatternUpdateWorker") }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker.UNIQUE_WORK_NAME) }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker.RECONCILIATION_WORK_NAME) }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler.UNIQUE_WORK_NAME) }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.InterventionEvaluationWorker.UNIQUE_WORK_NAME) }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.InterventionEvaluationWorker.UNIQUE_WORK_NAME_SCHEDULED) }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.InterventionEvaluationWorker.UNIQUE_WORK_NAME_PERIODIC) }
        verify { mockWorkManager.cancelUniqueWork(com.sanket_satpute_20.ironmind.infrastructure.worker.InterventionEvaluationWorker.UNIQUE_WORK_NAME_EVENT) }

        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.ReflectionProcessingWorker.TAG) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.SyncWorker.TAG) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.PatternUpdateWorker.TAG) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationWorker.TAG) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker.UNIQUE_WORK_NAME) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker.RECONCILIATION_WORK_NAME) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler.UNIQUE_WORK_NAME) }
        verify { mockWorkManager.cancelAllWorkByTag(com.sanket_satpute_20.ironmind.infrastructure.worker.InterventionEvaluationWorker.TAG) }
    }
}
