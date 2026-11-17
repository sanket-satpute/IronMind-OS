package com.sanket_satpute_20.ironmind.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.CorrectionReviewDao
import com.sanket_satpute_20.ironmind.data.local.dao.InterventionDao
import com.sanket_satpute_20.ironmind.data.local.dao.PatternDao
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toDomainModel
import com.sanket_satpute_20.ironmind.data.repository.CorrectionReviewRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.InterventionRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.PatternRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionChannel
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.ApproveCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.DismissCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.RefuteCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.model.EvolvePersonalModelUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.LearnInterventionResponsePatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.LearnInterventionTimingPatternUseCase
import com.sanket_satpute_20.ironmind.execution.background.DefaultEventProcessor
import com.sanket_satpute_20.ironmind.execution.background.EventProcessingOutcome
import com.sanket_satpute_20.ironmind.infrastructure.worker.PatternUpdateWorker
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 8.3.1: Closed-Loop Learning Integration Test Suite.
 * Covers all 15 scenarios verifying the connected learning pipeline and epistemic safeguards.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class ClosedLoopLearningIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var correctionReviewDao: CorrectionReviewDao
    private lateinit var patternDao: PatternDao
    private lateinit var interventionDao: InterventionDao

    private lateinit var correctionReviewRepo: CorrectionReviewRepositoryImpl
    private lateinit var interventionRepo: InterventionRepositoryImpl
    private lateinit var patternRepo: PatternRepositoryImpl

    private lateinit var eventProcessor: DefaultEventProcessor
    private lateinit var approveCorrectionReviewUseCase: ApproveCorrectionReviewUseCase
    private lateinit var refuteCorrectionReviewUseCase: RefuteCorrectionReviewUseCase
    private lateinit var dismissCorrectionReviewUseCase: DismissCorrectionReviewUseCase

    private val userAlice = "user-alice"
    private val userBob = "user-bob"
    private val testClock = object : Clock {
        var time = 10000L
        override fun currentTimeMillis(): Long = time
    }
    private val testIdGenerator = object : IdGenerator {
        private var count = 0
        override fun generateId(): String = "gen-id-${++count}"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        correctionReviewDao = db.correctionReviewDao()
        patternDao = db.patternDao()
        interventionDao = db.interventionDao()

        val mockEventRepo = mockk<EventRepository>(relaxed = true)
        val mockEvalUseCase = mockk<EvaluateCorrectionReviewDecisionUseCase>(relaxed = true)

        patternRepo = PatternRepositoryImpl(dao = patternDao, database = db, eventDao = db.ironMindDao())
        interventionRepo = InterventionRepositoryImpl(interventionDao = interventionDao)
        correctionReviewRepo = CorrectionReviewRepositoryImpl(
            eventRepository = mockEventRepo,
            evaluateCorrectionReviewDecisionUseCase = mockEvalUseCase,
            correctionReviewDao = correctionReviewDao,
            database = db,
            patternRepository = patternRepo
        )

        approveCorrectionReviewUseCase = ApproveCorrectionReviewUseCase(correctionReviewRepo, testClock)
        refuteCorrectionReviewUseCase = RefuteCorrectionReviewUseCase(correctionReviewRepo, testClock)
        dismissCorrectionReviewUseCase = DismissCorrectionReviewUseCase(correctionReviewRepo, testClock)

        eventProcessor = DefaultEventProcessor(
            evaluateCorrectionReviewDecisionUseCase = mockEvalUseCase,
            correctionReviewRepository = correctionReviewRepo,
            interventionRepository = interventionRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createInterventionRecord(
        id: String,
        userId: String = userAlice,
        state: InterventionState = InterventionState.TRIGGERED,
        resolutionReason: InterventionResolutionReason? = null
    ) = InterventionRecord(
        id = id,
        userId = userId,
        type = InterventionType.REMIND,
        state = state,
        title = "Take a break",
        description = "Go for a short walk",
        channel = InterventionChannel.IN_APP,
        resolutionReason = resolutionReason,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun createCorrectionReviewItem(
        id: String,
        userId: String = userAlice,
        causalId: String = "pat-1",
        causalType: CausalHypothesisType = CausalHypothesisType.PATTERN,
        status: CorrectionReviewStatus = CorrectionReviewStatus.PENDING
    ) = CorrectionReviewItem(
        id = id,
        userId = userId,
        sourceEventId = "evt-$id",
        recommendationId = "rec-$id",
        causalHypothesis = CausalHypothesisReference(id = causalId, type = causalType),
        correctionScope = CorrectionScope.INTERVENTION_CORRECTION,
        correctedText = "Only suggest after 2 hours",
        decisionState = "DEFERRED_FOR_CONSIDERATION",
        reviewStatus = status,
        createdAt = 2000L,
        updatedAt = 2000L
    )

    private fun createPatternEntity(
        id: String,
        userId: String = userAlice,
        confidence: Float = 0.8f,
        confirmationState: String = MemoryConfirmationState.UNCONFIRMED.name,
        status: String = PatternStatus.ACTIVE.name
    ) = PatternEntity(
        id = id,
        userId = userId,
        fingerprint = "fp-$id",
        type = PatternType.TIME_PATTERN.name,
        description = "Works best in the morning",
        conditions = "time == morning",
        predictedBehavior = "high focus",
        confidence = confidence,
        evidenceCount = 5,
        evidenceReferences = null,
        firstObservedAt = 1000L,
        lastObservedAt = 2000L,
        status = status,
        confirmationState = confirmationState,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    // =========================================================================
    // Scenario 1: User response to intervention updates record state (RESPONSE with null reason)
    // =========================================================================
    @Test
    fun `scenario 1 - accepted intervention event transitions to RESPONSE with null resolution reason`() = runBlocking {
        val record = createInterventionRecord("int-accept-1")
        interventionRepo.save(record)

        val event = Event(
            id = "evt-accept-1",
            userId = userAlice,
            type = EventType.INTERVENTION_ACCEPTED,
            causationId = "int-accept-1",
            occurredAt = 5000L,
            recordedAt = 5000L,
            source = EntitySource.USER
        )

        val outcome = eventProcessor.processEvent(event)
        assertTrue(outcome is EventProcessingOutcome.Success)

        val updated = (interventionRepo.getById("int-accept-1") as Result.Success).data
        assertNotNull(updated)
        assertEquals(InterventionState.RESPONSE, updated?.state)
        assertNull(updated?.resolutionReason)
    }

    @Test
    fun `scenario 1b - accepted intervention remains eligible for subsequent outcome recording`() = runBlocking {
        val record = createInterventionRecord("int-lifecycle-1")
        interventionRepo.save(record)

        // 1. User accepts
        val acceptEvent = Event(
            id = "evt-accept-life-1",
            userId = userAlice,
            type = EventType.INTERVENTION_ACCEPTED,
            causationId = "int-lifecycle-1",
            occurredAt = 5000L,
            recordedAt = 5000L,
            source = EntitySource.USER
        )
        eventProcessor.processEvent(acceptEvent)

        val accepted = (interventionRepo.getById("int-lifecycle-1") as Result.Success).data
        assertEquals(InterventionState.RESPONSE, accepted?.state)
        assertNull(accepted?.resolutionReason)

        // 2. Verified outcome is subsequently recorded
        val outcomeEvent = Event(
            id = "evt-outcome-life-1",
            userId = userAlice,
            type = EventType.OUTCOME_RECORDED,
            causationId = "int-lifecycle-1",
            occurredAt = 6000L,
            recordedAt = 6000L,
            source = EntitySource.USER
        )
        val outcomeResult = eventProcessor.processEvent(outcomeEvent)
        assertTrue(outcomeResult is EventProcessingOutcome.Success)

        val resolved = (interventionRepo.getById("int-lifecycle-1") as Result.Success).data
        assertEquals(InterventionState.OUTCOME, resolved?.state)
        assertEquals(InterventionResolutionReason.SUCCESS, resolved?.resolutionReason)
    }

    @Test
    fun `scenario 1c - verified task completion resolves accepted intervention to OUTCOME SUCCESS`() = runBlocking {
        val record = createInterventionRecord("int-task-1", state = InterventionState.RESPONSE, resolutionReason = null)
        interventionRepo.save(record)

        val taskCompletedEvent = Event(
            id = "evt-task-comp-1",
            userId = userAlice,
            type = EventType.TASK_COMPLETED,
            entityType = "Task",
            entityId = "task-99",
            causationId = "int-task-1",
            occurredAt = 7000L,
            recordedAt = 7000L,
            source = EntitySource.USER
        )

        val outcome = eventProcessor.processEvent(taskCompletedEvent)
        assertTrue(outcome is EventProcessingOutcome.Success)

        val updated = (interventionRepo.getById("int-task-1") as Result.Success).data
        assertEquals(InterventionState.OUTCOME, updated?.state)
        assertEquals(InterventionResolutionReason.SUCCESS, updated?.resolutionReason)
    }

    @Test
    fun `scenario 1d - verified commitment completion resolves accepted intervention to OUTCOME SUCCESS`() = runBlocking {
        val record = createInterventionRecord("int-comm-1", state = InterventionState.RESPONSE, resolutionReason = null)
        interventionRepo.save(record)

        val commitCompletedEvent = Event(
            id = "evt-comm-comp-1",
            userId = userAlice,
            type = EventType.COMMITMENT_COMPLETED,
            entityType = "Commitment",
            entityId = "comm-88",
            causationId = "int-comm-1",
            occurredAt = 7500L,
            recordedAt = 7500L,
            source = EntitySource.USER
        )

        val outcome = eventProcessor.processEvent(commitCompletedEvent)
        assertTrue(outcome is EventProcessingOutcome.Success)

        val updated = (interventionRepo.getById("int-comm-1") as Result.Success).data
        assertEquals(InterventionState.OUTCOME, updated?.state)
        assertEquals(InterventionResolutionReason.SUCCESS, updated?.resolutionReason)
    }

    @Test
    fun `scenario 1e - already completed or failed interventions reject duplicate resolution or state mutation`() = runBlocking {
        val completedRecord = createInterventionRecord(
            "int-already-done",
            state = InterventionState.OUTCOME,
            resolutionReason = InterventionResolutionReason.SUCCESS
        )
        val failedRecord = createInterventionRecord(
            "int-already-failed",
            state = InterventionState.FAILED,
            resolutionReason = InterventionResolutionReason.BEHAVIORAL_FAILURE
        )
        interventionRepo.save(completedRecord)
        interventionRepo.save(failedRecord)

        // Attempt to accept already completed intervention
        val acceptEvent = Event(
            id = "evt-dup-accept",
            userId = userAlice,
            type = EventType.INTERVENTION_ACCEPTED,
            causationId = "int-already-done",
            occurredAt = 8000L,
            recordedAt = 8000L,
            source = EntitySource.USER
        )
        eventProcessor.processEvent(acceptEvent)

        // Attempt to record outcome on already failed intervention
        val outcomeEvent = Event(
            id = "evt-dup-outcome",
            userId = userAlice,
            type = EventType.OUTCOME_RECORDED,
            causationId = "int-already-failed",
            occurredAt = 8000L,
            recordedAt = 8000L,
            source = EntitySource.USER
        )
        eventProcessor.processEvent(outcomeEvent)

        val postCompleted = (interventionRepo.getById("int-already-done") as Result.Success).data
        val postFailed = (interventionRepo.getById("int-already-failed") as Result.Success).data

        // Neither record should have mutated
        assertEquals(InterventionState.OUTCOME, postCompleted?.state)
        assertEquals(InterventionResolutionReason.SUCCESS, postCompleted?.resolutionReason)
        assertEquals(InterventionState.FAILED, postFailed?.state)
        assertEquals(InterventionResolutionReason.BEHAVIORAL_FAILURE, postFailed?.resolutionReason)
    }

    @Test
    fun `scenario 1f - cross-user isolation prevents mutating foreign user interventions`() = runBlocking {
        val aliceRecord = createInterventionRecord("int-alice-iso", userId = userAlice, state = InterventionState.TRIGGERED)
        interventionRepo.save(aliceRecord)

        // Bob attempts to resolve Alice's intervention
        val bobEvent = Event(
            id = "evt-bob-hack",
            userId = userBob,
            type = EventType.INTERVENTION_ACCEPTED,
            causationId = "int-alice-iso",
            occurredAt = 9000L,
            recordedAt = 9000L,
            source = EntitySource.USER
        )
        eventProcessor.processEvent(bobEvent)

        val afterBobEvent = (interventionRepo.getById("int-alice-iso") as Result.Success).data
        // Alice's record must remain TRIGGERED with null reason
        assertEquals(InterventionState.TRIGGERED, afterBobEvent?.state)
        assertNull(afterBobEvent?.resolutionReason)
    }

    // =========================================================================
    // Scenario 2: Rejected, dismissed, or overridden intervention updates record resolution cleanly
    // =========================================================================
    @Test
    fun `scenario 2 - rejected or dismissed intervention updates record resolution`() = runBlocking {
        val recordDismiss = createInterventionRecord("int-dismiss-1")
        val recordIgnore = createInterventionRecord("int-ignore-1")
        val recordOverride = createInterventionRecord("int-override-1")
        interventionRepo.save(recordDismiss)
        interventionRepo.save(recordIgnore)
        interventionRepo.save(recordOverride)

        val dismissEvent = Event(
            id = "evt-dismiss-1",
            userId = userAlice,
            type = EventType.INTERVENTION_DISMISSED,
            causationId = "int-dismiss-1",
            occurredAt = 5000L,
            recordedAt = 5000L,
            source = EntitySource.USER
        )
        val ignoreEvent = Event(
            id = "evt-ignore-1",
            userId = userAlice,
            type = EventType.INTERVENTION_IGNORED,
            causationId = "int-ignore-1",
            occurredAt = 5000L,
            recordedAt = 5000L,
            source = EntitySource.SYSTEM
        )
        val overrideEvent = Event(
            id = "evt-override-1",
            userId = userAlice,
            type = EventType.INTERVENTION_OVERRIDDEN,
            causationId = "int-override-1",
            occurredAt = 5000L,
            recordedAt = 5000L,
            source = EntitySource.USER
        )

        eventProcessor.processEvent(dismissEvent)
        eventProcessor.processEvent(ignoreEvent)
        eventProcessor.processEvent(overrideEvent)

        val updatedDismiss = (interventionRepo.getById("int-dismiss-1") as Result.Success).data
        val updatedIgnore = (interventionRepo.getById("int-ignore-1") as Result.Success).data
        val updatedOverride = (interventionRepo.getById("int-override-1") as Result.Success).data

        assertEquals(InterventionResolutionReason.DISMISSED, updatedDismiss?.resolutionReason)
        assertEquals(InterventionResolutionReason.IGNORED, updatedIgnore?.resolutionReason)
        assertEquals(InterventionResolutionReason.OVERRIDDEN, updatedOverride?.resolutionReason)
    }

    // =========================================================================
    // Scenario 3: Completed intervention outcome
    // =========================================================================
    @Test
    fun `scenario 3 - completed intervention outcome recorded event transitions to OUTCOME SUCCESS`() = runBlocking {
        val record = createInterventionRecord("int-outcome-1", state = InterventionState.RESPONSE)
        interventionRepo.save(record)

        val event = Event(
            id = "evt-outcome-1",
            userId = userAlice,
            type = EventType.OUTCOME_RECORDED,
            causationId = "int-outcome-1",
            occurredAt = 6000L,
            recordedAt = 6000L,
            source = EntitySource.USER
        )

        val outcome = eventProcessor.processEvent(event)
        assertTrue(outcome is EventProcessingOutcome.Success)

        val updated = (interventionRepo.getById("int-outcome-1") as Result.Success).data
        assertEquals(InterventionState.OUTCOME, updated?.state)
        assertEquals(InterventionResolutionReason.SUCCESS, updated?.resolutionReason)
    }

    // =========================================================================
    // Scenario 4: Failed intervention outcome (TECHNICAL_FAILURE vs BEHAVIORAL_FAILURE)
    // =========================================================================
    @Test
    fun `scenario 4 - learning use cases filter out technical failures but include behavioral failures`() = runBlocking {
        val techFailure = createInterventionRecord("int-tech-1", resolutionReason = InterventionResolutionReason.TECHNICAL_FAILURE, state = InterventionState.FAILED)
        val behavFailure = createInterventionRecord("int-behav-1", resolutionReason = InterventionResolutionReason.BEHAVIORAL_FAILURE, state = InterventionState.FAILED)
        val pendingAccept = createInterventionRecord("int-pending-1", state = InterventionState.RESPONSE, resolutionReason = null)
        interventionRepo.save(techFailure)
        interventionRepo.save(behavFailure)
        interventionRepo.save(pendingAccept)

        var capturedPrompt = ""
        val mockAI = object : IronMindAI {
            override suspend fun process(request: com.sanket_satpute_20.ironmind.domain.ai.AIRequest): Result<AIOutput, Exception> {
                capturedPrompt = request.input
                return Result.Success(AIOutput.NoAction())
            }
        }

        val useCase = LearnInterventionResponsePatternUseCase(
            interventionRepository = interventionRepo,
            patternRepository = patternRepo,
            ironMindAI = mockAI,
            clock = testClock,
            idGenerator = testIdGenerator
        )

        val result = useCase(userAlice, 0L)
        assertTrue(result is Result.Success)
        // Verify behavioral failure is present in the learning context prompt, but technical failure and pending accepted are excluded
        assertTrue(capturedPrompt.contains("BEHAVIORAL_FAILURE"))
        assertTrue(!capturedPrompt.contains("TECHNICAL_FAILURE"))
        assertTrue(!capturedPrompt.contains("int-pending-1"))
    }

    // =========================================================================
    // Scenario 5: Duplicate event retry (idempotency, no duplicate state transitions)
    // =========================================================================
    @Test
    fun `scenario 5 - duplicate event retry is idempotent without corruption`() = runBlocking {
        val record = createInterventionRecord("int-idempotent-1")
        interventionRepo.save(record)

        val event = Event(
            id = "evt-idem-1",
            userId = userAlice,
            type = EventType.INTERVENTION_ACCEPTED,
            causationId = "int-idempotent-1",
            occurredAt = 5000L,
            recordedAt = 5000L,
            source = EntitySource.USER
        )

        val firstOutcome = eventProcessor.processEvent(event)
        val secondOutcome = eventProcessor.processEvent(event)

        assertTrue(firstOutcome is EventProcessingOutcome.Success)
        assertTrue(secondOutcome is EventProcessingOutcome.Success)

        val updated = (interventionRepo.getById("int-idempotent-1") as Result.Success).data
        assertEquals(InterventionState.RESPONSE, updated?.state)
        assertNull(updated?.resolutionReason)
    }

    // =========================================================================
    // Scenario 6: Insufficient evidence (zero valid interventions returns null candidate)
    // =========================================================================
    @Test
    fun `scenario 6 - zero valid interventions returns null candidate without calling AI`() = runBlocking {
        // Only technical failure exists
        val techFailure = createInterventionRecord("int-only-tech", resolutionReason = InterventionResolutionReason.TECHNICAL_FAILURE)
        interventionRepo.save(techFailure)

        var aiCalled = false
        val mockAI = object : IronMindAI {
            override suspend fun process(request: com.sanket_satpute_20.ironmind.domain.ai.AIRequest): Result<AIOutput, Exception> {
                aiCalled = true
                return Result.Success(AIOutput.NoAction())
            }
        }

        val responseUseCase = LearnInterventionResponsePatternUseCase(
            interventionRepository = interventionRepo,
            patternRepository = patternRepo,
            ironMindAI = mockAI,
            clock = testClock,
            idGenerator = testIdGenerator
        )
        val timingUseCase = LearnInterventionTimingPatternUseCase(
            interventionRepository = interventionRepo,
            patternRepository = patternRepo,
            ironMindAI = mockAI,
            clock = testClock,
            idGenerator = testIdGenerator
        )

        val resResponse = responseUseCase(userAlice, 0L)
        val resTiming = timingUseCase(userAlice, 0L)

        assertTrue(resResponse is Result.Success && resResponse.data == null)
        assertTrue(resTiming is Result.Success && resTiming.data == null)
        assertTrue(!aiCalled)
    }

    // =========================================================================
    // Scenario 7: User-confirmed pattern protection (never decayed by correction approval)
    // =========================================================================
    @Test
    fun `scenario 7 - approved correction NEVER demotes or decays USER_CONFIRMED pattern`() = runBlocking {
        val pattern = createPatternEntity(
            id = "pat-confirmed-1",
            confidence = 0.95f,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED.name
        )
        patternDao.insertPattern(pattern)

        val reviewItem = createCorrectionReviewItem("cr-prot-1", causalId = "pat-confirmed-1")
        correctionReviewRepo.persistCandidate(reviewItem)

        val result = approveCorrectionReviewUseCase(userAlice, "cr-prot-1")
        assertTrue(result is Result.Success)

        val after = patternDao.getPatternById("pat-confirmed-1")
        assertNotNull(after)
        assertEquals(0.95f, after!!.confidence, 0.001f)
        assertEquals(MemoryConfirmationState.USER_CONFIRMED.name, after.confirmationState)
    }

    // =========================================================================
    // Scenario 8: User-rejected pattern protection (never reactivated or mutated)
    // =========================================================================
    @Test
    fun `scenario 8 - approved correction does not reactivate or alter USER_REJECTED pattern`() = runBlocking {
        val pattern = createPatternEntity(
            id = "pat-rejected-1",
            confidence = 0.5f,
            confirmationState = MemoryConfirmationState.USER_REJECTED.name,
            status = PatternStatus.DELETED.name
        )
        patternDao.insertPattern(pattern)

        val reviewItem = createCorrectionReviewItem("cr-rej-1", causalId = "pat-rejected-1")
        correctionReviewRepo.persistCandidate(reviewItem)

        val result = approveCorrectionReviewUseCase(userAlice, "cr-rej-1")
        assertTrue(result is Result.Success)

        val after = patternDao.getPatternById("pat-rejected-1")
        assertNotNull(after)
        assertEquals(0.5f, after!!.confidence, 0.001f)
        assertEquals(MemoryConfirmationState.USER_REJECTED.name, after.confirmationState)
        assertEquals(PatternStatus.DELETED.name, after.status)
    }

    // =========================================================================
    // Scenario 9: Correction approval decays unconfirmed pattern confidence in atomic transaction
    // =========================================================================
    @Test
    fun `scenario 9 - correction approval decays unconfirmed pattern confidence and records audit event`() = runBlocking {
        val pattern = createPatternEntity(
            id = "pat-unconfirmed-1",
            confidence = 0.8f,
            confirmationState = MemoryConfirmationState.UNCONFIRMED.name
        )
        patternDao.insertPattern(pattern)

        val reviewItem = createCorrectionReviewItem("cr-decay-1", causalId = "pat-unconfirmed-1")
        correctionReviewRepo.persistCandidate(reviewItem)

        val result = approveCorrectionReviewUseCase(userAlice, "cr-decay-1")
        assertTrue(result is Result.Success)

        // Review transitioned to APPROVED
        val updatedReview = (correctionReviewRepo.getReview(userAlice, "cr-decay-1") as Result.Success).data
        assertEquals(CorrectionReviewStatus.APPROVED, updatedReview?.reviewStatus)

        // Pattern confidence decayed from 0.80 to 0.60 (0.80 * 0.75)
        val updatedPattern = patternDao.getPatternById("pat-unconfirmed-1")
        assertNotNull(updatedPattern)
        assertEquals(0.6f, updatedPattern!!.confidence, 0.001f)

        // Audit event PATTERN_DECAYED recorded in Room
        val events = db.ironMindDao().getEventsForUser(userAlice)
        assertTrue(events.any { it.type == EventType.PATTERN_DECAYED.name && it.entityId == "pat-unconfirmed-1" })
    }

    // =========================================================================
    // Scenario 10: Correction refutation retains hypothesis without decay
    // =========================================================================
    @Test
    fun `scenario 10 - correction refutation retains hypothesis confidence without decay`() = runBlocking {
        val pattern = createPatternEntity(
            id = "pat-refute-1",
            confidence = 0.8f,
            confirmationState = MemoryConfirmationState.UNCONFIRMED.name
        )
        patternDao.insertPattern(pattern)

        val reviewItem = createCorrectionReviewItem("cr-refute-1", causalId = "pat-refute-1")
        correctionReviewRepo.persistCandidate(reviewItem)

        val result = refuteCorrectionReviewUseCase(userAlice, "cr-refute-1")
        assertTrue(result is Result.Success)

        val updatedReview = (correctionReviewRepo.getReview(userAlice, "cr-refute-1") as Result.Success).data
        assertEquals(CorrectionReviewStatus.REFUTED, updatedReview?.reviewStatus)

        val updatedPattern = patternDao.getPatternById("pat-refute-1")
        assertEquals(0.8f, updatedPattern!!.confidence, 0.001f)
    }

    // =========================================================================
    // Scenario 11: Correction dismissal retains hypothesis without decay
    // =========================================================================
    @Test
    fun `scenario 11 - correction dismissal retains hypothesis confidence without decay`() = runBlocking {
        val pattern = createPatternEntity(
            id = "pat-dismiss-1",
            confidence = 0.8f,
            confirmationState = MemoryConfirmationState.UNCONFIRMED.name
        )
        patternDao.insertPattern(pattern)

        val reviewItem = createCorrectionReviewItem("cr-dismiss-1", causalId = "pat-dismiss-1")
        correctionReviewRepo.persistCandidate(reviewItem)

        val result = dismissCorrectionReviewUseCase(userAlice, "cr-dismiss-1")
        assertTrue(result is Result.Success)

        val updatedReview = (correctionReviewRepo.getReview(userAlice, "cr-dismiss-1") as Result.Success).data
        assertEquals(CorrectionReviewStatus.DISMISSED, updatedReview?.reviewStatus)

        val updatedPattern = patternDao.getPatternById("pat-dismiss-1")
        assertEquals(0.8f, updatedPattern!!.confidence, 0.001f)
    }

    // =========================================================================
    // Scenario 12: Cross-user ownership protection (fails closed)
    // =========================================================================
    @Test
    fun `scenario 12 - cross user ownership protection fails closed`() = runBlocking {
        val reviewItem = createCorrectionReviewItem("cr-alice-private", userId = userAlice)
        correctionReviewRepo.persistCandidate(reviewItem)

        // Bob attempts to approve Alice's review
        val bobResult = approveCorrectionReviewUseCase(userBob, "cr-alice-private")
        assertTrue(bobResult is Result.Failure)

        // Alice's review remains PENDING
        val aliceItem = (correctionReviewRepo.getReview(userAlice, "cr-alice-private") as Result.Success).data
        assertEquals(CorrectionReviewStatus.PENDING, aliceItem?.reviewStatus)
    }

    // =========================================================================
    // Scenario 13: Stale session generation fails closed
    // =========================================================================
    @Test
    fun `scenario 13 - stale session generation fails closed in PatternUpdateWorker`() = runBlocking {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userAlice, false))
        val currentGen = sessionProvider.getSessionGeneration()

        val patternEngine = mockk<PatternEngine>(relaxed = true)
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, currentGen - 1) // Stale generation!
            .putString(PatternUpdateWorker.KEY_USER_ID, userAlice)
            .build()
        coEvery { workerParams.inputData } returns inputData

        val worker = PatternUpdateWorker(
            appContext = context,
            workerParams = workerParams,
            patternEngine = patternEngine,
            userSessionProvider = sessionProvider
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    // =========================================================================
    // Scenario 14: Global pause and autonomy level suppression
    // =========================================================================
    @Test
    fun `scenario 14 - global pause and autonomy off suppress learning execution`() = runBlocking {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userAlice, false))
        val currentGen = sessionProvider.getSessionGeneration()

        val patternEngine = mockk<PatternEngine>(relaxed = true)
        coEvery { patternEngine.decayStalePatterns(userAlice) } returns Result.Success(Unit)
        val evolveUseCase = mockk<EvolvePersonalModelUseCase>(relaxed = true)
        val autonomyRepo = mockk<AutonomySettingsRepository>(relaxed = true)

        // Global pause is ACTIVE
        coEvery { autonomyRepo.getSettings(userAlice) } returns Result.Success(
            AutonomySettings(
                userId = userAlice,
                isGlobalPauseActive = true,
                levels = mapOf(
                    AutonomyCapability.BACKGROUND_LEARNING to AutonomyLevel.FULL_AUTO,
                    AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.FULL_AUTO
                )
            )
        )

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, currentGen)
            .putString(PatternUpdateWorker.KEY_USER_ID, userAlice)
            .build()
        coEvery { workerParams.inputData } returns inputData

        var responseUseCaseCalled = false
        val mockAI = object : IronMindAI {
            override suspend fun process(request: com.sanket_satpute_20.ironmind.domain.ai.AIRequest): Result<AIOutput, Exception> {
                responseUseCaseCalled = true
                return Result.Success(AIOutput.NoAction())
            }
        }
        val responseUseCase = LearnInterventionResponsePatternUseCase(
            interventionRepository = interventionRepo,
            patternRepository = patternRepo,
            ironMindAI = mockAI,
            clock = testClock,
            idGenerator = testIdGenerator
        )

        val worker = PatternUpdateWorker(
            appContext = context,
            workerParams = workerParams,
            patternEngine = patternEngine,
            userSessionProvider = sessionProvider,
            evolvePersonalModelUseCase = evolveUseCase,
            autonomySettingsRepository = autonomyRepo,
            learnInterventionResponsePatternUseCase = responseUseCase,
            clock = testClock
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        // Background learning was suppressed by global pause
        assertTrue(!responseUseCaseCalled)
    }

    // =========================================================================
    // Scenario 15: Transaction rollback on hypothesis update failure
    // =========================================================================
    @Test
    fun `scenario 15 - transaction rolls back review state if hypothesis ownership mismatch occurs`() = runBlocking {
        // Pattern belongs to Bob (cross-user mismatch!)
        val bobPattern = createPatternEntity(
            id = "pat-bob-secret",
            userId = userBob,
            confidence = 0.8f,
            confirmationState = MemoryConfirmationState.UNCONFIRMED.name
        )
        patternDao.insertPattern(bobPattern)

        // Alice has a correction review maliciously or mistakenly pointing to Bob's pattern
        val aliceReview = createCorrectionReviewItem(
            id = "cr-alice-bad",
            userId = userAlice,
            causalId = "pat-bob-secret"
        )
        correctionReviewRepo.persistCandidate(aliceReview)

        // Attempting to approve must fail and roll back the whole Room transaction
        val result = approveCorrectionReviewUseCase(userAlice, "cr-alice-bad")
        assertTrue(result is Result.Failure)

        // Verify rollback: Alice's review must remain PENDING
        val reviewAfter = (correctionReviewRepo.getReview(userAlice, "cr-alice-bad") as Result.Success).data
        assertEquals(CorrectionReviewStatus.PENDING, reviewAfter?.reviewStatus)

        // Verify Bob's pattern was untouched
        val bobPatternAfter = patternDao.getPatternById("pat-bob-secret")
        assertEquals(0.8f, bobPatternAfter!!.confidence, 0.001f)
    }
}

