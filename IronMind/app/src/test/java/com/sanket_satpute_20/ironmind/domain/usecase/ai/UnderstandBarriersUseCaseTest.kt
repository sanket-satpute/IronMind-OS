package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCandidate
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandBarriersUseCaseTest {

    private class FakeIronMindAI(
        var outputToReturn: Result<AIOutput, Exception>
    ) : IronMindAI {
        var callCount = 0

        override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
            callCount++
            return outputToReturn
        }
    }

    private fun validBarrierOutput(
        confidence: Float = 0.75f,
        barriers: List<BarrierCandidate> = listOf(
            BarrierCandidate(
                category = BarrierCategory.LACK_OF_CLARITY,
                description = "I've noticed you often pause at the start of this task. Could it be unclear where to begin?"
            ),
            BarrierCandidate(
                category = BarrierCategory.LOW_ENERGY,
                description = "You tend to schedule this in the afternoon. Could energy levels be a factor?"
            )
        )
    ) = AIOutput.BarrierOutput(
        proposedBarriers = barriers,
        confidence = confidence,
        reasoning = "Based on observed patterns",
        schemaVersion = 1
    )

    @Test
    fun `invoke returns BarrierOutput when AI returns valid barriers with sufficient confidence`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validBarrierOutput()))
        val useCase = UnderstandBarriersUseCase(fakeAi)

        val result = useCase("I keep putting off writing the report", "user-1")

        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertEquals(2, output.proposedBarriers.size)
    }


    @Test
    fun `invoke ignores AI confidence and returns success when barriers are present`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validBarrierOutput(confidence = 0.3f)))
        val useCase = UnderstandBarriersUseCase(fakeAi)

        val result = useCase("Some input", "user-1")

        assertTrue(result is Result.Success)
    }

    @Test
    fun `invoke returns success when AI returns empty barriers list`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validBarrierOutput(barriers = emptyList())))
        val useCase = UnderstandBarriersUseCase(fakeAi)

        val result = useCase("Some input", "user-1")

        assertTrue(result is Result.Success)
        val output = (result as Result.Success).data
        assertTrue(output.proposedBarriers.isEmpty())
    }

    @Test
    fun `invoke returns failure when AI returns unexpected output type`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(
            AIOutput.NoAction(reason = "No barrier found", confidence = 1.0f, schemaVersion = 1)
        ))
        val useCase = UnderstandBarriersUseCase(fakeAi)

        val result = useCase("Some input", "user-1")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("unexpected output type") == true)
    }

    @Test
    fun `invoke returns failure when AI processing fails`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Failure(Exception("AI unavailable")))
        val useCase = UnderstandBarriersUseCase(fakeAi)

        val result = useCase("Some input", "user-1")

        assertTrue(result is Result.Failure)
        assertEquals("AI unavailable", (result as Result.Failure).error.message)
    }

    @Test
    fun `invoke does not call any repository - only calls AI once`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validBarrierOutput()))
        val useCase = UnderstandBarriersUseCase(fakeAi)

        useCase("Some input", "user-1")

        assertEquals(1, fakeAi.callCount)
    }

    @Test
    fun `barriers contain all allowed categories from roadmap spec`() {
        // Validate the BarrierCategory enum contains exactly the 10 roadmap-specified categories
        val expected = setOf(
            "UNCERTAINTY", "DISTRACTION", "FEAR", "BOREDOM", "LACK_OF_CLARITY",
            "ENVIRONMENTAL_FRICTION", "LOW_ENERGY", "EXCESSIVE_TASK_SIZE",
            "COMPETING_PRIORITIES", "SCHEDULING_MISMATCH"
        )
        val actual = BarrierCategory.values().map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
