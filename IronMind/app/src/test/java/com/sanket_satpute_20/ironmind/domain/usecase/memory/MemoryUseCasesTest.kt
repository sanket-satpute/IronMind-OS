package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeMemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryUseCasesTest {

    private lateinit var memoryRepository: FakeMemoryRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator

    private lateinit var proposeMemoryUseCase: ProposeMemoryUseCase
    private lateinit var confirmMemoryUseCase: ConfirmMemoryUseCase
    private lateinit var correctMemoryUseCase: CorrectMemoryUseCase
    private lateinit var weakenMemoryUseCase: WeakenMemoryUseCase
    private lateinit var expireMemoryUseCase: ExpireMemoryUseCase

    @Before
    fun setup() {
        memoryRepository = FakeMemoryRepository()
        eventRepository = FakeEventRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()

        proposeMemoryUseCase = ProposeMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        confirmMemoryUseCase = ConfirmMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        correctMemoryUseCase = CorrectMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        weakenMemoryUseCase = WeakenMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        expireMemoryUseCase = ExpireMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    @Test
    fun `proposeMemory creates UNCONFIRMED ACTIVE memory`() = runTest {
        val result = proposeMemoryUseCase(
            userId = "user-1",
            type = "preference",
            content = "User prefers morning planning.",
            source = EntitySource.USER,
            confidence = 0.8f
        )

        assertTrue(result is Result.Success)
        val memory = (result as Result.Success).data
        assertEquals(MemoryConfirmationState.UNCONFIRMED, memory.confirmationState)
        assertEquals(MemoryStatus.ACTIVE, memory.status)
        assertEquals(0.8f, memory.confidence)
        assertEquals(EntitySource.USER, memory.source)
    }

    @Test
    fun `proposeMemory fails with blank content`() = runTest {
        val result = proposeMemoryUseCase(
            userId = "user-1",
            type = "preference",
            content = "  ",
            source = EntitySource.USER,
            confidence = 0.5f
        )
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `proposeMemory fails with out-of-range confidence`() = runTest {
        val result = proposeMemoryUseCase(
            userId = "user-1",
            type = "preference",
            content = "Valid content",
            source = EntitySource.USER,
            confidence = 1.5f
        )
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `confirmMemory sets USER_CONFIRMED`() = runTest {
        val proposed = (proposeMemoryUseCase(
            userId = "user-1", type = "preference",
            content = "User prefers evening reflection.",
            source = EntitySource.AI, confidence = 0.7f
        ) as Result.Success).data

        val result = confirmMemoryUseCase(userId = "user-1", memoryId = proposed.id)

        assertTrue(result is Result.Success)
        val confirmed = (result as Result.Success).data
        assertEquals(MemoryConfirmationState.USER_CONFIRMED, confirmed.confirmationState)
    }

    @Test
    fun `confirmMemory fails for unknown memoryId`() = runTest {
        val result = confirmMemoryUseCase(userId = "user-1", memoryId = "nonexistent-id")
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `correctMemory updates content and resets to UNCONFIRMED`() = runTest {
        val proposed = (proposeMemoryUseCase(
            userId = "user-1", type = "preference",
            content = "Old content.",
            source = EntitySource.AI, confidence = 0.6f
        ) as Result.Success).data

        val result = correctMemoryUseCase(
            userId = "user-1",
            memoryId = proposed.id,
            newContent = "Corrected content."
        )

        assertTrue(result is Result.Success)
        val corrected = (result as Result.Success).data
        assertEquals("Corrected content.", corrected.content)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, corrected.confirmationState)
    }

    @Test
    fun `weakenMemory transitions status to DECAYING when below threshold`() = runTest {
        val proposed = (proposeMemoryUseCase(
            userId = "user-1", type = "barrier",
            content = "User struggles without accountability partner.",
            source = EntitySource.AI, confidence = 0.7f
        ) as Result.Success).data

        val result = weakenMemoryUseCase(
            userId = "user-1",
            memoryId = proposed.id,
            newConfidence = 0.2f   // Below threshold
        )

        assertTrue(result is Result.Success)
        val weakened = (result as Result.Success).data
        assertEquals(MemoryStatus.DECAYING, weakened.status)
        assertEquals(0.2f, weakened.confidence)
    }

    @Test
    fun `weakenMemory keeps status ACTIVE above threshold`() = runTest {
        val proposed = (proposeMemoryUseCase(
            userId = "user-1", type = "barrier",
            content = "User often delays tasks.",
            source = EntitySource.AI, confidence = 0.9f
        ) as Result.Success).data

        val result = weakenMemoryUseCase(
            userId = "user-1",
            memoryId = proposed.id,
            newConfidence = 0.5f   // Above threshold
        )

        assertTrue(result is Result.Success)
        val weakened = (result as Result.Success).data
        assertEquals(MemoryStatus.ACTIVE, weakened.status)
    }

    @Test
    fun `expireMemory sets status to EXPIRED`() = runTest {
        val proposed = (proposeMemoryUseCase(
            userId = "user-1", type = "preference",
            content = "User preferred gym sessions at 6am.",
            source = EntitySource.USER, confidence = 0.9f
        ) as Result.Success).data

        val result = expireMemoryUseCase(userId = "user-1", memoryId = proposed.id)

        assertTrue(result is Result.Success)
        val expired = (result as Result.Success).data
        assertEquals(MemoryStatus.EXPIRED, expired.status)
    }

    @Test
    fun `getActiveMemoriesForUser excludes EXPIRED memories`() = runTest {
        proposeMemoryUseCase(
            userId = "user-1", type = "preference",
            content = "Active memory.",
            source = EntitySource.USER, confidence = 0.8f
        )

        val proposed2 = (proposeMemoryUseCase(
            userId = "user-1", type = "preference",
            content = "This will expire.",
            source = EntitySource.USER, confidence = 0.5f
        ) as Result.Success).data
        expireMemoryUseCase(userId = "user-1", memoryId = proposed2.id)

        val result = memoryRepository.getActiveMemoriesForUser("user-1")
        assertTrue(result is Result.Success)
        val active = (result as Result.Success).data
        assertEquals(1, active.size)
    }
}
