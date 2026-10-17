package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.BarrierDao
import com.sanket_satpute_20.ironmind.data.local.dao.InterventionRecommendationDao
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer
import com.sanket_satpute_20.ironmind.domain.common.Result
import io.mockk.every
import io.mockk.mockk
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
}
