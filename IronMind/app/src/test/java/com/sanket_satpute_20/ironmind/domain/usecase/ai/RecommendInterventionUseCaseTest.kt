package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendInterventionUseCaseTest {

    private class FakeIronMindAI(
        var outputToReturn: Result<AIOutput, Exception>
    ) : IronMindAI {
        var callCount = 0

        override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
            callCount++
            return outputToReturn
        }
    }

    private fun validRecommendation(
        confidence: Float = 0.8f,
        type: InterventionType = InterventionType.BREAK_DOWN
    ) = AIOutput.InterventionRecommendation(
        interventionType = type,
        recommendation = "Break this into a 15-minute chunk",
        reason = "Task is large and hasn't been started",
        supportingContext = "User postponed 3 times",
        targetEntityId = "goal-1",
        confidence = confidence,
        schemaVersion = 1
    )

    @Test
    fun `invoke returns InterventionRecommendation when AI returns valid output with sufficient confidence`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation()))
        val useCase = RecommendInterventionUseCase(fakeAi)

        val result = useCase("User has been delaying this task.", "user-1")

        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertEquals(InterventionType.BREAK_DOWN, output.interventionType)
        assertEquals("Break this into a 15-minute chunk", output.recommendation)
        assertEquals("User postponed 3 times", output.supportingContext)
    }

    @Test
    fun `invoke handles STAY_SILENT type correctly`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(type = InterventionType.STAY_SILENT)))
        val useCase = RecommendInterventionUseCase(fakeAi)

        val result = useCase("Everything is fine.", "user-1")

        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertEquals(InterventionType.STAY_SILENT, output.interventionType)
    }

    @Test
    fun `invoke returns failure when AI confidence is too low`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(confidence = 0.4f)))
        val useCase = RecommendInterventionUseCase(fakeAi)

        val result = useCase("Some context", "user-1")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("confidence too low") == true)
    }

    @Test
    fun `invoke returns failure when AI returns unexpected output type`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(
            AIOutput.Summary(summaryText = "Just a summary", confidence = 0.9f)
        ))
        val useCase = RecommendInterventionUseCase(fakeAi)

        val result = useCase("Some context", "user-1")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("unexpected output type") == true)
    }

    @Test
    fun `invoke returns failure when AI processing fails`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Failure(Exception("AI unavailable")))
        val useCase = RecommendInterventionUseCase(fakeAi)

        val result = useCase("Some context", "user-1")

        assertTrue(result is Result.Failure)
        assertEquals("AI unavailable", (result as Result.Failure).error.message)
    }

    @Test
    fun `invoke does not call any repository - only calls AI once`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation()))
        val useCase = RecommendInterventionUseCase(fakeAi)

        useCase("Some context", "user-1")

        assertEquals(1, fakeAi.callCount)
    }
}
