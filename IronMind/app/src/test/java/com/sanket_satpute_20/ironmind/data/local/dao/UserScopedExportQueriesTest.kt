package com.sanket_satpute_20.ironmind.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.*
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionChannel
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 8.3.2 Stage 1 Targeted Tests:
 * Validates user-scoped export queries, multi-user isolation, task status coverage,
 * observation retrieval exceeding the former 1,000-record threshold, and deterministic ordering.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class UserScopedExportQueriesTest {

    private lateinit var db: IronMindDatabase
    private lateinit var ironMindDao: IronMindDao
    private lateinit var observationDao: ObservationDao
    private lateinit var interventionDao: InterventionDao
    private lateinit var barrierDao: BarrierDao
    private lateinit var interventionRecommendationDao: InterventionRecommendationDao
    private lateinit var correctionReviewDao: CorrectionReviewDao
    private lateinit var learnerEventStatusDao: LearnerEventStatusDao
    private lateinit var patternDao: PatternDao
    private lateinit var experimentDao: ExperimentDao
    private lateinit var autonomySettingsDao: AutonomySettingsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ironMindDao = db.ironMindDao()
        observationDao = db.observationDao()
        interventionDao = db.interventionDao()
        barrierDao = db.barrierDao()
        interventionRecommendationDao = db.interventionRecommendationDao()
        correctionReviewDao = db.correctionReviewDao()
        learnerEventStatusDao = db.learnerEventStatusDao()
        patternDao = db.patternDao()
        experimentDao = db.experimentDao()
        autonomySettingsDao = db.autonomySettingsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ==========================================
    // Task Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getTasksForUser_returnsMultipleStatusesAndIsolatesUsers() {
        val statuses = listOf("PENDING", "COMPLETED", "POSTPONED", "FAILED", "CANCELLED")
        statuses.forEachIndexed { index, status ->
            ironMindDao.insertTask(
                TaskEntity(
                    id = "task-user-a-$index",
                    userId = "user-A",
                    goalId = "goal-1",
                    planId = "plan-1",
                    parentTaskId = null,
                    title = "Task A $status",
                    description = "Description for $status",
                    status = status,
                    priority = 1,
                    estimatedDurationMinutes = 30,
                    scheduledAt = 1000L + index,
                    dueAt = 2000L + index,
                    createdAt = 100L + index,
                    updatedAt = 100L + index,
                    completedAt = if (status == "COMPLETED") 500L else null,
                    postponedAt = if (status == "POSTPONED") 600L else null,
                    source = "USER",
                    schemaVersion = 1
                )
            )
        }

        // Cross-user tasks for User B
        ironMindDao.insertTask(
            TaskEntity(
                id = "task-user-b-1",
                userId = "user-B",
                goalId = "goal-2",
                planId = "plan-2",
                parentTaskId = null,
                title = "Task B",
                description = "User B task",
                status = "PENDING",
                priority = 2,
                estimatedDurationMinutes = 15,
                scheduledAt = 3000L,
                dueAt = 4000L,
                createdAt = 200L,
                updatedAt = 200L,
                completedAt = null,
                postponedAt = null,
                source = "USER",
                schemaVersion = 1
            )
        )

        val userATasks = ironMindDao.getTasksForUser("user-A")
        val userBTasks = ironMindDao.getTasksForUser("user-B")

        assertEquals("User A must have all 5 status tasks", 5, userATasks.size)
        assertTrue("User A tasks must only belong to user-A", userATasks.all { it.userId == "user-A" })
        assertEquals("User B must have exactly 1 task", 1, userBTasks.size)
        assertEquals("user-B", userBTasks.first().userId)

        // Verify all 5 statuses are present in User A's export
        val returnedStatuses = userATasks.map { it.status }.toSet()
        assertEquals(statuses.toSet(), returnedStatuses)
    }

    @Test
    fun getTasksForUser_emptyDatasetReturnsEmptyList() {
        val result = ironMindDao.getTasksForUser("non-existent-user")
        assertTrue("Empty dataset must return empty list", result.isEmpty())
    }

    // ==========================================
    // Observation Retrieval & Truncation Tests
    // ==========================================

    @Test
    fun getAllObservationsForUser_retrievesExceeding1000WithoutTruncation() {
        val totalObservations = 1050
        // Insert 1,050 observations for User A
        for (i in 0 until totalObservations) {
            observationDao.insertObservation(
                ObservationEntity(
                    id = "obs-user-a-${String.format("%04d", i)}",
                    userId = "user-A",
                    type = "APP_USAGE",
                    source = "SYSTEM",
                    occurredAt = 10000L + i,
                    recordedAt = 10000L + i,
                    subjectId = "com.test.app",
                    value = "open",
                    context = "{}",
                    confidence = 1.0f,
                    provenanceSource = "LOCAL_SENSOR",
                    provenanceSourceReference = null,
                    provenanceCapturedAt = 10000L + i,
                    schemaVersion = 1
                )
            )
        }

        // Insert 50 observations for User B
        for (i in 0 until 50) {
            observationDao.insertObservation(
                ObservationEntity(
                    id = "obs-user-b-${String.format("%04d", i)}",
                    userId = "user-B",
                    type = "LOCATION",
                    source = "SYSTEM",
                    occurredAt = 20000L + i,
                    recordedAt = 20000L + i,
                    subjectId = null,
                    value = "home",
                    context = "{}",
                    confidence = 0.9f,
                    provenanceSource = "LOCAL_SENSOR",
                    provenanceSourceReference = null,
                    provenanceCapturedAt = 20000L + i,
                    schemaVersion = 1
                )
            )
        }

        val userAObservations = observationDao.getAllObservationsForUser("user-A")
        val userBObservations = observationDao.getAllObservationsForUser("user-B")

        assertEquals("Must retrieve all 1,050 records without truncation", totalObservations, userAObservations.size)
        assertTrue("Must strictly belong to user-A", userAObservations.all { it.userId == "user-A" })
        assertEquals("User B must receive exactly 50 observations", 50, userBObservations.size)
        assertTrue("Must strictly belong to user-B", userBObservations.all { it.userId == "user-B" })

        // Verify deterministic ordering: occurredAt ASC, id ASC
        for (i in 0 until userAObservations.size - 1) {
            val current = userAObservations[i]
            val next = userAObservations[i + 1]
            assertTrue(
                "Ordering must be monotonically increasing by occurredAt, id",
                current.occurredAt < next.occurredAt || (current.occurredAt == next.occurredAt && current.id <= next.id)
            )
        }
    }

    // ==========================================
    // Plan Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getPlansForUser_returnsOnlyUserPlans() {
        ironMindDao.insertPlan(
            PlanEntity(
                id = "plan-a",
                userId = "user-A",
                goalId = "goal-1",
                ambitionId = null,
                title = "Plan A",
                description = "Description A",
                status = "ACTIVE",
                createdAt = 1000L,
                updatedAt = 1000L,
                startedAt = 1000L,
                completedAt = null,
                source = "USER",
                schemaVersion = 1
            )
        )
        ironMindDao.insertPlan(
            PlanEntity(
                id = "plan-b",
                userId = "user-B",
                goalId = "goal-2",
                ambitionId = null,
                title = "Plan B",
                description = "Description B",
                status = "ACTIVE",
                createdAt = 2000L,
                updatedAt = 2000L,
                startedAt = 2000L,
                completedAt = null,
                source = "USER",
                schemaVersion = 1
            )
        )

        val userAPlans = ironMindDao.getPlansForUser("user-A")
        assertEquals(1, userAPlans.size)
        assertEquals("plan-a", userAPlans.first().id)
        assertEquals("user-A", userAPlans.first().userId)
    }

    // ==========================================
    // Outcome Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getOutcomesForUser_returnsOnlyUserOutcomes() {
        ironMindDao.insertOutcome(
            OutcomeEntity(
                id = "outcome-a",
                userId = "user-A",
                sourceEntityId = "task-1",
                sourceEntityType = "TASK",
                resultStatus = "COMPLETED",
                actualDurationMinutes = 25,
                completedAt = 1500L,
                createdAt = 1000L,
                schemaVersion = 1
            )
        )
        ironMindDao.insertOutcome(
            OutcomeEntity(
                id = "outcome-b",
                userId = "user-B",
                sourceEntityId = "task-2",
                sourceEntityType = "TASK",
                resultStatus = "FAILED",
                actualDurationMinutes = 10,
                completedAt = 2500L,
                createdAt = 2000L,
                schemaVersion = 1
            )
        )

        val userAOutcomes = ironMindDao.getOutcomesForUser("user-A")
        assertEquals(1, userAOutcomes.size)
        assertEquals("outcome-a", userAOutcomes.first().id)
        assertEquals("user-A", userAOutcomes.first().userId)
    }

    // ==========================================
    // Reflection Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getReflectionsForUser_returnsOnlyUserReflections() {
        ironMindDao.insertReflection(
            ReflectionEntity(
                id = "reflection-a",
                userId = "user-A",
                targetEntityId = "goal-1",
                targetEntityType = "GOAL",
                content = "Felt great making progress",
                sentiment = "POSITIVE",
                createdAt = 1000L,
                schemaVersion = 1
            )
        )
        ironMindDao.insertReflection(
            ReflectionEntity(
                id = "reflection-b",
                userId = "user-B",
                targetEntityId = "goal-2",
                targetEntityType = "GOAL",
                content = "Could have been better",
                sentiment = "NEUTRAL",
                createdAt = 2000L,
                schemaVersion = 1
            )
        )

        val userAReflections = ironMindDao.getReflectionsForUser("user-A")
        assertEquals(1, userAReflections.size)
        assertEquals("reflection-a", userAReflections.first().id)
        assertEquals("user-A", userAReflections.first().userId)
    }

    // ==========================================
    // ProtectionSession Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getProtectionSessionsForUser_returnsAllSessionsIncludingInactive() {
        ironMindDao.insertProtectionSession(
            ProtectionSessionEntity(
                id = "session-active-a",
                userId = "user-A",
                ruleId = "rule-1",
                commitmentId = null,
                taskId = null,
                startedAt = 1000L,
                scheduledEndAt = 2000L,
                endedAt = null,
                status = "ACTIVE",
                source = "USER",
                overrideAllowed = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        ironMindDao.insertProtectionSession(
            ProtectionSessionEntity(
                id = "session-completed-a",
                userId = "user-A",
                ruleId = "rule-1",
                commitmentId = null,
                taskId = null,
                startedAt = 500L,
                scheduledEndAt = 800L,
                endedAt = 800L,
                status = "COMPLETED",
                source = "USER",
                overrideAllowed = false,
                createdAt = 500L,
                updatedAt = 800L
            )
        )
        ironMindDao.insertProtectionSession(
            ProtectionSessionEntity(
                id = "session-user-b",
                userId = "user-B",
                ruleId = "rule-2",
                commitmentId = null,
                taskId = null,
                startedAt = 3000L,
                scheduledEndAt = 4000L,
                endedAt = null,
                status = "ACTIVE",
                source = "USER",
                overrideAllowed = true,
                createdAt = 3000L,
                updatedAt = 3000L
            )
        )

        val userASessions = ironMindDao.getProtectionSessionsForUser("user-A")
        assertEquals("User A should have 2 protection sessions (both active and completed)", 2, userASessions.size)
        assertTrue("All sessions must belong to user-A", userASessions.all { it.userId == "user-A" })
    }

    // ==========================================
    // InterventionRecord Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getInterventionsForUser_returnsOnlyUserInterventions() {
        interventionDao.insert(
            InterventionRecordEntity(
                id = "intervention-a",
                userId = "user-A",
                type = InterventionType.REMIND,
                state = InterventionState.PROPOSED,
                title = "Focus Nudge",
                description = "Time to get back to work",
                channel = InterventionChannel.IN_APP,
                resolutionReason = null,
                contextData = null,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        interventionDao.insert(
            InterventionRecordEntity(
                id = "intervention-b",
                userId = "user-B",
                type = InterventionType.REFLECT,
                state = InterventionState.APPROVED,
                title = "Prompt",
                description = "Plan review",
                channel = InterventionChannel.NOTIFICATION,
                resolutionReason = null,
                contextData = null,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )

        val userAInterventions = interventionDao.getInterventionsForUser("user-A")
        assertEquals(1, userAInterventions.size)
        assertEquals("intervention-a", userAInterventions.first().id)
        assertEquals("user-A", userAInterventions.first().userId)
    }

    // ==========================================
    // BarrierHypothesis Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getAllBarriersForUser_returnsOnlyUserBarriers() = runBlocking {
        barrierDao.insert(
            BarrierHypothesisEntity(
                id = "barrier-a",
                userId = "user-A",
                category = "PROCRASTINATION",
                description = "Delaying task start",
                confirmationState = "USER_CONFIRMED",
                status = "ACTIVE",
                sourceReflectionId = "reflection-1",
                firstObservedAt = 1000L,
                lastObservedAt = 2000L
            )
        )
        barrierDao.insert(
            BarrierHypothesisEntity(
                id = "barrier-b",
                userId = "user-B",
                category = "FATIGUE",
                description = "Tired in the afternoon",
                confirmationState = "AI_GENERATED",
                status = "ACTIVE",
                sourceReflectionId = "reflection-2",
                firstObservedAt = 3000L,
                lastObservedAt = 4000L
            )
        )

        val userABarriers = barrierDao.getAllBarriersForUser("user-A")
        assertEquals(1, userABarriers.size)
        assertEquals("barrier-a", userABarriers.first().id)
        assertEquals("user-A", userABarriers.first().userId)
    }

    // ==========================================
    // InterventionRecommendation Retrieval & Isolation Tests
    // ==========================================

    @Test
    fun getAllRecommendationsForUser_returnsOnlyUserRecommendations() {
        interventionRecommendationDao.insert(
            InterventionRecommendationEntity(
                id = "rec-a",
                userId = "user-A",
                interventionType = "BREAK",
                objective = "REST",
                targetEntityId = null,
                targetEntityType = null,
                rationale = "Long focus streak",
                suggestedAction = "Take 5 minute walk",
                status = "PENDING",
                createdAt = 1000L,
                expiresAt = 2000L
            )
        )
        interventionRecommendationDao.insert(
            InterventionRecommendationEntity(
                id = "rec-b",
                userId = "user-B",
                interventionType = "FOCUS",
                objective = "DEEP_WORK",
                targetEntityId = null,
                targetEntityType = null,
                rationale = "Morning peak energy",
                suggestedAction = "Block distractions",
                status = "PENDING",
                createdAt = 3000L,
                expiresAt = 4000L
            )
        )

        val userARecs = interventionRecommendationDao.getAllRecommendationsForUser("user-A")
        assertEquals(1, userARecs.size)
        assertEquals("rec-a", userARecs.first().id)
        assertEquals("user-A", userARecs.first().userId)
    }

    // ==========================================
    // CorrectionReview Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getAllReviewsForUser_returnsOnlyUserReviews() = runBlocking {
        correctionReviewDao.insertIgnore(
            CorrectionReviewEntity(
                id = "review-a",
                userId = "user-A",
                sourceEventId = "event-1",
                recommendationId = "rec-1",
                causalHypothesisId = "barrier-1",
                causalHypothesisType = "BARRIER",
                correctionScope = "LOCAL",
                correctedText = "Not tired, just interrupted",
                decisionState = "APPROVED",
                reviewStatus = "APPROVED",
                reviewFeedback = null,
                createdAt = 1000L,
                updatedAt = 1000L,
                reviewedAt = 1000L
            )
        )
        correctionReviewDao.insertIgnore(
            CorrectionReviewEntity(
                id = "review-b",
                userId = "user-B",
                sourceEventId = "event-2",
                recommendationId = "rec-2",
                causalHypothesisId = "barrier-2",
                causalHypothesisType = "BARRIER",
                correctionScope = "GLOBAL",
                correctedText = "Different reason",
                decisionState = "PENDING",
                reviewStatus = "PENDING",
                reviewFeedback = null,
                createdAt = 2000L,
                updatedAt = 2000L,
                reviewedAt = null
            )
        )

        val userAReviews = correctionReviewDao.getAllReviewsForUser("user-A")
        assertEquals(1, userAReviews.size)
        assertEquals("review-a", userAReviews.first().id)
        assertEquals("user-A", userAReviews.first().userId)
    }

    // ==========================================
    // LearnerEventStatus Retrieval & User Isolation Tests
    // ==========================================

    @Test
    fun getAllLearnerEventStatusForUser_returnsOnlyUserEventStatuses() = runBlocking {
        learnerEventStatusDao.insert(
            LearnerEventStatusEntity(
                eventId = "event-status-a",
                userId = "user-A",
                status = "SUCCEEDED",
                claimCount = 1,
                failureCount = 0,
                maxAttempts = 3,
                leaseToken = null,
                leaseExpiresAt = null,
                lastAttemptAt = 1000L,
                nextAttemptAt = 1000L,
                errorMessage = null,
                hadMalformedMetadata = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        learnerEventStatusDao.insert(
            LearnerEventStatusEntity(
                eventId = "event-status-b",
                userId = "user-B",
                status = "PENDING",
                claimCount = 0,
                failureCount = 0,
                maxAttempts = 3,
                leaseToken = null,
                leaseExpiresAt = null,
                lastAttemptAt = null,
                nextAttemptAt = 2000L,
                errorMessage = null,
                hadMalformedMetadata = false,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )

        val userAStatuses = learnerEventStatusDao.getAllForUser("user-A")
        assertEquals(1, userAStatuses.size)
        assertEquals("event-status-a", userAStatuses.first().eventId)
        assertEquals("user-A", userAStatuses.first().userId)
    }

    // ==========================================
    // Stage 1.1 Reused Queries & Deterministic Tie-Breaker Tests
    // ==========================================

    @Test
    fun getCommitmentsForUser_returnsMultipleStatusesAndIsolatesUsers() {
        val statuses = listOf("ACTIVE", "COMPLETED", "FAILED", "CANCELLED")
        statuses.forEachIndexed { index, status ->
            ironMindDao.insertCommitment(
                CommitmentEntity(
                    id = "comm-a-$index",
                    userId = "user-A",
                    goalId = "g-1",
                    planId = null,
                    taskId = null,
                    title = "Commitment $status",
                    description = "Description $status",
                    committedAt = 1000L + index,
                    scheduledStartAt = null,
                    scheduledEndAt = null,
                    status = status,
                    priority = 1,
                    source = "USER",
                    createdAt = 1000L + index,
                    updatedAt = 1000L + index,
                    startedAt = null,
                    completedAt = if (status == "COMPLETED") 2000L else null,
                    postponedAt = null,
                    missedAt = null,
                    recoveredAt = null,
                    parentCommitmentId = null,
                    schemaVersion = 1
                )
            )
        }

        ironMindDao.insertCommitment(
            CommitmentEntity(
                id = "comm-b-1",
                userId = "user-B",
                goalId = "g-2",
                planId = null,
                taskId = null,
                title = "User B Commitment",
                description = "User B description",
                committedAt = 2000L,
                scheduledStartAt = null,
                scheduledEndAt = null,
                status = "ACTIVE",
                priority = 1,
                source = "USER",
                createdAt = 2000L,
                updatedAt = 2000L,
                startedAt = null,
                completedAt = null,
                postponedAt = null,
                missedAt = null,
                recoveredAt = null,
                parentCommitmentId = null,
                schemaVersion = 1
            )
        )

        val userACommitments = ironMindDao.getCommitmentsForUser("user-A")
        val userBCommitments = ironMindDao.getCommitmentsForUser("user-B")
        val nonExistent = ironMindDao.getCommitmentsForUser("user-missing")

        assertEquals(4, userACommitments.size)
        assertTrue(userACommitments.all { it.userId == "user-A" })
        assertEquals(statuses.toSet(), userACommitments.map { it.status }.toSet())
        assertEquals(1, userBCommitments.size)
        assertEquals("user-B", userBCommitments.first().userId)
        assertTrue(nonExistent.isEmpty())
    }

    @Test
    fun getCommitmentsForUser_deterministicTieBreakerOrdering() {
        listOf("comm-3", "comm-1", "comm-2").forEach { id ->
            ironMindDao.insertCommitment(
                CommitmentEntity(
                    id = id,
                    userId = "user-A",
                    goalId = null,
                    planId = null,
                    taskId = null,
                    title = "Title $id",
                    description = "Desc",
                    committedAt = 1000L,
                    scheduledStartAt = null,
                    scheduledEndAt = null,
                    status = "ACTIVE",
                    priority = 1,
                    source = "USER",
                    createdAt = 5000L, // Identical createdAt
                    updatedAt = 5000L,
                    startedAt = null,
                    completedAt = null,
                    postponedAt = null,
                    missedAt = null,
                    recoveredAt = null,
                    parentCommitmentId = null,
                    schemaVersion = 1
                )
            )
        }

        val result = ironMindDao.getCommitmentsForUser("user-A")
        assertEquals(listOf("comm-1", "comm-2", "comm-3"), result.map { it.id })
    }

    @Test
    fun getProtectionRulesForUser_returnsBothEnabledAndDisabledAndIsolatesUsers() {
        ironMindDao.insertProtectionRule(
            ProtectionRuleEntity(
                id = "rule-a-enabled",
                userId = "user-A",
                targetPackage = "com.distraction.app",
                targetCategory = null,
                triggerType = "TIME",
                conditions = "{}",
                enabled = true,
                priority = 2,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        ironMindDao.insertProtectionRule(
            ProtectionRuleEntity(
                id = "rule-a-disabled",
                userId = "user-A",
                targetPackage = "com.other.app",
                targetCategory = null,
                triggerType = "LOCATION",
                conditions = "{}",
                enabled = false,
                priority = 1,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        ironMindDao.insertProtectionRule(
            ProtectionRuleEntity(
                id = "rule-b-enabled",
                userId = "user-B",
                targetPackage = "com.b.app",
                targetCategory = null,
                triggerType = "TIME",
                conditions = "{}",
                enabled = true,
                priority = 1,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )

        val userARules = ironMindDao.getProtectionRulesForUser("user-A")
        val userBRules = ironMindDao.getProtectionRulesForUser("user-B")
        val emptyRules = ironMindDao.getProtectionRulesForUser("user-missing")

        assertEquals(2, userARules.size)
        assertTrue(userARules.all { it.userId == "user-A" })
        assertEquals(setOf(true, false), userARules.map { it.enabled }.toSet())
        assertEquals(1, userBRules.size)
        assertEquals("user-B", userBRules.first().userId)
        assertTrue(emptyRules.isEmpty())
    }

    @Test
    fun experimentDao_getAllForUser_returnsMultipleStatesAndIsolatesUsers() {
        val states = listOf(ExperimentState.ACTIVE, ExperimentState.CONCLUDED, ExperimentState.ABORTED)
        states.forEachIndexed { index, state ->
            experimentDao.insert(
                ExperimentRecordEntity(
                    id = "exp-a-$index",
                    userId = "user-A",
                    hypothesis = "Hypothesis $state",
                    activeVariation = "v1",
                    controlVariation = "v0",
                    targetMetric = "focus_time",
                    state = state,
                    startedAt = 1000L + index,
                    endedAt = if (state != ExperimentState.ACTIVE) 2000L else null,
                    outcomeSummary = if (state != ExperimentState.ACTIVE) "Summary" else null
                )
            )
        }
        experimentDao.insert(
            ExperimentRecordEntity(
                id = "exp-b-1",
                userId = "user-B",
                hypothesis = "User B hypothesis",
                activeVariation = "v1",
                controlVariation = "v0",
                targetMetric = "focus_time",
                state = ExperimentState.ACTIVE,
                startedAt = 3000L,
                endedAt = null,
                outcomeSummary = null
            )
        )

        val userAExperiments = experimentDao.getAllForUser("user-A")
        val userBExperiments = experimentDao.getAllForUser("user-B")
        val emptyExperiments = experimentDao.getAllForUser("user-missing")

        assertEquals(3, userAExperiments.size)
        assertTrue(userAExperiments.all { it.userId == "user-A" })
        assertEquals(states.toSet(), userAExperiments.map { it.state }.toSet())
        assertEquals(1, userBExperiments.size)
        assertEquals("user-B", userBExperiments.first().userId)
        assertTrue(emptyExperiments.isEmpty())
    }

    @Test
    fun experimentDao_getAllForUser_deterministicTieBreakerOrdering() {
        listOf("exp-3", "exp-1", "exp-2").forEach { id ->
            experimentDao.insert(
                ExperimentRecordEntity(
                    id = id,
                    userId = "user-A",
                    hypothesis = "Hypothesis",
                    activeVariation = "v1",
                    controlVariation = "v0",
                    targetMetric = "metric",
                    state = ExperimentState.ACTIVE,
                    startedAt = 5000L, // Identical startedAt
                    endedAt = null,
                    outcomeSummary = null
                )
            )
        }

        val result = experimentDao.getAllForUser("user-A")
        assertEquals(listOf("exp-1", "exp-2", "exp-3"), result.map { it.id })
    }

    @Test
    fun getGoalsForUser_deterministicTieBreakerOrdering() {
        listOf("goal-3", "goal-1", "goal-2").forEach { id ->
            ironMindDao.insertGoal(
                GoalEntity(
                    id = id,
                    userId = "user-A",
                    ambitionId = null,
                    title = "Title $id",
                    description = "Description",
                    why = "Why",
                    importance = 1,
                    status = "ACTIVE",
                    targetAt = null,
                    startedAt = null,
                    completedAt = null,
                    createdAt = 5000L, // Identical createdAt
                    updatedAt = 5000L,
                    schemaVersion = 1
                )
            )
        }

        val result = ironMindDao.getGoalsForUser("user-A")
        assertEquals(listOf("goal-1", "goal-2", "goal-3"), result.map { it.id })
    }

    @Test
    fun getPatternsForUser_deterministicTieBreakerOrdering() {
        listOf("pat-3", "pat-1", "pat-2").forEach { id ->
            patternDao.insertPattern(
                PatternEntity(
                    id = id,
                    userId = "user-A",
                    fingerprint = "fingerprint-$id",
                    type = "TEMPORAL",
                    description = "Desc",
                    conditions = null,
                    predictedBehavior = null,
                    confidence = 0.8f,
                    evidenceCount = 2,
                    evidenceReferences = null,
                    firstObservedAt = 1000L,
                    lastObservedAt = 5000L, // Identical lastObservedAt
                    status = "ACTIVE",
                    confirmationState = "USER_CONFIRMED",
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }

        val result = patternDao.getPatternsForUser("user-A")
        assertEquals(listOf("pat-1", "pat-2", "pat-3"), result.map { it.id })
    }

    @Test
    fun getMemoriesForUser_deterministicTieBreakerOrdering() {
        listOf("mem-3", "mem-1", "mem-2").forEach { id ->
            ironMindDao.insertMemory(
                MemoryEntity(
                    id = id,
                    userId = "user-A",
                    type = "FACT",
                    content = "Content $id",
                    source = "USER",
                    confidence = 1.0f,
                    evidenceCount = 1,
                    firstObservedAt = 1000L,
                    lastObservedAt = 1000L,
                    confirmationState = "USER_CONFIRMED",
                    status = "ACTIVE",
                    expiresAt = null,
                    createdAt = 5000L, // Identical createdAt
                    updatedAt = 5000L,
                    schemaVersion = 1
                )
            )
        }

        val result = ironMindDao.getMemoriesForUser("user-A")
        assertEquals(listOf("mem-1", "mem-2", "mem-3"), result.map { it.id })
    }

    @Test
    fun getProtectionRulesForUser_deterministicTieBreakerOrdering() {
        // Insert rules with identical priority (10) out of order
        listOf("rule-gamma", "rule-alpha", "rule-beta").forEach { id ->
            ironMindDao.insertProtectionRule(
                ProtectionRuleEntity(
                    id = id,
                    userId = "user-A",
                    targetPackage = "com.test.$id",
                    targetCategory = null,
                    triggerType = "MANUAL",
                    conditions = "{}",
                    enabled = true,
                    priority = 10, // Identical priority
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }

        val result = ironMindDao.getProtectionRulesForUser("user-A")
        assertEquals(listOf("rule-alpha", "rule-beta", "rule-gamma"), result.map { it.id })
    }

    @Test
    fun getAutonomySettingsForUser_deterministicCapabilityOrdering() {
        // Insert autonomy settings with capabilities out of alphabetical order
        listOf("TASK_MANAGEMENT", "APP_BLOCKING", "NOTIFICATION_INTERVENTION").forEach { cap ->
            autonomySettingsDao.upsertSetting(
                AutonomySettingsEntity(
                    userId = "user-A",
                    capability = cap,
                    level = "AUTO",
                    updatedAt = 1000L
                )
            )
        }

        val result = autonomySettingsDao.getSettingsForUser("user-A")
        assertEquals(
            listOf("APP_BLOCKING", "NOTIFICATION_INTERVENTION", "TASK_MANAGEMENT"),
            result.map { it.capability }
        )
    }
}

