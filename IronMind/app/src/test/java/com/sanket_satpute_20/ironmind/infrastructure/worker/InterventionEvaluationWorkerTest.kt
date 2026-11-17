package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngine
import com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutorImpl
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterventionEvaluationWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var pipeline: InterventionExecutionPipeline
    private lateinit var autonomyRepo: AutonomySettingsRepository
    private lateinit var goalResurfacingEngine: GoalResurfacingEngine

    private val userId = "user-eval-123"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))
        pipeline = mockk(relaxed = true)
        autonomyRepo = mockk(relaxed = true)
        goalResurfacingEngine = mockk(relaxed = true)

        coEvery { autonomyRepo.getSettings(userId) } returns DomainResult.Success(
            AutonomySettings(
                userId = userId,
                levels = mapOf(AutonomyCapability.INTERVENTION_GENERATION to AutonomyLevel.FULL_AUTO),
                isGlobalPauseActive = false
            )
        )
    }

    private fun createWorker(inputData: Data): InterventionEvaluationWorker {
        every { workerParams.inputData } returns inputData
        return InterventionEvaluationWorker(
            appContext = context,
            workerParams = workerParams,
            interventionExecutionPipeline = pipeline,
            userSessionProvider = sessionProvider,
            autonomySettingsRepository = autonomyRepo,
            goalResurfacingEngine = goalResurfacingEngine
        )
    }

    @Test
    fun `valid execution with explicit candidate proposes through pipeline and succeeds`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(InterventionEvaluationWorker.KEY_USER_ID, userId)
            .putString(InterventionEvaluationWorker.KEY_RECOMMENDATION, "Take a 5-minute break")
            .putString(InterventionEvaluationWorker.KEY_REASON, "Continuous focus detected")
            .putString(InterventionEvaluationWorker.KEY_INTERVENTION_TYPE, InterventionType.REMIND.name)
            .putString(InterventionEvaluationWorker.KEY_TARGET_ENTITY_ID, "task-99")
            .putFloat(InterventionEvaluationWorker.KEY_CONFIDENCE, 0.9f)
            .build()

        val record = InterventionRecord(
            id = "rec-1",
            userId = userId,
            type = InterventionType.REMIND,
            state = InterventionState.APPROVED,
            title = "Take a 5-minute break",
            description = "Continuous focus detected",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { pipeline.propose(userId, any()) } returns DomainResult.Success(record)

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val candidateSlot = slot<AIOutput.InterventionRecommendation>()
        coVerify(exactly = 1) { pipeline.propose(userId, capture(candidateSlot)) }
        assertEquals(InterventionType.REMIND, candidateSlot.captured.interventionType)
        assertEquals("Take a 5-minute break", candidateSlot.captured.recommendation)
    }

    @Test
    fun `valid scheduled execution without explicit candidate evaluates neglected goals`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(InterventionEvaluationWorker.KEY_TRIGGER, InterventionEvaluationWorker.TRIGGER_SCHEDULED)
            .build()

        coEvery { goalResurfacingEngine.evaluateNeglectedGoals(userId) } returns Unit

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { goalResurfacingEngine.evaluateNeglectedGoals(userId) }
    }

    @Test
    fun `global pause active cleanly suppresses execution without invoking pipeline`() = runBlocking {
        coEvery { autonomyRepo.getSettings(userId) } returns DomainResult.Success(
            AutonomySettings(
                userId = userId,
                levels = mapOf(AutonomyCapability.INTERVENTION_GENERATION to AutonomyLevel.FULL_AUTO),
                isGlobalPauseActive = true
            )
        )

        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(InterventionEvaluationWorker.KEY_RECOMMENDATION, "Check on goal")
            .putString(InterventionEvaluationWorker.KEY_REASON, "Stale")
            .putString(InterventionEvaluationWorker.KEY_INTERVENTION_TYPE, InterventionType.ASK.name)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { pipeline.propose(any(), any()) }
        coVerify(exactly = 0) { goalResurfacingEngine.evaluateNeglectedGoals(any()) }
    }

    @Test
    fun `autonomy capability OFF cleanly suppresses execution without invoking pipeline`() = runBlocking {
        coEvery { autonomyRepo.getSettings(userId) } returns DomainResult.Success(
            AutonomySettings(
                userId = userId,
                levels = mapOf(AutonomyCapability.INTERVENTION_GENERATION to AutonomyLevel.OFF),
                isGlobalPauseActive = false
            )
        )

        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(InterventionEvaluationWorker.KEY_RECOMMENDATION, "Check on goal")
            .putString(InterventionEvaluationWorker.KEY_REASON, "Stale")
            .putString(InterventionEvaluationWorker.KEY_INTERVENTION_TYPE, InterventionType.ASK.name)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { pipeline.propose(any(), any()) }
    }

    @Test
    fun `missing session generation fails closed`() = runBlocking {
        val inputData = Data.Builder()
            // No KEY_SESSION_GENERATION
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `stale session generation fails closed`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 999L) // Stale generation
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `ownership mismatch fails closed`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(InterventionEvaluationWorker.KEY_USER_ID, "attacker-user-id")
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `pipeline propose failure returns Result retry`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(InterventionEvaluationWorker.KEY_RECOMMENDATION, "Check on goal")
            .putString(InterventionEvaluationWorker.KEY_REASON, "Stale")
            .putString(InterventionEvaluationWorker.KEY_INTERVENTION_TYPE, InterventionType.ASK.name)
            .build()

        coEvery { pipeline.propose(userId, any()) } returns DomainResult.Failure(RuntimeException("Database busy"))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `BackgroundExecutorImpl scheduleInterventionEvaluation enqueues event work with REPLACE policy`() {
        val mockWm = mockk<WorkManager>(relaxed = true)
        val requestSlot = slot<OneTimeWorkRequest>()

        every {
            mockWm.enqueueUniqueWork(
                InterventionEvaluationWorker.UNIQUE_WORK_NAME_EVENT,
                ExistingWorkPolicy.REPLACE,
                capture(requestSlot)
            )
        } returns mockk()

        val executor = BackgroundExecutorImpl(
            context = context,
            workManager = mockWm,
            sessionProvider = { sessionProvider }
        )

        executor.scheduleInterventionEvaluation("commitment_missed")

        val captured = requestSlot.captured
        val generation = captured.workSpec.input.getLong(UserSessionProvider.KEY_SESSION_GENERATION, -1L)
        val trigger = captured.workSpec.input.getString(InterventionEvaluationWorker.KEY_TRIGGER)

        assertEquals(sessionProvider.getSessionGeneration(), generation)
        assertEquals("commitment_missed", trigger)
        assertTrue(captured.tags.contains(InterventionEvaluationWorker.TAG))
    }

    @Test
    fun `BackgroundExecutorImpl schedulePeriodicInterventionEvaluation enqueues periodic work with UPDATE policy`() {
        val mockWm = mockk<WorkManager>(relaxed = true)
        val requestSlot = slot<androidx.work.PeriodicWorkRequest>()

        every {
            mockWm.enqueueUniquePeriodicWork(
                InterventionEvaluationWorker.UNIQUE_WORK_NAME_PERIODIC,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                capture(requestSlot)
            )
        } returns mockk()

        val executor = BackgroundExecutorImpl(
            context = context,
            workManager = mockWm,
            sessionProvider = { sessionProvider }
        )

        executor.schedulePeriodicInterventionEvaluation()

        val captured = requestSlot.captured
        val generation = captured.workSpec.input.getLong(UserSessionProvider.KEY_SESSION_GENERATION, -1L)
        val trigger = captured.workSpec.input.getString(InterventionEvaluationWorker.KEY_TRIGGER)

        assertEquals(sessionProvider.getSessionGeneration(), generation)
        assertEquals(InterventionEvaluationWorker.TRIGGER_SCHEDULED, trigger)
        assertTrue(captured.tags.contains(InterventionEvaluationWorker.TAG))
    }

    @Test
    fun `BackgroundExecutorImpl scheduleInterventionCandidate enqueues candidate work with distinct name`() {
        val mockWm = mockk<WorkManager>(relaxed = true)
        val requestSlot = slot<OneTimeWorkRequest>()

        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Take a break",
            reason = "Focus block exceeded",
            targetEntityId = "task-555",
            confidence = 0.95f
        )

        every {
            mockWm.enqueueUniqueWork(
                "${InterventionEvaluationWorker.UNIQUE_WORK_NAME_CANDIDATE_PREFIX}task-555",
                ExistingWorkPolicy.REPLACE,
                capture(requestSlot)
            )
        } returns mockk()

        val executor = BackgroundExecutorImpl(
            context = context,
            workManager = mockWm,
            sessionProvider = { sessionProvider }
        )

        executor.scheduleInterventionCandidate(candidate, userId)

        val captured = requestSlot.captured
        val generation = captured.workSpec.input.getLong(UserSessionProvider.KEY_SESSION_GENERATION, -1L)
        val recommendation = captured.workSpec.input.getString(InterventionEvaluationWorker.KEY_RECOMMENDATION)

        assertEquals(sessionProvider.getSessionGeneration(), generation)
        assertEquals("Take a break", recommendation)
        assertTrue(captured.tags.contains(InterventionEvaluationWorker.TAG))
    }
}

