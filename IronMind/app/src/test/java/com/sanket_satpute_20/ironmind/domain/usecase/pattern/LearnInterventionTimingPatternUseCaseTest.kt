package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionChannel
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnInterventionTimingPatternUseCaseTest {

    private val interventionRepository = mockk<InterventionRepository>()
    private val patternRepository = mockk<PatternRepository>()
    private val clock = object : Clock {
        override fun currentTimeMillis(): Long = 10000L
    }
    private val idGenerator = object : IdGenerator {
        override fun generateId(): String = "test-timing-pattern-id"
    }

    private fun createRecord(
        id: String,
        resolutionReason: InterventionResolutionReason?,
        state: InterventionState = InterventionState.OUTCOME
    ) = InterventionRecord(
        id = id,
        userId = "user-1",
        type = InterventionType.REMIND,
        state = state,
        title = "Test Timing Intervention $id",
        description = "Test description",
        channel = InterventionChannel.IN_APP,
        resolutionReason = resolutionReason,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `excludes technical failures and pending accepted records from timing AI prompt`() = runBlocking {
        val techFailure = createRecord("rec-timing-tech", InterventionResolutionReason.TECHNICAL_FAILURE, InterventionState.FAILED)
        val pendingAccept = createRecord("rec-timing-pending", null, InterventionState.RESPONSE)
        val behavioralFailure = createRecord("rec-timing-behav", InterventionResolutionReason.BEHAVIORAL_FAILURE, InterventionState.FAILED)
        val successRecord = createRecord("rec-timing-success", InterventionResolutionReason.SUCCESS, InterventionState.OUTCOME)

        coEvery { interventionRepository.getRecentInterventions("user-1", 0L) } returns Result.Success(
            listOf(techFailure, pendingAccept, behavioralFailure, successRecord)
        )

        var capturedPrompt = ""
        val mockAI = object : IronMindAI {
            override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
                capturedPrompt = request.input
                return Result.Success(AIOutput.NoAction())
            }
        }

        val useCase = LearnInterventionTimingPatternUseCase(
            interventionRepository = interventionRepository,
            patternRepository = patternRepository,
            ironMindAI = mockAI,
            clock = clock,
            idGenerator = idGenerator
        )

        val result = useCase("user-1", 0L)
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)

        assertTrue("Prompt must include behavioral failure", capturedPrompt.contains("BEHAVIORAL_FAILURE"))
        assertTrue("Prompt must include success", capturedPrompt.contains("SUCCESS"))
        assertTrue("Prompt must exclude technical failure", !capturedPrompt.contains("TECHNICAL_FAILURE"))
        assertTrue("Prompt must exclude pending accepted record", !capturedPrompt.contains("rec-timing-pending"))
    }

    @Test
    fun `returns null candidate when zero valid interventions exist without invoking AI`() = runBlocking {
        val techFailure = createRecord("rec-timing-tech-only", InterventionResolutionReason.TECHNICAL_FAILURE, InterventionState.FAILED)
        val pendingAccept = createRecord("rec-timing-pending-only", null, InterventionState.RESPONSE)

        coEvery { interventionRepository.getRecentInterventions("user-1", 0L) } returns Result.Success(
            listOf(techFailure, pendingAccept)
        )

        var aiInvoked = false
        val mockAI = object : IronMindAI {
            override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
                aiInvoked = true
                return Result.Success(AIOutput.NoAction())
            }
        }

        val useCase = LearnInterventionTimingPatternUseCase(
            interventionRepository = interventionRepository,
            patternRepository = patternRepository,
            ironMindAI = mockAI,
            clock = clock,
            idGenerator = idGenerator
        )

        val result = useCase("user-1", 0L)
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
        assertTrue("AI must not be invoked when zero valid interventions exist", !aiInvoked)
    }
}

