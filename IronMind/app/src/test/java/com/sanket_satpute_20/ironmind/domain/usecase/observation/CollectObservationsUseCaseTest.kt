package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CollectObservationsUseCaseTest {

    class FakeCollector(
        override val collectorName: String,
        var behavior: () -> Result<Int, Exception>
    ) : ObservationCollector {
        var wasInvoked = false
        override suspend fun invoke(userId: String): Result<Int, Exception> {
            wasInvoked = true
            return behavior()
        }
    }

    @Test
    fun `All eligible providers succeed`() = runTest {
        val collector1 = FakeCollector("C1") { Result.Success(2) }
        val collector2 = FakeCollector("C2") { Result.Success(3) }
        val useCase = CollectObservationsUseCase(listOf(collector1, collector2))

        val result = useCase("user-1")
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        
        assertEquals(5, summary.totalCollected)
        assertEquals(2, summary.successfulProviders.size)
        assertEquals(0, summary.failedProviders.size)
        assertTrue(collector1.wasInvoked)
        assertTrue(collector2.wasInvoked)
    }

    @Test
    fun `One provider fails while the others succeed`() = runTest {
        val collector1 = FakeCollector("C1") { Result.Failure(Exception("Error 1")) }
        val collector2 = FakeCollector("C2") { Result.Success(3) }
        val collector3 = FakeCollector("C3") { Result.Success(1) }
        val useCase = CollectObservationsUseCase(listOf(collector1, collector2, collector3))

        val result = useCase("user-1")
        assertTrue(result is Result.Success) // Still success overall
        val summary = (result as Result.Success).data
        
        assertEquals(4, summary.totalCollected)
        assertEquals(2, summary.successfulProviders.size)
        assertEquals(1, summary.failedProviders.size)
        assertTrue(summary.failedProviders.contains("C1"))
        
        // Ensure isolation (collector2 and collector3 were invoked despite collector1 failing)
        assertTrue(collector1.wasInvoked)
        assertTrue(collector2.wasInvoked)
        assertTrue(collector3.wasInvoked)
    }

    @Test
    fun `Multiple providers fail`() = runTest {
        val collector1 = FakeCollector("C1") { Result.Failure(Exception("Error 1")) }
        val collector2 = FakeCollector("C2") { Result.Failure(Exception("Error 2")) }
        val useCase = CollectObservationsUseCase(listOf(collector1, collector2))

        val result = useCase("user-1")
        // If all fail, it should return Failure
        assertTrue(result is Result.Failure)
        assertEquals("All observation providers failed", (result as Result.Failure).error.message)
    }

    @Test
    fun `Collection is not permitted - returns 0 as valid success`() = runTest {
        // Individual collectors handle permission by silently returning 0
        val collector1 = FakeCollector("AppUsage") { Result.Success(0) }
        val useCase = CollectObservationsUseCase(listOf(collector1))

        val result = useCase("user-1")
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        
        assertEquals(0, summary.totalCollected)
        assertEquals(1, summary.successfulProviders.size)
        assertTrue(collector1.wasInvoked)
    }

    @Test
    fun `A provider returns zero observations is treated as valid`() = runTest {
        val collector1 = FakeCollector("C1") { Result.Success(0) }
        val collector2 = FakeCollector("C2") { Result.Success(5) }
        val useCase = CollectObservationsUseCase(listOf(collector1, collector2))

        val result = useCase("user-1")
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        
        assertEquals(5, summary.totalCollected)
        assertEquals(2, summary.successfulProviders.size) // C1 is successful
        assertEquals(0, summary.failedProviders.size)
    }
    
    @Test
    fun `Verify lifecycle logging does not contain raw observation content`() = runTest {
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            val collector1 = FakeCollector("SecretProvider") { Result.Success(10) }
            val useCase = CollectObservationsUseCase(listOf(collector1))

            useCase("user-1")

            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle Observation [COLLECTION_STARTED]"))
            assertTrue(logs.contains("IronMindLifecycle Observation [PROVIDER_COMPLETED]"))
            assertTrue(logs.contains("provider=SecretProvider"))
            assertTrue(logs.contains("count=10"))
            
            // Ensure no raw observation content leaked into this orchestration log
            assertTrue(!logs.contains("observationData=")) 
        } finally {
            System.setOut(originalOut)
        }
    }
}
