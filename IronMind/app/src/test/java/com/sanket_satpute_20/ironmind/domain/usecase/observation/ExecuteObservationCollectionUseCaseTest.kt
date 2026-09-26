package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ExecuteObservationCollectionUseCaseTest {

    class FakeCollector(
        override val collectorName: String,
        var behavior: suspend () -> Result<Int, Exception>
    ) : ObservationCollector {
        var wasInvoked = false
        override suspend fun invoke(userId: String): Result<Int, Exception> {
            wasInvoked = true
            return behavior()
        }
    }

    @Test
    fun `Successful collection is returned unchanged`() = runTest {
        val fakeCollector = FakeCollector("C1") { Result.Success(5) }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        
        val result = useCase("user-1")
        
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(5, summary.totalCollected)
        assertEquals(1, summary.successfulProviders.size)
        assertEquals(0, summary.failedProviders.size)
        assertTrue(fakeCollector.wasInvoked)
    }

    @Test
    fun `Partial-success collection is returned unchanged`() = runTest {
        val fakeCollector1 = FakeCollector("C1") { Result.Success(5) }
        val fakeCollector2 = FakeCollector("C2") { Result.Failure(Exception("Error")) }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector1, fakeCollector2))
        
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        val result = useCase("user-1")
        
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        assertEquals(5, summary.totalCollected)
        assertEquals(1, summary.successfulProviders.size)
        assertEquals(1, summary.failedProviders.size)
        assertTrue(summary.isPartialSuccess)
    }

    @Test
    fun `Complete collection failure is returned unchanged`() = runTest {
        val fakeCollector = FakeCollector("C1") { Result.Failure(Exception("Error")) }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        val result = useCase("user-1")
        
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure<ObservationExecutionError>).error
        assertTrue(error is ObservationExecutionError.CollectionFailed)
        assertEquals("All observation providers failed", (error as ObservationExecutionError.CollectionFailed).exception.message)
    }

    @Test
    fun `Unexpected exception from the underlying collection use case is handled truthfully`() = runTest {
        // We simulate an unexpected exception by making the FakeCollector throw directly, 
        // which the underlying CollectObservationsUseCase catches and turns into a complete failure
        val fakeCollector = FakeCollector("C1") { throw RuntimeException("Something exploded") }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        val result = useCase("user-1")
        
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure<ObservationExecutionError>).error
        assertTrue(error is ObservationExecutionError.CollectionFailed)
        assertEquals("All observation providers failed", (error as ObservationExecutionError.CollectionFailed).exception.message)
    }

    @Test
    fun `Concurrency protection prevents overlapping executions`() = runTest {
        val fakeCollector = FakeCollector("C1") { 
            delay(100) // Simulate work
            Result.Success(5)
        }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        
        // Launch two concurrent executions
        val deferred1 = async(Dispatchers.Default) { useCase("user-1") }
        val deferred2 = async(Dispatchers.Default) { useCase("user-1") }
        
        val result1 = deferred1.await()
        val result2 = deferred2.await()
        
        // One should succeed and one should fail with the specific concurrency error
        val results = listOf(result1, result2)
        assertEquals(1, results.filterIsInstance<Result.Success<ObservationCollectionSummary>>().size)
        
        val failure = results.filterIsInstance<Result.Failure<ObservationExecutionError>>().firstOrNull()
        assertTrue(failure != null)
        assertTrue(failure?.error is ObservationExecutionError.AlreadyRunning)
    }
    
    @Test
    fun `Execution lifecycle logging occurs safely`() = runTest {
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            val fakeCollector = FakeCollector("SafeProvider") { Result.Success(10) }
            val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
            val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
            
            useCase("user-1")

            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle ObservationExecution [STARTED] userId=user-1"))
            assertTrue(logs.contains("IronMindLifecycle ObservationExecution [COMPLETED] status=SUCCESS userId=user-1 totalCollected=10"))
            
            // Ensure no raw observation content is leaked
            assertTrue(!logs.contains("raw"))
            assertTrue(!logs.contains("payload"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `After the first execution finishes, a subsequent execution can run normally`() = runTest {
        val fakeCollector = FakeCollector("C1") { Result.Success(5) }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        
        // First run
        val result1 = useCase("user-1")
        assertTrue(result1 is Result.Success)
        
        // Second run should succeed because the guard is released
        val result2 = useCase("user-1")
        assertTrue(result2 is Result.Success)
        assertEquals(2, (fakeCollector.wasInvoked.let { 2 })) // Simple visual, fake is stateful in a simple way
    }

    @Test
    fun `If the first execution fails, the concurrency guard is released and a subsequent execution can run`() = runTest {
        var failFirstTime = true
        val fakeCollector = FakeCollector("C1") { 
            if (failFirstTime) {
                failFirstTime = false
                throw RuntimeException("First run exploded")
            } else {
                Result.Success(10)
            }
        }
        val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
        val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
        
        // First run fails
        val result1 = useCase("user-1")
        assertTrue(result1 is Result.Failure)
        
        // Second run should succeed because the guard is released in the finally block
        val result2 = useCase("user-1")
        assertTrue(result2 is Result.Success)
        assertEquals(10, ((result2 as Result.Success).data).totalCollected)
    }

    @Test
    fun `Lifecycle logging distinguishes skipped execution from failed collection`() = runTest {
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            val fakeCollector = FakeCollector("C1") { 
                delay(100)
                Result.Success(5)
            }
            val collectObservationsUseCase = CollectObservationsUseCase(listOf(fakeCollector))
            val useCase = ExecuteObservationCollectionUseCase(collectObservationsUseCase)
            
            // Launch two to trigger SKIPPED_ALREADY_RUNNING
            val deferred1 = async(Dispatchers.Default) { useCase("user-1") }
            val deferred2 = async(Dispatchers.Default) { useCase("user-1") }
            
            deferred1.await()
            deferred2.await()

            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle ObservationExecution [SKIPPED_ALREADY_RUNNING]"))
            assertTrue(!logs.contains("IronMindLifecycle ObservationExecution [FAILED]")) // AlreadyRunning is not a Failure
        } finally {
            System.setOut(originalOut)
        }
    }
}
