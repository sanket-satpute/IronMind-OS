package com.sanket_satpute_20.ironmind.domain.usecase.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.*
import com.sanket_satpute_20.ironmind.data.repository.DataExportRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentState
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionChannel
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 8.3.2 Stage 2 Targeted Verification Test:
 * Validates transactional export snapshot across all 26 user-owned Room entities,
 * full unpaginated observation retrieval (1,050+ records), multi-user isolation,
 * session safety, admission fencing (erasure protection), and single-flight concurrency.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class DataExportSnapshotTest {

    private lateinit var db: IronMindDatabase
    private lateinit var dataExportRepository: DataExportRepositoryImpl
    private val clock = object : Clock {
        override fun currentTimeMillis(): Long = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataExportRepository = DataExportRepositoryImpl(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun captureUserSnapshot_completeness_capturesAll26EntitiesAndOver1000Observations() = runBlocking {
        val userA = "user-A"
        val userB = "user-B"

        val ironMindDao = db.ironMindDao()
        val observationDao = db.observationDao()
        val interventionDao = db.interventionDao()
        val barrierDao = db.barrierDao()
        val interventionRecommendationDao = db.interventionRecommendationDao()
        val correctionReviewDao = db.correctionReviewDao()
        val learnerEventStatusDao = db.learnerEventStatusDao()
        val patternDao = db.patternDao()
        val autonomySettingsDao = db.autonomySettingsDao()
        val globalAutonomyStateDao = db.globalAutonomyStateDao()
        val appUsageDao = db.appUsageObservationSettingsDao()
        val notificationSettingsDao = db.notificationObservationSettingsDao()
        val calendarSettingsDao = db.calendarObservationSettingsDao()
        val locationSettingsDao = db.locationObservationSettingsDao()
        val activitySettingsDao = db.activityObservationSettingsDao()
        val experimentDao = db.experimentDao()

        // 1. UserProfile
        ironMindDao.insertUserProfile(UserProfileEntity(id = userA, createdAt = 1000L, updatedAt = 1100L, displayName = "User A", timezone = "UTC", createdFrom = "google", status = "ACTIVE", schemaVersion = 1))
        ironMindDao.insertUserProfile(UserProfileEntity(id = userB, createdAt = 1000L, updatedAt = 1100L, displayName = "User B", timezone = "UTC", createdFrom = "google", status = "ACTIVE", schemaVersion = 1))

        // 2. Goal
        ironMindDao.insertGoal(GoalEntity(id = "g-1", userId = userA, ambitionId = null, title = "Goal A", description = "Desc", why = "Why", importance = 5, status = "ACTIVE", targetAt = 2000L, startedAt = 1000L, completedAt = null, createdAt = 1000L, updatedAt = 1000L, schemaVersion = 1))

        // 3. Plan
        ironMindDao.insertPlan(PlanEntity(id = "p-1", userId = userA, goalId = "g-1", ambitionId = null, title = "Plan A", description = "Desc", status = "ACTIVE", createdAt = 1000L, updatedAt = 1000L, startedAt = 1000L, completedAt = null, source = "USER", schemaVersion = 1))

        // 4. Task
        ironMindDao.insertTask(TaskEntity(id = "t-1", userId = userA, goalId = "g-1", planId = "p-1", parentTaskId = null, title = "Task A", description = "Desc", status = "PENDING", priority = 3, estimatedDurationMinutes = 30, scheduledAt = 1500L, dueAt = 2000L, createdAt = 1000L, updatedAt = 1000L, completedAt = null, postponedAt = null, source = "USER", schemaVersion = 1))

        // 5. Commitment
        ironMindDao.insertCommitment(CommitmentEntity(id = "c-1", userId = userA, goalId = "g-1", planId = "p-1", taskId = "t-1", parentCommitmentId = null, title = "Commitment A", description = "Desc", committedAt = 1000L, scheduledStartAt = 1500L, scheduledEndAt = 2000L, status = "COMMITTED", priority = 3, source = "USER", createdAt = 1000L, updatedAt = 1000L, startedAt = 1000L, completedAt = null, postponedAt = null, missedAt = null, recoveredAt = null, schemaVersion = 1))

        // 6. Outcome
        ironMindDao.insertOutcome(OutcomeEntity(id = "o-1", userId = userA, sourceEntityId = "t-1", sourceEntityType = "TASK", resultStatus = "POSITIVE", actualDurationMinutes = 25, completedAt = 1200L, createdAt = 1200L, schemaVersion = 1))

        // 7. Reflection
        ironMindDao.insertReflection(ReflectionEntity(id = "r-1", userId = userA, targetEntityId = "t-1", targetEntityType = "TASK", content = "Reflecting on task", sentiment = "POSITIVE", createdAt = 1250L, schemaVersion = 1))

        // 8. ProtectionRule
        ironMindDao.insertProtectionRule(ProtectionRuleEntity(id = "pr-1", userId = userA, targetPackage = "com.distraction", targetCategory = null, triggerType = "MANUAL", conditions = "{}", enabled = true, priority = 1, createdAt = 1000L, updatedAt = 1000L))

        // 9. ProtectionSession
        ironMindDao.insertProtectionSession(ProtectionSessionEntity(id = "ps-1", userId = userA, ruleId = "pr-1", commitmentId = null, taskId = null, startedAt = 1000L, scheduledEndAt = 1500L, endedAt = 1500L, status = "COMPLETED", source = "USER", overrideAllowed = false, createdAt = 1000L, updatedAt = 1500L))

        // 10. Event
        ironMindDao.insertEvent(EventEntity(id = "ev-1", userId = userA, type = "TASK_COMPLETED", entityType = "TASK", entityId = "t-1", occurredAt = 1000L, recordedAt = 1000L, processedAt = 1000L, source = "SYSTEM", previousState = "PENDING", newState = "COMPLETED", metadata = null, correlationId = null, causationId = null, schemaVersion = 1))

        // 11. Memory
        ironMindDao.insertMemory(MemoryEntity(id = "m-1", userId = userA, type = "PREFERENCE", content = "Likes morning runs", source = "REFLECTION", confidence = 0.9f, evidenceCount = 2, firstObservedAt = 1000L, lastObservedAt = 1100L, confirmationState = "USER_CONFIRMED", status = "ACTIVE", expiresAt = null, createdAt = 1000L, updatedAt = 1100L, schemaVersion = 1))

        // 12. Observations (1,050 records to verify threshold exceedance without truncation)
        for (i in 1..1050) {
            observationDao.insertObservation(
                ObservationEntity(
                    id = "obs-$i",
                    userId = userA,
                    type = "APP_USAGE_SESSION",
                    source = "SYSTEM",
                    occurredAt = 1000L + i,
                    recordedAt = 1000L + i,
                    subjectId = "com.app.test",
                    value = """{"duration":$i}""",
                    context = "{}",
                    confidence = 0.95f,
                    provenanceSource = "SYSTEM",
                    provenanceSourceReference = null,
                    provenanceCapturedAt = 1000L + i,
                    schemaVersion = 1
                )
            )
        }

        // 13. Pattern
        patternDao.insertPattern(PatternEntity(id = "pat-1", userId = userA, type = "TIME_PATTERN", description = "Morning focus", conditions = null, predictedBehavior = null, confidence = 0.85f, evidenceCount = 3, evidenceReferences = null, firstObservedAt = 1000L, lastObservedAt = 1200L, status = "ACTIVE", confirmationState = "USER_CONFIRMED", createdAt = 1000L, updatedAt = 1200L, fingerprint = "user-A_TIME_PATTERN_morning focus"))

        // 14. AutonomySettings
        autonomySettingsDao.upsertSetting(AutonomySettingsEntity(userId = userA, capability = "GOAL_CREATION", level = "AUTOMATIC", updatedAt = 1000L))

        // 15. GlobalAutonomyState
        globalAutonomyStateDao.upsertState(GlobalAutonomyStateEntity(userId = userA, isGlobalPauseActive = false, updatedAt = 1000L))

        // 16. AppUsageObservationSettings
        appUsageDao.upsertSettings(AppUsageObservationSettingsEntity(userId = userA, isEnabled = true, updatedAt = 1000L))

        // 17. InterventionRecord
        interventionDao.insert(InterventionRecordEntity(id = "int-1", userId = userA, type = InterventionType.REMIND, state = InterventionState.PROPOSED, title = "Take a break", description = "You worked 2 hours", channel = InterventionChannel.IN_APP, resolutionReason = null, contextData = null, createdAt = 1000L, updatedAt = 1000L))

        // 18. NotificationObservationSettings
        notificationSettingsDao.upsertSettings(NotificationObservationSettingsEntity(userId = userA, isEnabled = true, updatedAt = 1000L))

        // 19. CalendarObservationSettings
        calendarSettingsDao.insertOrUpdate(CalendarObservationSettingsEntity(userId = userA, isEnabled = true, updatedAt = 1000L))

        // 20. LocationObservationSettings
        locationSettingsDao.insertOrUpdate(LocationObservationSettingsEntity(userId = userA, isEnabled = true, updatedAt = 1000L))

        // 21. ActivityObservationSettings
        activitySettingsDao.insertOrUpdateSettings(ActivityObservationSettingsEntity(userId = userA, lastUpdatedAt = 1000L, isEnabled = true))

        // 22. ExperimentRecord
        experimentDao.insert(ExperimentRecordEntity(id = "exp-1", userId = userA, hypothesis = "Test hypothesis", activeVariation = "v1", controlVariation = "v0", targetMetric = "completion_rate", state = ExperimentState.ACTIVE, startedAt = 1000L, endedAt = null, outcomeSummary = null))

        // 23. BarrierHypothesis
        barrierDao.insert(BarrierHypothesisEntity(id = "bar-1", userId = userA, category = BarrierCategory.DISTRACTION.name, description = "Underestimates task duration", confirmationState = BarrierConfirmationState.CONFIRMED.name, status = BarrierStatus.ACTIVE.name, sourceReflectionId = "r-1", firstObservedAt = 1000L, lastObservedAt = 1100L))

        // 24. InterventionRecommendation
        interventionRecommendationDao.insert(InterventionRecommendationEntity(id = "rec-1", userId = userA, interventionType = InterventionType.REMIND.name, objective = InterventionObjective.INITIATE_ACTION.name, targetEntityId = "t-1", targetEntityType = "TASK", rationale = "Preserve momentum", suggestedAction = "Take 5m break", status = "PENDING", createdAt = 1000L, expiresAt = 2000L, causalHypothesisId = null, causalHypothesisType = null))

        // 25. LearnerEventStatus
        learnerEventStatusDao.insert(LearnerEventStatusEntity(eventId = "ev-1", userId = userA, status = "COMPLETED", claimCount = 1, failureCount = 0, maxAttempts = 3, leaseToken = null, leaseExpiresAt = null, lastAttemptAt = 1000L, nextAttemptAt = 0L, errorMessage = null, hadMalformedMetadata = false, createdAt = 1000L, updatedAt = 1000L))

        // 26. CorrectionReview
        correctionReviewDao.insertIgnore(CorrectionReviewEntity(id = "rev-1", userId = userA, sourceEventId = "ev-1", recommendationId = "rec-1", causalHypothesisId = "pat-1", causalHypothesisType = "PATTERN", correctionScope = "INTERVENTION_CORRECTION", correctedText = "Correction", decisionState = "DISMISSED", reviewStatus = "PENDING", reviewFeedback = null, createdAt = 1000L, updatedAt = 1000L, reviewedAt = null, schemaVersion = 1))

        // Capture snapshot
        val result = dataExportRepository.captureUserSnapshot(userA)
        if (result is Result.Failure) {
            System.err.println("CAPTURE FAILED: ${result.error}")
            result.error.printStackTrace()
        }
        assertTrue("Snapshot capture must succeed: ${(result as? Result.Failure)?.error?.message}", result is Result.Success)
        val snapshot = (result as Result.Success).data

        // Verify metadata
        assertEquals(1, snapshot.exportVersion)
        assertEquals(userA, snapshot.userId)
        assertEquals(1_700_000_000_000L, snapshot.exportedAt)

        // Verify all 26 entity collections are populated
        assertNotNull("UserProfile must be present", snapshot.profile)
        assertEquals(userA, snapshot.profile?.id)
        assertEquals(1, snapshot.goals.size)
        assertEquals("g-1", snapshot.goals.first().id)
        assertEquals(1, snapshot.plans.size)
        assertEquals("p-1", snapshot.plans.first().id)
        assertEquals(1, snapshot.tasks.size)
        assertEquals("t-1", snapshot.tasks.first().id)
        assertEquals(1, snapshot.commitments.size)
        assertEquals("c-1", snapshot.commitments.first().id)
        assertEquals(1, snapshot.outcomes.size)
        assertEquals("o-1", snapshot.outcomes.first().id)
        assertEquals(1, snapshot.reflections.size)
        assertEquals("r-1", snapshot.reflections.first().id)
        assertEquals(1, snapshot.protectionRules.size)
        assertEquals("pr-1", snapshot.protectionRules.first().id)
        assertEquals(1, snapshot.protectionSessions.size)
        assertEquals("ps-1", snapshot.protectionSessions.first().id)
        assertEquals(1, snapshot.events.size)
        assertEquals("ev-1", snapshot.events.first().id)
        assertEquals(1, snapshot.memories.size)
        assertEquals("m-1", snapshot.memories.first().id)
        assertEquals(1050, snapshot.observations.size)
        assertEquals("obs-1", snapshot.observations.first().id)
        assertEquals("obs-1050", snapshot.observations.last().id)
        assertEquals(1, snapshot.patterns.size)
        assertEquals("pat-1", snapshot.patterns.first().id)
        assertEquals(1, snapshot.autonomySettings.size)
        assertEquals("GOAL_CREATION", snapshot.autonomySettings.first().capability)
        assertNotNull("GlobalAutonomyState must be present", snapshot.globalAutonomyState)
        assertFalse(snapshot.globalAutonomyState!!.isGlobalPauseActive)
        assertNotNull("AppUsageObservationSettings must be present", snapshot.appUsageObservationSettings)
        assertTrue(snapshot.appUsageObservationSettings!!.isEnabled)
        assertEquals(1, snapshot.interventionRecords.size)
        assertEquals("int-1", snapshot.interventionRecords.first().id)
        assertNotNull("NotificationObservationSettings must be present", snapshot.notificationObservationSettings)
        assertTrue(snapshot.notificationObservationSettings!!.isEnabled)
        assertNotNull("CalendarObservationSettings must be present", snapshot.calendarObservationSettings)
        assertTrue(snapshot.calendarObservationSettings!!.isEnabled)
        assertNotNull("LocationObservationSettings must be present", snapshot.locationObservationSettings)
        assertTrue(snapshot.locationObservationSettings!!.isEnabled)
        assertNotNull("ActivityObservationSettings must be present", snapshot.activityObservationSettings)
        assertTrue(snapshot.activityObservationSettings!!.isEnabled)
        assertEquals(1, snapshot.experimentRecords.size)
        assertEquals("exp-1", snapshot.experimentRecords.first().id)
        assertEquals(1, snapshot.barrierHypotheses.size)
        assertEquals("bar-1", snapshot.barrierHypotheses.first().id)
        assertEquals(1, snapshot.interventionRecommendations.size)
        assertEquals("rec-1", snapshot.interventionRecommendations.first().id)
        assertEquals(1, snapshot.learnerEventStatuses.size)
        assertEquals("ev-1", snapshot.learnerEventStatuses.first().eventId)
        assertEquals(1, snapshot.correctionReviews.size)
        assertEquals("rev-1", snapshot.correctionReviews.first().id)

        // Verify excluded device-level tables contract
        assertEquals(3, UserDataExport.EXCLUDED_DEVICE_LEVEL_TABLES.size)
        assertTrue(UserDataExport.EXCLUDED_DEVICE_LEVEL_TABLES.contains("decision_records"))
        assertTrue(UserDataExport.EXCLUDED_DEVICE_LEVEL_TABLES.contains("notification_records"))
        assertTrue(UserDataExport.EXCLUDED_DEVICE_LEVEL_TABLES.contains("outbox"))
    }

    @Test
    fun captureUserSnapshot_multiUserIsolation_strictPartitioning() = runBlocking {
        val userA = "user-A"
        val userB = "user-B"

        val ironMindDao = db.ironMindDao()
        val observationDao = db.observationDao()

        // Insert distinct goals, tasks, observations for user-A and user-B
        ironMindDao.insertGoal(GoalEntity(id = "g-A", userId = userA, ambitionId = null, title = "Goal A", description = "Desc", why = "Why", importance = 5, status = "ACTIVE", targetAt = 2000L, startedAt = 1000L, completedAt = null, createdAt = 1000L, updatedAt = 1000L, schemaVersion = 1))
        ironMindDao.insertGoal(GoalEntity(id = "g-B", userId = userB, ambitionId = null, title = "Goal B", description = "Desc", why = "Why", importance = 5, status = "ACTIVE", targetAt = 2000L, startedAt = 1000L, completedAt = null, createdAt = 1000L, updatedAt = 1000L, schemaVersion = 1))

        observationDao.insertObservation(
            ObservationEntity("obs-A", userA, "APP_USAGE_SESSION", "SYSTEM", 1000L, 1000L, null, "val-A", "{}", 1.0f, "SYSTEM", null, 1000L, 1)
        )
        observationDao.insertObservation(
            ObservationEntity("obs-B", userB, "APP_USAGE_SESSION", "SYSTEM", 1000L, 1000L, null, "val-B", "{}", 1.0f, "SYSTEM", null, 1000L, 1)
        )

        val resultA = dataExportRepository.captureUserSnapshot(userA)
        if (resultA is Result.Failure) {
            System.err.println("CAPTURE A FAILED: ${resultA.error}")
            resultA.error.printStackTrace()
        }
        assertTrue("Capture A should succeed: ${(resultA as? Result.Failure)?.error?.message}", resultA is Result.Success)
        val snapshotA = (resultA as Result.Success).data
        assertEquals(1, snapshotA.goals.size)
        assertEquals("g-A", snapshotA.goals.first().id)
        assertEquals(1, snapshotA.observations.size)
        assertEquals("obs-A", snapshotA.observations.first().id)

        val resultB = dataExportRepository.captureUserSnapshot(userB)
        assertTrue(resultB is Result.Success)
        val snapshotB = (resultB as Result.Success).data
        assertEquals(1, snapshotB.goals.size)
        assertEquals("g-B", snapshotB.goals.first().id)
        assertEquals(1, snapshotB.observations.size)
        assertEquals("obs-B", snapshotB.observations.first().id)
    }

    @Test
    fun exportUserDataUseCase_sessionSafety_rejectsInvalidAndErasedStates() = runBlocking {
        val userA = "user-A"
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userA, isAnonymous = false))
        val useCase = ExportUserDataUseCase(
            dataExportRepository = dataExportRepository,
            userSessionProvider = sessionProvider,
            clock = clock
        )

        // 1. Initializing state
        sessionProvider.setState(SessionState.Initializing)
        val initResult = useCase()
        assertTrue(initResult is Result.Failure)
        assertTrue((initResult as Result.Failure).error is IllegalStateException)
        assertEquals("Cannot export user data while session is initializing", initResult.error.message)

        // 2. ReconciliationFailed state
        sessionProvider.setState(SessionState.ReconciliationFailed(RuntimeException("Network down"), userA))
        val recResult = useCase()
        assertTrue(recResult is Result.Failure)
        assertTrue((recResult as Result.Failure).error is IllegalStateException)
        assertTrue(recResult.error.message!!.contains("Cannot export user data while session reconciliation is in failed state"))

        // 3. Authenticated state -> Succeeds
        sessionProvider.setState(SessionState.Authenticated(userA, isAnonymous = false))
        val successResult = useCase()
        assertTrue("Valid authenticated session must succeed", successResult is Result.Success)

        // 4. Erased state via admission fence
        val currentGen = sessionProvider.getSessionGeneration()
        sessionProvider.markUserErasing(userA, currentGen)
        val erasedResult = useCase()
        assertTrue(erasedResult is Result.Failure)
        assertTrue((erasedResult as Result.Failure).error is IllegalStateException)
        assertTrue(erasedResult.error.message!!.contains("Cannot export data for erased user"))
    }

    @Test
    fun exportUserDataUseCase_requestedUserIdValidation_failsClosedOnMismatch() = runBlocking {
        val userA = "user-A"
        val userB = "user-B"
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userA, isAnonymous = false))
        val useCase = ExportUserDataUseCase(
            dataExportRepository = dataExportRepository,
            userSessionProvider = sessionProvider,
            clock = clock
        )

        // Mismatched target user ID
        val mismatchResult = useCase(requestedUserId = userB)
        assertTrue(mismatchResult is Result.Failure)
        assertTrue((mismatchResult as Result.Failure).error is IllegalArgumentException)
        assertTrue(mismatchResult.error.message!!.contains("active session belongs to $userA"))

        // Attempting to export legacy user while authenticated
        val legacyResult = useCase(requestedUserId = SessionState.DEFAULT_LEGACY_USER_ID)
        assertTrue(legacyResult is Result.Failure)
        assertTrue((legacyResult as Result.Failure).error is IllegalArgumentException)
        assertTrue(legacyResult.error.message!!.contains("Cannot export legacy user data when active session is authenticated"))

        // Offline fallback attempting to export authenticated user
        sessionProvider.setState(SessionState.OfflineFallback())
        val offlineMismatchResult = useCase(requestedUserId = userA)
        assertTrue(offlineMismatchResult is Result.Failure)
        assertTrue((offlineMismatchResult as Result.Failure).error is IllegalArgumentException)
        assertTrue(offlineMismatchResult.error.message!!.contains("active session is unauthenticated offline fallback"))
    }

    @Test
    fun exportUserDataUseCase_singleFlightConcurrencyEnforcement() = runBlocking {
        val userA = "user-A"
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userA, isAnonymous = false))

        // Create a slow repository to observe concurrency locking
        val slowRepo = object : com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository {
            override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> {
                delay(150)
                return Result.Success(UserDataExport(exportedAt = clock.currentTimeMillis(), userId = userId, profile = null))
            }
        }

        val useCase = ExportUserDataUseCase(
            dataExportRepository = slowRepo,
            userSessionProvider = sessionProvider,
            clock = clock
        )

        val firstDeferred = async(Dispatchers.IO) { useCase() }
        delay(20) // Give the first call a head start to acquire single-flight lock
        val secondResult = useCase() // Second call runs while first is active

        assertTrue("Second concurrent export call must be rejected", secondResult is Result.Failure)
        assertTrue((secondResult as Result.Failure).error is IllegalStateException)
        assertEquals("User data export is already in progress", secondResult.error.message)

        // Wait for first call to complete
        val firstResult = firstDeferred.await()
        assertTrue("First export call must complete successfully", firstResult is Result.Success)

        // Subsequent call after first has finished must succeed (guard released in finally)
        val subsequentResult = useCase()
        assertTrue("Subsequent export call after first completes must succeed", subsequentResult is Result.Success)
    }

    @Test
    fun exportUserData_preservesHistoricalNullObjectiveWithoutFabricatingInitiateAction() = runBlocking {
        val userId = "user-legacy"
        val dao = db.interventionRecommendationDao()

        // Insert historical pre-migration-21 entity where objective is NULL
        dao.insert(
            InterventionRecommendationEntity(
                id = "rec-legacy-null",
                userId = userId,
                interventionType = "REMIND",
                objective = null, // Historical record prior to migration 20->21
                targetEntityId = "t-1",
                targetEntityType = "TASK",
                rationale = "Legacy reminder without objective",
                suggestedAction = "Take a break",
                status = "PENDING",
                createdAt = 1000L,
                expiresAt = 2000L,
                causalHypothesisId = null,
                causalHypothesisType = null
            )
        )

        val result = dataExportRepository.captureUserSnapshot(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        assertEquals(1, snapshot.interventionRecommendations.size)
        val exportRecord = snapshot.interventionRecommendations.first()
        assertEquals("rec-legacy-null", exportRecord.id)
        assertNull("Historical null objective must remain null and not be fabricated into INITIATE_ACTION", exportRecord.objective)
        assertEquals("REMIND", exportRecord.interventionType)
        assertEquals("PENDING", exportRecord.status)
    }

    @Test
    fun exportUserData_preservesNonNullObjective() = runBlocking {
        val userId = "user-modern"
        val dao = db.interventionRecommendationDao()

        dao.insert(
            InterventionRecommendationEntity(
                id = "rec-modern-obj",
                userId = userId,
                interventionType = "CHALLENGE",
                objective = "MOMENTUM_PRESERVATION",
                targetEntityId = "t-2",
                targetEntityType = "TASK",
                rationale = "Maintain focus",
                suggestedAction = "Continue 10m",
                status = "PENDING",
                createdAt = 2000L,
                expiresAt = 3000L,
                causalHypothesisId = "hyp-1",
                causalHypothesisType = "BARRIER"
            )
        )

        val result = dataExportRepository.captureUserSnapshot(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        assertEquals(1, snapshot.interventionRecommendations.size)
        val exportRecord = snapshot.interventionRecommendations.first()
        assertEquals("rec-modern-obj", exportRecord.id)
        assertEquals("MOMENTUM_PRESERVATION", exportRecord.objective)
        assertEquals("hyp-1", exportRecord.causalHypothesisId)
        assertEquals("BARRIER", exportRecord.causalHypothesisType)
    }
}
