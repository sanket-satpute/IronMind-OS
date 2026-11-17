package com.sanket_satpute_20.ironmind.domain.usecase.barrier

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarrierLifecycleEventTest {

    private lateinit var barrierRepository: LifecycleTestBarrierRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock

    private lateinit var confirmBarrierUseCase: ConfirmBarrierUseCase
    private lateinit var refuteBarrierUseCase: RefuteBarrierUseCase
    private lateinit var dismissBarrierUseCase: DismissBarrierUseCase

    private val userId = "user-event-test"

    @Before
    fun setUp() {
        eventRepository = FakeEventRepository()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        barrierRepository = LifecycleTestBarrierRepository(eventRepository)

        confirmBarrierUseCase = ConfirmBarrierUseCase(barrierRepository, eventRepository, idGenerator, clock)
        refuteBarrierUseCase = RefuteBarrierUseCase(barrierRepository, eventRepository, idGenerator, clock)
        dismissBarrierUseCase = DismissBarrierUseCase(barrierRepository, eventRepository, idGenerator, clock)
    }

    private fun createBarrier(id: String, state: BarrierConfirmationState = BarrierConfirmationState.UNCONFIRMED) = BarrierHypothesis(
        id = id,
        userId = userId,
        category = BarrierCategory.LOW_ENERGY,
        description = "Afternoon fatigue",
        confirmationState = state,
        status = BarrierStatus.ACTIVE,
        sourceReflectionId = "ref-1",
        firstObservedAt = 100L,
        lastObservedAt = 200L
    )

    @Test
    fun `confirmBarrier emits BARRIER_CONFIRMED with complete provenance`() = runBlocking {
        barrierRepository.saveBarrier(createBarrier("b1", BarrierConfirmationState.UNCONFIRMED))

        val result = confirmBarrierUseCase(userId, "b1")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(EventType.BARRIER_CONFIRMED, event.type)
        assertEquals("BARRIER", event.entityType)
        assertEquals("b1", event.entityId)
        assertEquals(userId, event.userId)
        assertEquals("UNCONFIRMED", event.previousState)
        assertEquals("CONFIRMED", event.newState)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `refuteBarrier emits BARRIER_REJECTED with complete provenance`() = runBlocking {
        barrierRepository.saveBarrier(createBarrier("b2", BarrierConfirmationState.UNCONFIRMED))

        val result = refuteBarrierUseCase(userId, "b2")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(EventType.BARRIER_REJECTED, event.type)
        assertEquals("b2", event.entityId)
        assertEquals("UNCONFIRMED", event.previousState)
        assertEquals("REJECTED", event.newState)
    }

    @Test
    fun `dismissBarrier emits BARRIER_DISMISSED with complete provenance`() = runBlocking {
        barrierRepository.saveBarrier(createBarrier("b3", BarrierConfirmationState.UNCONFIRMED))

        val result = dismissBarrierUseCase(userId, "b3")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(EventType.BARRIER_DISMISSED, event.type)
        assertEquals("b3", event.entityId)
        assertEquals("UNCONFIRMED", event.previousState)
        assertEquals("DISMISSED", event.newState)
    }

    @Test
    fun `failed operation does not emit audit event`() = runBlocking {
        // Non-existent barrier
        val result = confirmBarrierUseCase(userId, "non-existent")
        assertTrue(result is Result.Failure)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    private class LifecycleTestBarrierRepository(
        private val eventRepo: FakeEventRepository
    ) : BarrierRepository {
        private val map = mutableMapOf<String, BarrierHypothesis>()

        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> {
            map[barrier.id] = barrier
            return Result.Success(Unit)
        }

        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> =
            Result.Success(map.values.filter { it.userId == userId })

        override fun observeBarriersForUser(userId: String): Flow<List<BarrierHypothesis>> =
            flowOf(map.values.filter { it.userId == userId })

        override suspend fun getActiveBarriersForUser(
            userId: String,
            excludedStates: List<BarrierConfirmationState>,
            limit: Int
        ): Result<List<BarrierHypothesis>, Exception> =
            Result.Success(map.values.filter { it.userId == userId && it.confirmationState !in excludedStates }.take(limit))

        override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> =
            Result.Success(map[id])

        override suspend fun updateBarrierStateWithEvent(
            id: String,
            userId: String,
            confirmationState: BarrierConfirmationState,
            status: BarrierStatus,
            now: Long,
            event: com.sanket_satpute_20.ironmind.domain.model.Event?
        ): Result<Unit, Exception> {
            val existing = map[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            if (existing.userId != userId) return Result.Failure(IllegalStateException("Not owner"))
            if (existing.confirmationState == confirmationState && existing.status == status) {
                return Result.Success(Unit)
            }
            map[id] = existing.copy(confirmationState = confirmationState, status = status, lastObservedAt = now)
            if (event != null) {
                eventRepo.saveEvent(event)
            }
            return Result.Success(Unit)
        }
    }
}
