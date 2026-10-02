package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCandidate
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UnderstandBarriersUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarrierUnderstandingOrchestratorTest {



    private class FakeBarrierRepository : BarrierRepository {
        val savedBarriers = mutableListOf<BarrierHypothesis>()
        var shouldFailSave = false

        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> {
            if (shouldFailSave) return Result.Failure(Exception("DB error"))
            savedBarriers.add(barrier)
            return Result.Success(Unit)
        }

        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(savedBarriers.filter { it.userId == userId })
        }
    }

    private val fakeIdGenerator = object : IdGenerator {
        var count = 0
        override fun generateId(): String = "id-\${++count}"
    }

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = 1000L
    }

    @Test
    fun `processReflection saves returned barriers as unconfirmed hypotheses`() = runBlocking {
        val aiOutput = AIOutput.BarrierOutput(
            proposedBarriers = listOf(
                BarrierCandidate(
                    category = BarrierCategory.FEAR,
                    description = "Possible fear of failure?"
                )
            ),
            confidence = 0.8f,
            reasoning = "Because",
            schemaVersion = 1
        )
        
        val fakeUseCase = mockk<UnderstandBarriersUseCase>()
        coEvery { fakeUseCase(any(), any()) } returns Result.Success(aiOutput)
        val fakeRepo = FakeBarrierRepository()
        
        val orchestrator = BarrierUnderstandingOrchestrator(
            understandBarriersUseCase = fakeUseCase,
            barrierRepository = fakeRepo,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val reflection = Reflection(
            id = "ref-1", 
            userId = "user-1", 
            content = "I am stuck", 
            createdAt = 100L,
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null
        )
        val result = orchestrator.processReflection(reflection)

        assertTrue(result is Result.Success)
        assertEquals(1, fakeRepo.savedBarriers.size)
        
        val saved = fakeRepo.savedBarriers[0]
        assertEquals("user-1", saved.userId)
        assertEquals(BarrierCategory.FEAR, saved.category)
        assertEquals(BarrierConfirmationState.UNCONFIRMED, saved.confirmationState)
        assertEquals(BarrierStatus.ACTIVE, saved.status)
        assertEquals("ref-1", saved.sourceReflectionId)
        assertEquals(1000L, saved.firstObservedAt)
    }

    @Test
    fun `processReflection fails gracefully when usecase fails`() = runBlocking {
        val fakeUseCase = mockk<UnderstandBarriersUseCase>()
        coEvery { fakeUseCase(any(), any()) } returns Result.Failure(Exception("AI Error"))
        val fakeRepo = FakeBarrierRepository()
        
        val orchestrator = BarrierUnderstandingOrchestrator(
            understandBarriersUseCase = fakeUseCase,
            barrierRepository = fakeRepo,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val reflection = Reflection(
            id = "ref-1", 
            userId = "user-1", 
            content = "I am stuck", 
            createdAt = 100L,
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null
        )
        val result = orchestrator.processReflection(reflection)

        assertTrue(result is Result.Failure)
        assertEquals("AI Error", (result as Result.Failure).error.message)
        assertEquals(0, fakeRepo.savedBarriers.size)
    }

    @Test
    fun `processReflection returns failure when saving fails`() = runBlocking {
        val aiOutput = AIOutput.BarrierOutput(
            proposedBarriers = listOf(
                BarrierCandidate(
                    category = BarrierCategory.FEAR,
                    description = "Possible fear of failure?"
                )
            ),
            confidence = 0.8f,
            reasoning = "Because",
            schemaVersion = 1
        )
        
        val fakeUseCase = mockk<UnderstandBarriersUseCase>()
        coEvery { fakeUseCase(any(), any()) } returns Result.Success(aiOutput)
        val fakeRepo = FakeBarrierRepository().apply { shouldFailSave = true }
        
        val orchestrator = BarrierUnderstandingOrchestrator(
            understandBarriersUseCase = fakeUseCase,
            barrierRepository = fakeRepo,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val reflection = Reflection(
            id = "ref-1", 
            userId = "user-1", 
            content = "I am stuck", 
            createdAt = 100L,
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null
        )
        val result = orchestrator.processReflection(reflection)

        assertTrue(result is Result.Failure)
        assertEquals("DB error", (result as Result.Failure).error.message)
    }
}
