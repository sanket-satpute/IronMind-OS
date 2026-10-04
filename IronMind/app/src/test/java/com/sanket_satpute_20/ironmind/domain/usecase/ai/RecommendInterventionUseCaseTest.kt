package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.ContextCommitment
import com.sanket_satpute_20.ironmind.domain.model.intervention.ContextGoal
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.RecommendationContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendInterventionUseCaseTest {

    private class FakeIronMindAI(
        var outputToReturn: Result<AIOutput, Exception>
    ) : IronMindAI {
        var callCount = 0
        var lastRequest: AIRequest? = null

        override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
            callCount++
            lastRequest = request
            return outputToReturn
        }
    }

    private class FakeClock(var currentTime: Long = 1000L) : Clock {
        override fun currentTimeMillis(): Long = currentTime
    }

    private class FakeIdGenerator : IdGenerator {
        var count = 0
        override fun generateId(): String {
            count++
            return "ir_$count"
        }
    }

    private fun validRecommendation(
        confidence: Float = 0.8f,
        type: InterventionType = InterventionType.BREAK_DOWN,
        targetId: String? = "goal-1",
        targetType: String? = "GOAL",
        recommendation: String = "Break this into a 15-minute chunk",
        reason: String = "Task is large and hasn't been started"
    ) = AIOutput.InterventionRecommendation(
        interventionType = type,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
        recommendation = recommendation,
        reason = reason,
        supportingContext = "User postponed 3 times",
        targetEntityId = targetId,
        targetEntityType = targetType,
        confidence = confidence,
        schemaVersion = 2
    )

    private val defaultContext = RecommendationContext(
        userId = "user-1",
        activeGoals = listOf(ContextGoal("goal-1", "Goal 1", "", GoalStatus.ACTIVE, null)),
        activeCommitments = listOf(ContextCommitment("comm-1", "Comm 1", "", CommitmentStatus.PLANNED, null, null)),
        recentObservations = emptyList(),
        activePatterns = emptyList(),
        activeBarriers = emptyList(),
        recentReflections = listOf(com.sanket_satpute_20.ironmind.domain.model.intervention.ContextReflection("refl-1", "Reflect", 0L))
    )

    @Test
    fun `1 valid GOAL target resolves successfully`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "goal-1", targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertTrue(data is InterventionRecommendationResult.Recommended)
        val recommendation = (data as InterventionRecommendationResult.Recommended).recommendation
        assertEquals("GOAL", recommendation.targetEntityType)
        assertEquals("goal-1", recommendation.targetEntityId)
    }

    @Test
    fun `2 valid COMMITMENT target resolves successfully`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "comm-1", targetType = "COMMITMENT")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    @Test
    fun `3 valid REFLECTION target resolves successfully`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "refl-1", targetType = "REFLECTION")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    @Test
    fun `4 GOAL type + commitment ID returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "comm-1", targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `5 COMMITMENT type + goal ID returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "goal-1", targetType = "COMMITMENT")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `6 REFLECTION type + goal ID returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "goal-1", targetType = "REFLECTION")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `7 TASK target returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "task-1", targetType = "TASK")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `8 unknown target type returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "goal-1", targetType = "UNKNOWN_TYPE")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `9 target ID without target type returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "goal-1", targetType = null)))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `10 target type without target ID returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = null, targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `11 PROTECT without target ID returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(type = InterventionType.PROTECT, targetId = null, targetType = null)))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `12 PROTECT without target type returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(type = InterventionType.PROTECT, targetId = "goal-1", targetType = null)))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `13 PROTECT with invalid target returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(type = InterventionType.PROTECT, targetId = "invalid-id", targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `14 PROTECT with valid target returns recommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(type = InterventionType.PROTECT, targetId = "goal-1", targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    @Test
    fun `15 Pattern ID cannot resolve as target`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "pattern-1", targetType = "PATTERN")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `16 Barrier ID cannot resolve as target`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "barrier-1", targetType = "BARRIER")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `17 Observation ID cannot resolve as target`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(targetId = "obs-1", targetType = "OBSERVATION")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `18 STAY_SILENT returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(type = InterventionType.STAY_SILENT, targetId = null, targetType = null)))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `19 AI NoAction returns NoRecommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(AIOutput.NoAction()))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    @Test
    fun `20 confidence below threshold can still produce a recommendation`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(confidence = 0.1f, targetId = "goal-1", targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    @Test
    fun `21 provider failure preserves Result Failure`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Failure(Exception("AI unavailable")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertTrue(result is Result.Failure)
        assertEquals("AI unavailable", (result as Result.Failure).error.message)
    }

    @Test
    fun `22 invalid AI output preserves NoRecommendation semantics`() = runBlocking {
        val fakeAi = FakeIronMindAI(Result.Success(validRecommendation(recommendation = "   ", targetId = "goal-1", targetType = "GOAL")))
        val useCase = RecommendInterventionUseCase(fakeAi, FakeClock(), FakeIdGenerator())
        val result = useCase(defaultContext)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }
}
