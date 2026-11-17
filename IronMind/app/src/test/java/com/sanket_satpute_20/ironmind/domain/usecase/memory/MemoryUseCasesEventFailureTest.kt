package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryUseCasesEventFailureTest {

    private lateinit var memoryRepository: FakeMemoryRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock

    private lateinit var confirmMemoryUseCase: ConfirmMemoryUseCase
    private lateinit var correctMemoryUseCase: CorrectMemoryUseCase
    private lateinit var rejectMemoryUseCase: RejectMemoryUseCase
    private lateinit var deleteMemoryUseCase: DeleteMemoryUseCase

    private val userId = "user-event-fail-test"

    @Before
    fun setUp() {
        eventRepository = FakeEventRepository()
        memoryRepository = FakeMemoryRepository(eventRepository)
        idGenerator = FakeIdGenerator()
        clock = FakeClock()

        confirmMemoryUseCase = ConfirmMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        correctMemoryUseCase = CorrectMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        rejectMemoryUseCase = RejectMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        deleteMemoryUseCase = DeleteMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    private fun createMemory(id: String) = Memory(
        id = id,
        userId = userId,
        type = "fact",
        content = "Important fact",
        source = EntitySource.USER,
        confidence = 1.0f,
        evidenceCount = 1,
        firstObservedAt = 100L,
        lastObservedAt = 200L,
        confirmationState = MemoryConfirmationState.UNCONFIRMED,
        status = MemoryStatus.ACTIVE,
        expiresAt = null,
        createdAt = 100L,
        updatedAt = 200L
    )

    @Test
    fun `confirmMemory propagates event persistence failure and does not return success`() = runBlocking {
        memoryRepository.saveMemory(createMemory("m-fail-1"))
        memoryRepository.shouldFailEventPersistence = true

        val result = confirmMemoryUseCase(userId, "m-fail-1")
        assertTrue(result is Result.Failure)
        assertEquals("Simulated event persistence failure", (result as Result.Failure).error.message)
    }

    @Test
    fun `correctMemory propagates event persistence failure and does not return success`() = runBlocking {
        memoryRepository.saveMemory(createMemory("m-fail-2"))
        memoryRepository.shouldFailEventPersistence = true

        val result = correctMemoryUseCase(userId, "m-fail-2", "New correction")
        assertTrue(result is Result.Failure)
        assertEquals("Simulated event persistence failure", (result as Result.Failure).error.message)
    }

    @Test
    fun `rejectMemory propagates event persistence failure and does not return success`() = runBlocking {
        memoryRepository.saveMemory(createMemory("m-fail-3"))
        memoryRepository.shouldFailEventPersistence = true

        val result = rejectMemoryUseCase(userId, "m-fail-3")
        assertTrue(result is Result.Failure)
        assertEquals("Simulated event persistence failure", (result as Result.Failure).error.message)
    }

    @Test
    fun `deleteMemory propagates event persistence failure and preserves memory`() = runBlocking {
        memoryRepository.saveMemory(createMemory("m-fail-4"))
        memoryRepository.shouldFailEventPersistence = true

        val result = deleteMemoryUseCase(userId, "m-fail-4")
        assertTrue(result is Result.Failure)
        assertEquals("Simulated event persistence failure", (result as Result.Failure).error.message)

        val memory = memoryRepository.getMemoryById("m-fail-4")
        assertTrue(memory is Result.Success && memory.data != null)
    }
}

