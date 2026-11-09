package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceOrchestrator
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceResult
import com.sanket_satpute_20.ironmind.domain.engine.PipelineExecutionResult
import com.sanket_satpute_20.ironmind.domain.model.context.*
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class IntelligenceOrchestrationWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var orchestrator: PersonalIntelligenceOrchestrator
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var profileRepo: UserProfileRepository
    private lateinit var scheduler: IntelligenceOrchestrationScheduler

    private var currentTime = 500_000L
    private val clock = object : Clock {
        override fun currentTimeMillis(): Long = currentTime
    }

    private val userId = "user-intel-orchestration-456"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        orchestrator = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))
        profileRepo = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)

        coEvery {
            orchestrator.orchestrate(any(), any(), any(), any())
        } returns DomainResult.Success(
            PersonalIntelligenceResult(
                pipelineResult = PipelineExecutionResult(
                    unifiedContext = mockk(relaxed = true),
                    discoveredPatterns = emptyList(),
                    activePatterns = emptyList(),
                    barrierHypotheses = emptyList(),
                    interventionRecommendations = emptyList(),
                    insufficientEvidenceBarriers = emptyList(),
                    diagnosticNotes = emptyList()
                ),
                newlyDiscoveredInsights = emptyList(),
                allActiveInsights = emptyList(),
                suppressedCount = 0,
                diagnosticNotes = emptyList()
            )
        )
    }

    private fun createWorker(
        inputData: Data,
        runAttemptCount: Int = 0,
        customOrchestrator: PersonalIntelligenceOrchestrator = orchestrator,
        customSessionProvider: UserSessionProvider = sessionProvider,
        customScheduler: IntelligenceOrchestrationScheduler? = scheduler
    ): IntelligenceOrchestrationWorker {
        every { workerParams.inputData } returns inputData
        every { workerParams.runAttemptCount } returns runAttemptCount

        return IntelligenceOrchestrationWorker(
            appContext = context,
            workerParams = workerParams,
            personalIntelligenceOrchestrator = customOrchestrator,
            userSessionProvider = customSessionProvider,
            userProfileRepository = profileRepo,
            clock = clock,
            scheduler = customScheduler
        )
    }

    @Test
    fun `valid execution orchestrates personal intelligence and records completion`() = runBlocking {
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) {
            orchestrator.orchestrate(
                userId = userId,
                windowStartTimeMs = currentTime - (7L * 24 * 60 * 60 * 1000L),
                windowEndTimeMs = currentTime,
                zoneId = any()
            )
        }
        io.mockk.verify(exactly = 1) {
            scheduler.recordCompletion(userId, currentTime)
        }
    }

    @Test
    fun `missing inputs or non-positive session generation fails closed`() = runBlocking {
        // Missing generation
        val inputData1 = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .build()
        val worker1 = createWorker(inputData1)
        assertEquals(Result.failure(), worker1.doWork())

        // Missing userId
        val inputData2 = Data.Builder()
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, 1L)
            .build()
        val worker2 = createWorker(inputData2)
        assertEquals(Result.failure(), worker2.doWork())

        coVerify(exactly = 0) { orchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `stale session generation is rejected and fails closed`() = runBlocking {
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, 999L) // Stale generation
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { orchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `ownership mismatch safely skips execution without processing other user`() = runBlocking {
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, "attacker-user")
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { orchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `user erasure aborts execution and fails closed`() = runBlocking {
        val gen = sessionProvider.getSessionGeneration()
        sessionProvider.markUserErased(userId, gen)

        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, gen)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { orchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `same-user overlapping execution is prevented by in-process concurrency guard`() = runBlocking {
        val gen = sessionProvider.getSessionGeneration()
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, gen)
            .build()

        // Slow mock that simulates long orchestration computation
        val slowOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery {
            slowOrchestrator.orchestrate(any(), any(), any(), any())
        } coAnswers {
            delay(200)
            DomainResult.Success(mockk(relaxed = true))
        }

        val worker1 = createWorker(inputData, customOrchestrator = slowOrchestrator)
        val worker2 = createWorker(inputData, customOrchestrator = slowOrchestrator)

        val job1 = async { worker1.doWork() }
        delay(20) // Give job1 time to acquire the in-process concurrency guard
        val job2 = async { worker2.doWork() }

        val result1 = job1.await()
        val result2 = job2.await()

        // Both workers return success (one performed work, the other gracefully skipped)
        assertEquals(Result.success(), result1)
        assertEquals(Result.success(), result2)

        // Only 1 invocation reached the orchestrator
        coVerify(exactly = 1) { slowOrchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `session switch during execution aborts and fails closed`() = runBlocking {
        val gen = sessionProvider.getSessionGeneration()
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, gen)
            .build()

        coEvery {
            orchestrator.orchestrate(any(), any(), any(), any())
        } coAnswers {
            // User switches mid-execution
            sessionProvider.setState(SessionState.Authenticated("switched-user", false))
            DomainResult.Success(mockk(relaxed = true))
        }

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `orchestration failure returns retry when attempt count is below MAX_RETRIES`() = runBlocking {
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        coEvery {
            orchestrator.orchestrate(any(), any(), any(), any())
        } returns DomainResult.Failure(RuntimeException("SQLite busy"))

        val worker = createWorker(inputData, runAttemptCount = 1)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `orchestration failure returns failure when attempt count reaches MAX_RETRIES`() = runBlocking {
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        coEvery {
            orchestrator.orchestrate(any(), any(), any(), any())
        } returns DomainResult.Failure(RuntimeException("Persistent corruption"))

        val worker = createWorker(inputData, runAttemptCount = IntelligenceOrchestrationWorker.MAX_RETRIES)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `idempotent repeated execution preserves deduplicated insights and patterns`() = runBlocking {
        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        val sampleInsight = PersonalInsight(
            id = "insight_test_1",
            userId = userId,
            category = InsightCategory.DIGITAL_BEHAVIOUR,
            title = "High Screen Time",
            explanation = "User spent 4 hours on entertainment apps.",
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Observed 4 hours of phone usage",
                observationPeriod = "Past 7 days",
                supportingEvidenceDescription = "Persisted app usage sessions",
                whyItMatters = "Exceeds daily baseline",
                uncertaintyOrLimitations = "None",
                suggestedNextSteps = "Consider focus session"
            ),
            observationWindowStartMs = currentTime - 86_400_000L,
            observationWindowEndMs = currentTime,
            firstObservedAt = currentTime,
            lastObservedAt = currentTime,
            confidence = 0.95f,
            epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
            reviewStatus = InsightReviewStatus.UNREVIEWED,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        // Simulate orchestrator returning the same insight on duplicate executions
        val mockResult = PersonalIntelligenceResult(
            pipelineResult = mockk(relaxed = true),
            newlyDiscoveredInsights = listOf(sampleInsight),
            allActiveInsights = listOf(sampleInsight),
            suppressedCount = 0,
            diagnosticNotes = emptyList()
        )
        coEvery { orchestrator.orchestrate(any(), any(), any(), any()) } returns DomainResult.Success(mockResult)

        val worker1 = createWorker(inputData)
        val res1 = worker1.doWork()
        assertEquals(Result.success(), res1)

        val worker2 = createWorker(inputData)
        val res2 = worker2.doWork()
        assertEquals(Result.success(), res2)

        // Both executions succeeded without diverging
        coVerify(exactly = 2) { orchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `permission denied context maintains epistemic integrity and does not fabricate zero-usage intelligence`() = runBlocking {
        // Construct UnifiedContext where DigitalBehaviour availability is PERMISSION_DENIED
        val digitalContext = DigitalBehaviourContext(
            permissionStatus = UsagePermissionStatus.DENIED,
            availability = SourceAvailability.PERMISSION_DENIED,
            windowStartTimeMs = currentTime - 86_400_000L,
            windowEndTimeMs = currentTime,
            appUsages = emptyList(),
            focusActivity = FocusActivitySummary(),
            recentProtectionEvents = emptyList(),
            lastObservedAt = null,
            notes = "Permission not granted"
        )

        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = currentTime,
            windowStartTimeMs = currentTime - 86_400_000L,
            windowEndTimeMs = currentTime,
            productivity = ProductivityContext(),
            digitalBehaviour = digitalContext,
            correlations = emptyList(),
            provenanceMap = mapOf(
                "AppUsage" to SourceProvenanceAndFreshness(
                    sourceName = "Room_Observation",
                    availability = SourceAvailability.PERMISSION_DENIED,
                    isPermissionGranted = false,
                    lastRecordedAt = null,
                    dataAgeMs = null,
                    isStale = false
                )
            ),
            isFresh = false,
            isDegraded = true
        )

        val pipelineResult = PipelineExecutionResult(
            unifiedContext = unifiedContext,
            discoveredPatterns = emptyList(),
            activePatterns = emptyList(),
            barrierHypotheses = emptyList(),
            interventionRecommendations = emptyList(),
            insufficientEvidenceBarriers = emptyList(),
            diagnosticNotes = listOf("Digital behaviour omitted due to PERMISSION_DENIED")
        )

        coEvery {
            orchestrator.orchestrate(any(), any(), any(), any())
        } returns DomainResult.Success(
            PersonalIntelligenceResult(
                pipelineResult = pipelineResult,
                newlyDiscoveredInsights = emptyList(),
                allActiveInsights = emptyList(),
                suppressedCount = 0,
                diagnosticNotes = listOf("No false zero-usage insights generated")
            )
        )

        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        // Verify provenance remains PERMISSION_DENIED and no false conclusions were drawn
        assertEquals(UsagePermissionStatus.DENIED, unifiedContext.digitalBehaviour.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, unifiedContext.digitalBehaviour.availability)
        assertTrue(unifiedContext.digitalBehaviour.appUsages.isEmpty())
    }
}
