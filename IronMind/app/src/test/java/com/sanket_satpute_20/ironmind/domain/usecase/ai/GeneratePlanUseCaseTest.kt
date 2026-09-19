package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.TaskCandidate
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePlanUseCaseTest {

    private class FakeIronMindAI(
        var outputToReturn: Result<AIOutput, Exception>
    ) : IronMindAI {
        var callCount = 0

        override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
            callCount++
            return outputToReturn
        }
    }

    private fun validPlanOutput(confidence: Float = 0.8f, tasks: List<TaskCandidate> = listOf(
        TaskCandidate(title = "Research topic", description = "Look up sources", isNextAction = true),
        TaskCandidate(title = "Write outline", description = "Draft sections", estimatedDurationMinutes = 60)
    )) = AIOutput.PlanOutput(
        goalId = "goal-1",
        proposedTasks = tasks,
        confidence = confidence,
        reasoning = "User needs to finish proposal",
        schemaVersion = 1
    )

    @Test
    fun `invoke returns PlanOutput when AI returns valid plan with sufficient confidence`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validPlanOutput()))
        val useCase = GeneratePlanUseCase(fakeAi)

        val result = useCase("goal-1", "Finish business proposal", "user-1")

        assertTrue(result is Result.Success)
        val plan = (result as Result.Success).data
        assertEquals("goal-1", plan.goalId)
        assertEquals(2, plan.proposedTasks.size)
        assertTrue(plan.proposedTasks.any { it.isNextAction })
    }

    @Test
    fun `invoke returns failure when AI plan confidence is too low`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validPlanOutput(confidence = 0.3f)))
        val useCase = GeneratePlanUseCase(fakeAi)

        val result = useCase("goal-1", "Finish proposal", "user-1")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("confidence too low") == true)
    }

    @Test
    fun `invoke returns failure when AI plan has no proposed tasks`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validPlanOutput(tasks = emptyList())))
        val useCase = GeneratePlanUseCase(fakeAi)

        val result = useCase("goal-1", "Finish proposal", "user-1")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("no tasks proposed") == true)
    }

    @Test
    fun `invoke returns failure when AI returns unexpected output type`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(
            AIOutput.NoAction(reason = "Could not process", confidence = 1.0f, schemaVersion = 1)
        ))
        val useCase = GeneratePlanUseCase(fakeAi)

        val result = useCase("goal-1", "Finish proposal", "user-1")

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("unexpected output type") == true)
    }

    @Test
    fun `invoke returns failure when AI processing fails`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Failure(Exception("Timeout")))
        val useCase = GeneratePlanUseCase(fakeAi)

        val result = useCase("goal-1", "Finish proposal", "user-1")

        assertTrue(result is Result.Failure)
        assertEquals("Timeout", (result as Result.Failure).error.message)
    }

    @Test
    fun `invoke does not call any repository - only calls AI`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validPlanOutput()))
        val useCase = GeneratePlanUseCase(fakeAi)

        useCase("goal-1", "Finish proposal", "user-1")

        // If no repository was injected and no exception occurred, domain mutation cannot happen
        assertEquals(1, fakeAi.callCount)
    }

    @Test
    fun `invoke passes context hints correctly in AI input`() = runBlocking {
        var capturedRequest: AIRequest? = null
        val fakeAi = object : IronMindAI {
            override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
                capturedRequest = request
                return Result.Success(validPlanOutput())
            }
        }
        val useCase = GeneratePlanUseCase(fakeAi)

        useCase(
            goalId = "goal-1",
            goalDescription = "Finish proposal",
            userId = "user-1",
            contextHints = listOf("Has 2 meetings this week", "Deadline is Friday")
        )

        assertTrue(capturedRequest?.input?.contains("Has 2 meetings this week") == true)
        assertTrue(capturedRequest?.input?.contains("Deadline is Friday") == true)
    }
}
