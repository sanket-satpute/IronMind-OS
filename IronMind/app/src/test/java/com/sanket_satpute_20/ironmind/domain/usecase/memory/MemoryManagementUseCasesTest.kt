package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeMemoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryManagementUseCasesTest {

    private lateinit var memoryRepository: FakeMemoryRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock

    private lateinit var getMemoriesUseCase: GetMemoriesUseCase
    private lateinit var confirmMemoryUseCase: ConfirmMemoryUseCase
    private lateinit var correctMemoryUseCase: CorrectMemoryUseCase
    private lateinit var rejectMemoryUseCase: RejectMemoryUseCase
    private lateinit var deleteMemoryUseCase: DeleteMemoryUseCase

    private val userId = "user-memory-123"

    @Before
    fun setUp() {
        eventRepository = FakeEventRepository()
        memoryRepository = FakeMemoryRepository(eventRepository)
        idGenerator = FakeIdGenerator()
        clock = FakeClock()

        getMemoriesUseCase = GetMemoriesUseCase(memoryRepository)
        confirmMemoryUseCase = ConfirmMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        correctMemoryUseCase = CorrectMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        rejectMemoryUseCase = RejectMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
        deleteMemoryUseCase = DeleteMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    private fun createSampleMemory(
        id: String,
        uid: String = userId,
        content: String = "Prefers morning workout sessions",
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED,
        status: MemoryStatus = MemoryStatus.ACTIVE,
        source: EntitySource = EntitySource.AI,
        confidence: Float = 0.75f
    ) = Memory(
        id = id,
        userId = uid,
        type = "preference",
        content = content,
        source = source,
        confidence = confidence,
        evidenceCount = 2,
        firstObservedAt = clock.currentTimeMillis() - 10000L,
        lastObservedAt = clock.currentTimeMillis(),
        confirmationState = confirmationState,
        status = status,
        expiresAt = null,
        createdAt = clock.currentTimeMillis() - 10000L,
        updatedAt = clock.currentTimeMillis()
    )

    @Test
    fun `confirmMemory transitions state to USER_CONFIRMED and emits MEMORY_UPDATED event`() = runBlocking {
        val memory = createSampleMemory("mem-1")
        memoryRepository.saveMemory(memory)

        val result = confirmMemoryUseCase(userId, "mem-1")
        assertTrue(result is Result.Success)

        val updated = (memoryRepository.getMemoryById("mem-1") as Result.Success).data
        assertNotNull(updated)
        assertEquals(MemoryConfirmationState.USER_CONFIRMED, updated!!.confirmationState)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.MEMORY_UPDATED, events[0].type)
        assertEquals("mem-1", events[0].entityId)
    }

    @Test
    fun `correctMemory updates content, sets USER_CONFIRMED, confidence 1_0, source USER, and emits event`() = runBlocking {
        val memory = createSampleMemory("mem-2", content = "Prefers working at 2 PM")
        memoryRepository.saveMemory(memory)

        val result = correctMemoryUseCase(userId, "mem-2", "Prefers working at 9 AM")
        assertTrue(result is Result.Success)

        val updated = (memoryRepository.getMemoryById("mem-2") as Result.Success).data
        assertNotNull(updated)
        assertEquals("Prefers working at 9 AM", updated!!.content)
        assertEquals(MemoryConfirmationState.USER_CONFIRMED, updated.confirmationState)
        assertEquals(EntitySource.USER, updated.source)
        assertEquals(1.0f, updated.confidence, 0.001f)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.MEMORY_UPDATED, events[0].type)
        assertEquals("Prefers working at 2 PM", events[0].previousState)
        assertEquals("Prefers working at 9 AM", events[0].newState)
    }

    @Test
    fun `rejectMemory transitions state to USER_REJECTED and INACTIVE and emits MEMORY_REJECTED event`() = runBlocking {
        val memory = createSampleMemory("mem-3")
        memoryRepository.saveMemory(memory)

        val result = rejectMemoryUseCase(userId, "mem-3")
        assertTrue(result is Result.Success)

        val updated = (memoryRepository.getMemoryById("mem-3") as Result.Success).data
        assertNotNull(updated)
        assertEquals(MemoryConfirmationState.USER_REJECTED, updated!!.confirmationState)
        assertEquals(MemoryStatus.INACTIVE, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.MEMORY_REJECTED, events[0].type)
        assertEquals("mem-3", events[0].entityId)
    }

    @Test
    fun `deleteMemory deletes from repository and emits MEMORY_DELETED event`() = runBlocking {
        val memory = createSampleMemory("mem-4")
        memoryRepository.saveMemory(memory)

        val result = deleteMemoryUseCase(userId, "mem-4")
        assertTrue(result is Result.Success)

        val deleted = (memoryRepository.getMemoryById("mem-4") as Result.Success).data
        assertNull(deleted)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.MEMORY_DELETED, events[0].type)
        assertEquals("mem-4", events[0].entityId)
    }

    @Test
    fun `cross-user memory operations fail closed with IllegalStateException`() = runBlocking {
        val memory = createSampleMemory("mem-other", uid = "user-attacker")
        memoryRepository.saveMemory(memory)

        val confirmResult = confirmMemoryUseCase(userId, "mem-other")
        assertTrue(confirmResult is Result.Failure)
        assertTrue((confirmResult as Result.Failure).error is IllegalStateException)

        val correctResult = correctMemoryUseCase(userId, "mem-other", "Hacked content")
        assertTrue(correctResult is Result.Failure)
        assertTrue((correctResult as Result.Failure).error is IllegalStateException)

        val rejectResult = rejectMemoryUseCase(userId, "mem-other")
        assertTrue(rejectResult is Result.Failure)
        assertTrue((rejectResult as Result.Failure).error is IllegalStateException)

        val deleteResult = deleteMemoryUseCase(userId, "mem-other")
        assertTrue(deleteResult is Result.Failure)
        assertTrue((deleteResult as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `getMemoriesUseCase observes memories for active user`() = runBlocking {
        val memory = createSampleMemory("mem-obs")
        memoryRepository.saveMemory(memory)

        val observed = getMemoriesUseCase.observe(userId).first()
        assertEquals(1, observed.size)
        assertEquals("mem-obs", observed[0].id)
    }

    @Test
    fun `event persistence failure in confirm propagates failure and does not report success`() = runBlocking {
        val memory = createSampleMemory("mem-fail-1")
        memoryRepository.saveMemory(memory)
        memoryRepository.shouldFailEventPersistence = true

        val result = confirmMemoryUseCase(userId, "mem-fail-1")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `event persistence failure in correct propagates failure and does not report success`() = runBlocking {
        val memory = createSampleMemory("mem-fail-2")
        memoryRepository.saveMemory(memory)
        memoryRepository.shouldFailEventPersistence = true

        val result = correctMemoryUseCase(userId, "mem-fail-2", "New corrected text")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `event persistence failure in reject propagates failure and does not report success`() = runBlocking {
        val memory = createSampleMemory("mem-fail-3")
        memoryRepository.saveMemory(memory)
        memoryRepository.shouldFailEventPersistence = true

        val result = rejectMemoryUseCase(userId, "mem-fail-3")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `event persistence failure in delete propagates failure and memory remains un-deleted`() = runBlocking {
        val memory = createSampleMemory("mem-fail-4")
        memoryRepository.saveMemory(memory)
        memoryRepository.shouldFailEventPersistence = true

        val result = deleteMemoryUseCase(userId, "mem-fail-4")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)

        // Verify memory was not deleted
        val check = memoryRepository.getMemoryById("mem-fail-4")
        assertTrue(check is Result.Success && check.data != null)
    }

    @Test
    fun `repeated confirm on already confirmed memory is idempotent and does not emit duplicate event`() = runBlocking {
        val memory = createSampleMemory("mem-rep-conf", confirmationState = MemoryConfirmationState.USER_CONFIRMED)
        memoryRepository.saveMemory(memory)

        val result = confirmMemoryUseCase(userId, "mem-rep-conf")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `repeated reject on already rejected memory is idempotent and does not emit duplicate event`() = runBlocking {
        val memory = createSampleMemory("mem-rep-rej", confirmationState = MemoryConfirmationState.USER_REJECTED, status = com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.INACTIVE)
        memoryRepository.saveMemory(memory)

        val result = rejectMemoryUseCase(userId, "mem-rep-rej")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }
}

