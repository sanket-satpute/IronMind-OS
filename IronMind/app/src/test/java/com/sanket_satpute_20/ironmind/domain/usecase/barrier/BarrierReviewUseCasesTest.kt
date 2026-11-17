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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarrierReviewUseCasesTest {

    private lateinit var barrierRepository: TestBarrierRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock

    private lateinit var getBarriersUseCase: GetBarriersUseCase
    private lateinit var confirmBarrierUseCase: ConfirmBarrierUseCase
    private lateinit var refuteBarrierUseCase: RefuteBarrierUseCase
    private lateinit var dismissBarrierUseCase: DismissBarrierUseCase

    private val userId = "user-barrier-123"

    @Before
    fun setUp() {
        eventRepository = FakeEventRepository()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        barrierRepository = TestBarrierRepository(eventRepository)

        getBarriersUseCase = GetBarriersUseCase(barrierRepository)
        confirmBarrierUseCase = ConfirmBarrierUseCase(barrierRepository, eventRepository, idGenerator, clock)
        refuteBarrierUseCase = RefuteBarrierUseCase(barrierRepository, eventRepository, idGenerator, clock)
        dismissBarrierUseCase = DismissBarrierUseCase(barrierRepository, eventRepository, idGenerator, clock)
    }

    private fun createSampleBarrier(
        id: String,
        uid: String = userId,
        confirmationState: BarrierConfirmationState = BarrierConfirmationState.UNCONFIRMED,
        status: BarrierStatus = BarrierStatus.ACTIVE
    ) = BarrierHypothesis(
        id = id,
        userId = uid,
        category = BarrierCategory.LOW_ENERGY,
        description = "Fatigued in late evening sessions",
        confirmationState = confirmationState,
        status = status,
        sourceReflectionId = "refl-99",
        firstObservedAt = clock.currentTimeMillis() - 5000L,
        lastObservedAt = clock.currentTimeMillis()
    )

    @Test
    fun `confirmBarrier transitions state to CONFIRMED and ACTIVE and emits event`() = runBlocking {
        val barrier = createSampleBarrier("bar-1")
        barrierRepository.save(barrier)

        val result = confirmBarrierUseCase(userId, "bar-1")
        assertTrue(result is Result.Success)

        val updated = barrierRepository.get("bar-1")
        assertNotNull(updated)
        assertEquals(BarrierConfirmationState.CONFIRMED, updated!!.confirmationState)
        assertEquals(BarrierStatus.ACTIVE, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.BARRIER_CONFIRMED, events[0].type)
        assertEquals("bar-1", events[0].entityId)
        assertEquals(BarrierConfirmationState.UNCONFIRMED.name, events[0].previousState)
        assertEquals(BarrierConfirmationState.CONFIRMED.name, events[0].newState)
    }

    @Test
    fun `refuteBarrier transitions state to REJECTED and EXPIRED and emits event`() = runBlocking {
        val barrier = createSampleBarrier("bar-2")
        barrierRepository.save(barrier)

        val result = refuteBarrierUseCase(userId, "bar-2")
        assertTrue(result is Result.Success)

        val updated = barrierRepository.get("bar-2")
        assertNotNull(updated)
        assertEquals(BarrierConfirmationState.REJECTED, updated!!.confirmationState)
        assertEquals(BarrierStatus.EXPIRED, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.BARRIER_REJECTED, events[0].type)
        assertEquals("bar-2", events[0].entityId)
        assertEquals(BarrierConfirmationState.UNCONFIRMED.name, events[0].previousState)
        assertEquals(BarrierConfirmationState.REJECTED.name, events[0].newState)
    }

    @Test
    fun `dismissBarrier transitions state to DISMISSED and EXPIRED and emits event`() = runBlocking {
        val barrier = createSampleBarrier("bar-3")
        barrierRepository.save(barrier)

        val result = dismissBarrierUseCase(userId, "bar-3")
        assertTrue(result is Result.Success)

        val updated = barrierRepository.get("bar-3")
        assertNotNull(updated)
        assertEquals(BarrierConfirmationState.DISMISSED, updated!!.confirmationState)
        assertEquals(BarrierStatus.EXPIRED, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.BARRIER_DISMISSED, events[0].type)
        assertEquals("bar-3", events[0].entityId)
        assertEquals(BarrierConfirmationState.UNCONFIRMED.name, events[0].previousState)
        assertEquals(BarrierConfirmationState.DISMISSED.name, events[0].newState)
    }

    @Test
    fun `repeated confirm on already confirmed barrier is idempotent and does not emit duplicate event`() = runBlocking {
        val barrier = createSampleBarrier("bar-rep", confirmationState = BarrierConfirmationState.CONFIRMED, status = BarrierStatus.ACTIVE)
        barrierRepository.save(barrier)

        val result = confirmBarrierUseCase(userId, "bar-rep")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `confirm on already rejected barrier fails with IllegalStateException`() = runBlocking {
        val barrier = createSampleBarrier("bar-rej", confirmationState = BarrierConfirmationState.REJECTED, status = BarrierStatus.EXPIRED)
        barrierRepository.save(barrier)

        val result = confirmBarrierUseCase(userId, "bar-rej")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `repeated refute on already refuted barrier is idempotent and does not emit duplicate event`() = runBlocking {
        val barrier = createSampleBarrier("bar-ref-rep", confirmationState = BarrierConfirmationState.REJECTED, status = BarrierStatus.EXPIRED)
        barrierRepository.save(barrier)

        val result = refuteBarrierUseCase(userId, "bar-ref-rep")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `repeated dismiss on already dismissed barrier is idempotent and does not emit duplicate event`() = runBlocking {
        val barrier = createSampleBarrier("bar-dis-rep", confirmationState = BarrierConfirmationState.DISMISSED, status = BarrierStatus.EXPIRED)
        barrierRepository.save(barrier)

        val result = dismissBarrierUseCase(userId, "bar-dis-rep")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `cross-user barrier confirmation is rejected with failure`() = runBlocking {
        val barrier = createSampleBarrier("bar-other", uid = "user-attacker")
        barrierRepository.save(barrier)

        val result = confirmBarrierUseCase(userId, "bar-other")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `getBarriersUseCase observes barriers for active user`() = runBlocking {
        val barrier = createSampleBarrier("bar-obs")
        barrierRepository.save(barrier)

        val observed = getBarriersUseCase.observe(userId).first()
        assertEquals(1, observed.size)
        assertEquals("bar-obs", observed[0].id)
    }

    private class TestBarrierRepository(
        private val eventRepository: FakeEventRepository? = null
    ) : BarrierRepository {
        private val barriers = mutableMapOf<String, BarrierHypothesis>()

        fun save(barrier: BarrierHypothesis) {
            barriers[barrier.id] = barrier
        }

        fun get(id: String): BarrierHypothesis? = barriers[id]

        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> {
            barriers[barrier.id] = barrier
            return Result.Success(Unit)
        }

        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> =
            Result.Success(barriers.values.filter { it.userId == userId })

        override fun observeBarriersForUser(userId: String): Flow<List<BarrierHypothesis>> =
            flowOf(barriers.values.filter { it.userId == userId })

        override suspend fun getActiveBarriersForUser(
            userId: String,
            excludedStates: List<BarrierConfirmationState>,
            limit: Int
        ): Result<List<BarrierHypothesis>, Exception> =
            Result.Success(barriers.values.filter { it.userId == userId && it.confirmationState !in excludedStates }.take(limit))

        override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> =
            Result.Success(barriers[id])

        override suspend fun updateBarrierState(
            id: String,
            userId: String,
            confirmationState: BarrierConfirmationState,
            status: BarrierStatus,
            now: Long
        ): Result<Unit, Exception> = updateBarrierStateWithEvent(id, userId, confirmationState, status, now, null)

        override suspend fun updateBarrierStateWithEvent(
            id: String,
            userId: String,
            confirmationState: BarrierConfirmationState,
            status: BarrierStatus,
            now: Long,
            event: com.sanket_satpute_20.ironmind.domain.model.Event?
        ): Result<Unit, Exception> {
            val b = barriers[id] ?: return Result.Failure(IllegalArgumentException("Barrier not found: $id"))
            if (b.userId != userId) return Result.Failure(IllegalStateException("Barrier $id does not belong to user $userId"))
            if (b.confirmationState == confirmationState && b.status == status) {
                return Result.Success(Unit)
            }
            barriers[id] = b.copy(
                confirmationState = confirmationState,
                status = status,
                lastObservedAt = now
            )
            if (event != null) {
                eventRepository?.saveEvent(event)
            }
            return Result.Success(Unit)
        }
    }
}

