package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollector
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollectionSummary
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ObservationWorkerIntelligenceTriggerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var fakeCollector: FakeObservationCollector
    private lateinit var executeUseCase: ExecuteObservationCollectionUseCase
    private lateinit var scheduler: IntelligenceOrchestrationScheduler

    private val userId = "user-obs-intel-1"

    class FakeObservationCollector(
        override val collectorName: String = "AppUsage",
        var countToReturn: Int = 5,
        var shouldFail: Boolean = false
    ) : ObservationCollector {
        override suspend fun invoke(userId: String): DomainResult<Int, Exception> {
            return if (shouldFail) {
                DomainResult.Failure(RuntimeException("Provider collection failed"))
            } else {
                DomainResult.Success(countToReturn)
            }
        }
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 1L)
            .build()
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, true))
        fakeCollector = FakeObservationCollector()
        val collectUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        executeUseCase = ExecuteObservationCollectionUseCase(collectUseCase)
        scheduler = mockk(relaxed = true)
    }

    private fun createWorker(
        customScheduler: IntelligenceOrchestrationScheduler? = scheduler
    ): ObservationWorker {
        return ObservationWorker(
            appContext = context,
            workerParams = workerParams,
            executeObservationCollectionUseCase = executeUseCase,
            userSessionProvider = sessionProvider,
            intelligenceOrchestrationScheduler = customScheduler
        )
    }

    @Test
    fun `successful observation collection with new data schedules intelligence orchestration`() = runBlocking {
        fakeCollector.countToReturn = 8 // 8 new observations inserted
        fakeCollector.shouldFail = false

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)

        // Verifies Requirement 1: Successful observation collection schedules intelligence work
        verify(exactly = 1) {
            scheduler.scheduleOrchestration(
                userId = userId,
                generation = 1L
            )
        }
    }

    @Test
    fun `observation collection with no new data (totalCollected == 0) does NOT schedule intelligence orchestration`() = runBlocking {
        fakeCollector.countToReturn = 0 // Checkpoint up-to-date, 0 new observations
        fakeCollector.shouldFail = false

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)

        // Verifies Requirement 3: No unnecessary scheduling occurs when no new relevant data exists
        verify(exactly = 0) {
            scheduler.scheduleOrchestration(any(), any(), any())
        }
    }

    @Test
    fun `collection failure does not schedule intelligence orchestration`() = runBlocking {
        fakeCollector.shouldFail = true

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.failure(), result)

        verify(exactly = 0) {
            scheduler.scheduleOrchestration(any(), any(), any())
        }
    }

    @Test
    fun `null scheduler does not crash observation worker`() = runBlocking {
        fakeCollector.countToReturn = 5

        val worker = createWorker(customScheduler = null)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
    }
}
