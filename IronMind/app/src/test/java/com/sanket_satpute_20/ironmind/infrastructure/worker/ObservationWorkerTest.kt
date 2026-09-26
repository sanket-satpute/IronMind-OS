package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollector
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollectionSummary
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationExecutionError
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ObservationWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    
    // Fakes
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var fakeCollector: FakeObservationCollector
    private lateinit var executeUseCase: ExecuteObservationCollectionUseCase
    private lateinit var worker: ObservationWorker

    class FakeAuthRepository(var user: AuthUser?) : AuthRepository {
        override val authStatus: Flow<AuthStatus> = flowOf()
        override val currentUser: Flow<AuthUser?> = flowOf(user)
        override suspend fun signInAnonymously(): DomainResult<AuthUser, Exception> = DomainResult.Failure(Exception())
        override suspend fun signOut(): DomainResult<Unit, Exception> = DomainResult.Failure(Exception())
        override fun getCurrentUser(): AuthUser? = user
    }

    class FakeObservationCollector(
        override val collectorName: String = "FakeCollector",
        var behavior: suspend (String) -> DomainResult<Int, Exception> = { DomainResult.Success(5) }
    ) : ObservationCollector {
        var invokedUserId: String? = null
        override suspend fun invoke(userId: String): DomainResult<Int, Exception> {
            invokedUserId = userId
            return behavior(userId)
        }
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        authRepository = FakeAuthRepository(AuthUser("user-1", true))
        fakeCollector = FakeObservationCollector()
        val collectUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        executeUseCase = ExecuteObservationCollectionUseCase(collectUseCase)
        
        worker = ObservationWorker(context, workerParams, executeUseCase, authRepository)
    }

    @Test
    fun `Worker delegates to ExecuteObservationCollectionUseCase with authoritative user ID`() = runBlocking {
        val result = worker.doWork()
        
        assertEquals(Result.success(), result)
        assertEquals("user-1", fakeCollector.invokedUserId)
    }

    @Test
    fun `Missing user context is handled truthfully and fails`() = runBlocking {
        authRepository.user = null // No user
        
        val result = worker.doWork()
        
        assertEquals(Result.failure(), result)
        assertEquals(null, fakeCollector.invokedUserId) // Didn't invoke execution
    }

    @Test
    fun `Successful execution maps to correct WorkManager result`() = runBlocking {
        fakeCollector.behavior = { DomainResult.Success(5) }
        
        val result = worker.doWork()
        assertEquals(Result.success(), result)
    }

    @Test
    fun `Complete failure maps correctly and does not retry blindly`() = runBlocking {
        fakeCollector.behavior = { DomainResult.Failure(Exception("All failed")) }
        
        val result = worker.doWork()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `Already-running execution maps correctly to success`() = runBlocking {
        fakeCollector.behavior = { 
            delay(1000)
            DomainResult.Success(5)
        }
        
        // Start the execution in background to lock the AtomicBoolean
        val backgroundJob = launch(Dispatchers.Default) {
            executeUseCase("user-1")
        }
        
        delay(50) // let it lock
        
        val result = worker.doWork()
        assertEquals(Result.success(), result) // skipped implies success in this context
        
        backgroundJob.cancel()
    }

    @Test
    fun `Worker lifecycle logging is safe`() = runBlocking {
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            worker.doWork()
            
            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle ObservationWorker [STARTED]"))
            assertTrue(logs.contains("IronMindLifecycle ObservationWorker [COMPLETED]"))
            assertTrue(!logs.contains("raw"))
            assertTrue(!logs.contains("payload"))
        } finally {
            System.setOut(originalOut)
        }
    }
}
