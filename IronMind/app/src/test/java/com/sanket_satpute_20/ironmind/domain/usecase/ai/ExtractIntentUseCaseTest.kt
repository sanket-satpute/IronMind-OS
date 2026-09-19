package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractIntentUseCaseTest {

    private class FakeIronMindAI(
        var outputToReturn: Result<AIOutput, Exception>
    ) : IronMindAI {
        override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
            return outputToReturn
        }
    }

    @Test
    fun `invoke returns success when AI returns valid intent output`() = runBlocking {
        val fakeAi = FakeIronMindAI(
            Result.Success(
                AIOutput.IntentOutput(
                    intentDescription = "Do groceries",
                    confidence = 0.8f,
                    reasoning = "User said they need food",
                    schemaVersion = 1
                )
            )
        )
        val useCase = ExtractIntentUseCase(fakeAi)

        val result = useCase.invoke("I need to buy food", "user1")
        
        assertTrue(result is Result.Success)
        assertEquals("Do groceries", (result as Result.Success).data)
    }

    @Test
    fun `invoke returns failure when AI returns intent with low confidence`() = runBlocking {
        val fakeAi = FakeIronMindAI(
            Result.Success(
                AIOutput.IntentOutput(
                    intentDescription = "Do groceries",
                    confidence = 0.4f, // Below 0.5f threshold
                    reasoning = "Not sure",
                    schemaVersion = 1
                )
            )
        )
        val useCase = ExtractIntentUseCase(fakeAi)

        val result = useCase.invoke("Maybe I need to buy food", "user1")
        
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("confidence too low") == true)
    }

    @Test
    fun `invoke returns failure when AI returns unexpected output type`() = runBlocking {
        val fakeAi = FakeIronMindAI(
            Result.Success(
                AIOutput.NoAction(
                    reason = "I couldn't figure it out",
                    confidence = 1.0f,
                    reasoning = null,
                    schemaVersion = 1
                )
            )
        )
        val useCase = ExtractIntentUseCase(fakeAi)

        val result = useCase.invoke("Blah blah blah", "user1")
        
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("unexpected output type") == true)
    }

    @Test
    fun `invoke returns failure when AI processing fails`() = runBlocking {
        val fakeAi = FakeIronMindAI(
            Result.Failure(Exception("Network error"))
        )
        val useCase = ExtractIntentUseCase(fakeAi)

        val result = useCase.invoke("Buy food", "user1")
        
        assertTrue(result is Result.Failure)
        assertEquals("Network error", (result as Result.Failure).error.message)
    }
}
