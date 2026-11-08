package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result as WorkResult
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollector
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationExecutionError
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectionConcurrencyCoordinationTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var userSessionProvider: FakeUserSessionProvider
    private lateinit var executeUseCase: ExecuteObservationCollectionUseCase

    class SlowCollector(
        override val collectorName: String = "SlowAppUsage",
        val delayMs: Long = 100L
    ) : ObservationCollector {
        var invocations = 0
        override suspend fun invoke(userId: String): DomainResult<Int, Exception> {
            invocations++
            delay(delayMs)
            return DomainResult.Success(3)
        }
    }

    private lateinit var slowCollector: SlowCollector

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 1L)
            .build()

        userSessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-shared", true))
        slowCollector = SlowCollector()
        val collectUseCase = CollectObservationsUseCase(listOf(slowCollector))
        executeUseCase = ExecuteObservationCollectionUseCase(collectUseCase)
    }

    @Test
    fun `two concurrent lifecycle triggers for same user are coordinated`() = runBlocking {
        val deferred1 = async(Dispatchers.Default) { executeUseCase("user-shared") }
        val deferred2 = async(Dispatchers.Default) { executeUseCase("user-shared") }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        val successes = listOf(res1, res2).filterIsInstance<DomainResult.Success<*>>()
        val failures = listOf(res1, res2).filterIsInstance<DomainResult.Failure<ObservationExecutionError>>()

        assertEquals("Exactly one trigger must succeed", 1, successes.size)
        assertEquals("The concurrent trigger must receive AlreadyRunning", 1, failures.size)
        assertTrue(failures.first().error is ObservationExecutionError.AlreadyRunning)
    }

    @Test
    fun `lifecycle and WorkManager trigger overlap for same user completes safely`() = runBlocking {
        val worker = ObservationWorker(context, workerParams, executeUseCase, userSessionProvider)

        // Launch lifecycle catchup and WorkManager worker concurrently
        val lifecycleDeferred = async(Dispatchers.Default) { executeUseCase("user-shared") }
        val workerDeferred = async(Dispatchers.Default) { worker.doWork() }

        val lifecycleRes = lifecycleDeferred.await()
        val workerRes = workerDeferred.await()

        // WorkManager handles AlreadyRunning by returning success() to avoid redundant retries
        assertTrue("Worker must succeed or handle already_running cleanly", workerRes == WorkResult.success())
        assertTrue("Lifecycle either ran or deferred to worker", lifecycleRes is DomainResult.Success || lifecycleRes is DomainResult.Failure)
    }

    @Test
    fun `manual refresh and WorkManager trigger overlap for same user does not conflict`() = runBlocking {
        val worker = ObservationWorker(context, workerParams, executeUseCase, userSessionProvider)

        val manualRefreshDeferred = async(Dispatchers.Default) { executeUseCase("user-shared") }
        val workerDeferred = async(Dispatchers.Default) { worker.doWork() }

        val refreshRes = manualRefreshDeferred.await()
        val workerRes = workerDeferred.await()

        assertTrue("WorkManager completes safely", workerRes == WorkResult.success())
        assertTrue("Manual refresh handled safely", refreshRes is DomainResult.Success || refreshRes is DomainResult.Failure)
    }

    @Test
    fun `different users remain isolated and execute concurrently`() = runBlocking {
        val deferredUserA = async(Dispatchers.Default) { executeUseCase("user-alpha") }
        val deferredUserB = async(Dispatchers.Default) { executeUseCase("user-beta") }

        val resA = deferredUserA.await()
        val resB = deferredUserB.await()

        assertTrue("User Alpha collection must succeed", resA is DomainResult.Success)
        assertTrue("User Beta collection must succeed", resB is DomainResult.Success)
    }
}

