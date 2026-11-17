package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.model.SyncState
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.HandleIncomingNotificationUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollector
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutorImpl
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class SessionGenerationWorkerEnforcementTest {

    private lateinit var context: Context
    private lateinit var sessionProvider: FakeUserSessionProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-100", isAnonymous = false))
    }

    private fun createParams(generation: Long?): WorkerParameters {
        val params = mockk<WorkerParameters>(relaxed = true)
        val dataBuilder = Data.Builder()
        if (generation != null) {
            dataBuilder.putLong(UserSessionProvider.KEY_SESSION_GENERATION, generation)
        }
        every { params.inputData } returns dataBuilder.build()
        return params
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Stale generation rejection: Request at Gen 1 executed at Gen 2 fails closed
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `worker created at generation 1 executed at generation 2 is rejected`() = runBlocking {
        // Enqueue at generation 1
        val paramsGen1 = createParams(generation = 1L)
        val syncUseCase = mockk<SyncUseCase>(relaxed = true)

        // Session generation advances to 2
        sessionProvider.setState(SessionState.Authenticated("user-100", isAnonymous = false))
        assertEquals(2L, sessionProvider.getSessionGeneration())

        val worker = SyncWorker(context, paramsGen1, syncUseCase, sessionProvider)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { syncUseCase(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. Missing or invalid generation fails closed
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `missing generation fails closed across all workers`() = runBlocking {
        val missingParams = createParams(generation = null)

        val syncWorker = SyncWorker(context, missingParams, mockk(relaxed = true), sessionProvider)
        val patternWorker = PatternUpdateWorker(context, missingParams, mockk(relaxed = true), sessionProvider)
        val interventionWorker = InterventionEvaluationWorker(context, missingParams, mockk(relaxed = true), sessionProvider)

        assertEquals(Result.failure(), syncWorker.doWork())
        assertEquals(Result.failure(), patternWorker.doWork())
        assertEquals(Result.failure(), interventionWorker.doWork())
    }

    @Test
    fun `invalid negative or zero generation fails closed`() = runBlocking {
        val zeroParams = createParams(generation = 0L)
        val negativeParams = createParams(generation = -1L)

        val syncWorkerZero = SyncWorker(context, zeroParams, mockk(relaxed = true), sessionProvider)
        val syncWorkerNeg = SyncWorker(context, negativeParams, mockk(relaxed = true), sessionProvider)

        assertEquals(Result.failure(), syncWorkerZero.doWork())
        assertEquals(Result.failure(), syncWorkerNeg.doWork())
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. Same UID re-authenticated under new generation cannot accept stale work
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `same UID re-authenticated under new generation rejects stale work`() = runBlocking {
        // Gen 1: user-100
        val staleParams = createParams(generation = 1L)
        val syncUseCase = mockk<SyncUseCase>(relaxed = true)

        // User signs out and re-authenticates as same UID "user-100", but generation is now 2
        sessionProvider.setState(SessionState.Authenticated("user-100", isAnonymous = false))
        assertEquals(2L, sessionProvider.getSessionGeneration())

        val worker = SyncWorker(context, staleParams, syncUseCase, sessionProvider)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { syncUseCase(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. Rejected work performs zero user-scoped writes or uploads
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `rejected work performs no domain operations or uploads`() = runBlocking {
        val staleParams = createParams(generation = 1L)
        sessionProvider.setState(SessionState.Authenticated("user-100", isAnonymous = false))

        val syncUseCase = mockk<SyncUseCase>()
        val patternEngine = mockk<PatternEngine>()
        val pipeline = mockk<InterventionExecutionPipeline>()

        val syncWorker = SyncWorker(context, staleParams, syncUseCase, sessionProvider)
        val patternWorker = PatternUpdateWorker(context, staleParams, patternEngine, sessionProvider)
        val interventionWorker = InterventionEvaluationWorker(context, staleParams, pipeline, sessionProvider)

        assertEquals(Result.failure(), syncWorker.doWork())
        assertEquals(Result.failure(), patternWorker.doWork())
        assertEquals(Result.failure(), interventionWorker.doWork())

        coVerify(exactly = 0) { syncUseCase(any()) }
        coVerify(exactly = 0) { patternEngine.decayStalePatterns(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. Valid current-generation work succeeds
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `valid current-generation work succeeds normally`() = runBlocking {
        val validParams = createParams(generation = 1L)
        val syncUseCase = mockk<SyncUseCase> {
            coEvery { this@mockk.invoke("user-100") } returns DomainResult.Success(
                SyncState(isIdle = true, isSyncing = false, lastSyncedAt = null, lastError = null, pendingCount = 0)
            )
        }

        val worker = SyncWorker(context, validParams, syncUseCase, sessionProvider)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { syncUseCase("user-100") }
    }

    @Test
    fun `ObservationWorker succeeds on valid current generation and fails on stale generation`() = runBlocking {
        var observedUserId: String? = null
        val collector = object : ObservationCollector {
            override val collectorName: String = "TestCollector"
            override suspend fun invoke(userId: String): DomainResult<Int, Exception> {
                observedUserId = userId
                return DomainResult.Success(1)
            }
        }
        val executeUseCase = ExecuteObservationCollectionUseCase(CollectObservationsUseCase(listOf(collector)))

        // Case A: Stale generation 1 running against current generation 2 fails closed
        sessionProvider.setState(SessionState.Authenticated("user-100", isAnonymous = false))
        val staleWorker = ObservationWorker(context, createParams(generation = 1L), executeUseCase, sessionProvider)
        assertEquals(Result.failure(), staleWorker.doWork())
        assertEquals(null, observedUserId)

        // Case B: Current generation 2 succeeds
        val currentWorker = ObservationWorker(context, createParams(generation = 2L), executeUseCase, sessionProvider)
        assertEquals(Result.success(), currentWorker.doWork())
        assertEquals("user-100", observedUserId)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. Periodic work rescheduling uses UPDATE and carries active generation
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `periodic work scheduling uses UPDATE policy and carries current generation`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        val requestSlot = slot<PeriodicWorkRequest>()

        every {
            workManager.enqueueUniquePeriodicWork(
                EventProcessingWorker.RECONCILIATION_WORK_NAME,
                capture(policySlot),
                capture(requestSlot)
            )
        } returns mockk()

        val backgroundExecutor = BackgroundExecutorImpl(
            context = context,
            workManager = workManager,
            sessionProvider = { sessionProvider }
        )

        backgroundExecutor.scheduleEventReconciliation()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(EventProcessingWorker.RECONCILIATION_WORK_NAME, any(), any())
        }
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policySlot.captured)
        val enqueuedGen = requestSlot.captured.workSpec.input.getLong(UserSessionProvider.KEY_SESSION_GENERATION, -1L)
        assertEquals(1L, enqueuedGen)

        // When generation increments to 2, rescheduling updates inputData to generation 2
        sessionProvider.setState(SessionState.Authenticated("user-100", isAnonymous = false))
        backgroundExecutor.scheduleEventReconciliation()

        val updatedGen = requestSlot.captured.workSpec.input.getLong(UserSessionProvider.KEY_SESSION_GENERATION, -1L)
        assertEquals(2L, updatedGen)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 7. Notification listener generation isolation
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `notification event bound to stale generation is safely dropped`() = runBlocking {
        val handleUseCase = mockk<HandleIncomingNotificationUseCase>(relaxed = true)

        // Capture generation at event arrival time (gen 1)
        val eventGeneration = sessionProvider.getSessionGeneration()
        assertEquals(1L, eventGeneration)

        // Session advances to generation 2 before event coroutine executes
        sessionProvider.setState(SessionState.Authenticated("user-100", isAnonymous = false))
        assertEquals(2L, sessionProvider.getSessionGeneration())

        // Coroutine execution under eventGeneration 1
        var droppedSafely = false
        try {
            sessionProvider.withUserSession(expectedGeneration = eventGeneration) { activeUserId ->
                handleUseCase(
                    userId = activeUserId,
                    packageName = "com.test.app",
                    isClearable = true,
                    timestamp = 1000L
                )
            }
        } catch (e: IllegalStateException) {
            droppedSafely = true
        }

        assertTrue("Stale notification event must be dropped via IllegalStateException", droppedSafely)
        coVerify(exactly = 0) { handleUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `notification event with active generation executes under authoritative user`() = runBlocking {
        val handleUseCase = mockk<HandleIncomingNotificationUseCase>(relaxed = true)

        val eventGeneration = sessionProvider.getSessionGeneration()
        assertEquals(1L, eventGeneration)

        sessionProvider.withUserSession(expectedGeneration = eventGeneration) { activeUserId ->
            handleUseCase(
                userId = activeUserId,
                packageName = "com.test.app",
                isClearable = true,
                timestamp = 1000L
            )
        }

        coVerify(exactly = 1) {
            handleUseCase(
                userId = "user-100",
                packageName = "com.test.app",
                isClearable = true,
                timestamp = 1000L
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 8. Erasure barrier integrity preserved
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `worker fails closed when user has been erased even if generation number matches`() = runBlocking {
        val params = createParams(generation = 1L)
        sessionProvider.markUserErased("user-100", 1L)

        val syncUseCase = mockk<SyncUseCase>(relaxed = true)
        val worker = SyncWorker(context, params, syncUseCase, sessionProvider)

        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { syncUseCase(any()) }
    }
}
