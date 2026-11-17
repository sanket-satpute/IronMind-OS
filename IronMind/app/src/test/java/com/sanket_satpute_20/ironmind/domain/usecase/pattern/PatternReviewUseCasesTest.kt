package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
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

class PatternReviewUseCasesTest {

    private lateinit var patternRepository: TestPatternRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock

    private lateinit var getPatternsUseCase: GetPatternsUseCase
    private lateinit var confirmPatternUseCase: ConfirmPatternUseCase
    private lateinit var refutePatternUseCase: RefutePatternUseCase
    private lateinit var dismissPatternUseCase: DismissPatternUseCase

    private val userId = "user-123"

    @Before
    fun setUp() {
        eventRepository = FakeEventRepository()
        patternRepository = TestPatternRepository(eventRepository)
        idGenerator = FakeIdGenerator()
        clock = FakeClock()

        getPatternsUseCase = GetPatternsUseCase(patternRepository)
        confirmPatternUseCase = ConfirmPatternUseCase(patternRepository, eventRepository, idGenerator, clock)
        refutePatternUseCase = RefutePatternUseCase(patternRepository, eventRepository, idGenerator, clock)
        dismissPatternUseCase = DismissPatternUseCase(patternRepository, eventRepository, idGenerator, clock)
    }

    private fun createSamplePattern(
        id: String,
        uid: String = userId,
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED,
        status: PatternStatus = PatternStatus.ACTIVE
    ) = Pattern(
        id = id,
        userId = uid,
        fingerprint = "fp-$id",
        type = PatternType.TIME_PATTERN,
        description = "Works best around 10am",
        conditions = null,
        predictedBehavior = "High productivity",
        confidence = 0.82f,
        evidenceCount = 4,
        evidenceReferences = emptyList(),
        firstObservedAt = clock.currentTimeMillis() - 10000L,
        lastObservedAt = clock.currentTimeMillis(),
        status = status,
        confirmationState = confirmationState,
        createdAt = clock.currentTimeMillis() - 10000L,
        updatedAt = clock.currentTimeMillis()
    )

    @Test
    fun `confirmPattern transitions state to USER_CONFIRMED and ACTIVE and emits event`() = runBlocking {
        val pattern = createSamplePattern("pat-1")
        patternRepository.save(pattern)

        val result = confirmPatternUseCase(userId, "pat-1")
        assertTrue(result is Result.Success)

        val updated = patternRepository.get("pat-1")
        assertNotNull(updated)
        assertEquals(MemoryConfirmationState.USER_CONFIRMED, updated!!.confirmationState)
        assertEquals(PatternStatus.ACTIVE, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.PATTERN_CONFIRMED, events[0].type)
        assertEquals("pat-1", events[0].entityId)
    }

    @Test
    fun `refutePattern transitions state to USER_REJECTED and DELETED and emits event`() = runBlocking {
        val pattern = createSamplePattern("pat-2")
        patternRepository.save(pattern)

        val result = refutePatternUseCase(userId, "pat-2")
        assertTrue(result is Result.Success)

        val updated = patternRepository.get("pat-2")
        assertNotNull(updated)
        assertEquals(MemoryConfirmationState.USER_REJECTED, updated!!.confirmationState)
        assertEquals(PatternStatus.DELETED, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.PATTERN_REJECTED, events[0].type)
        assertEquals("pat-2", events[0].entityId)
    }

    @Test
    fun `dismissPattern transitions state to USER_REJECTED and INACTIVE and emits event`() = runBlocking {
        val pattern = createSamplePattern("pat-3")
        patternRepository.save(pattern)

        val result = dismissPatternUseCase(userId, "pat-3")
        assertTrue(result is Result.Success)

        val updated = patternRepository.get("pat-3")
        assertNotNull(updated)
        assertEquals(MemoryConfirmationState.USER_REJECTED, updated!!.confirmationState)
        assertEquals(PatternStatus.INACTIVE, updated.status)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.PATTERN_DEACTIVATED, events[0].type)
        assertEquals("pat-3", events[0].entityId)
    }

    @Test
    fun `cross-user pattern confirmation is rejected with failure`() = runBlocking {
        val pattern = createSamplePattern("pat-other", uid = "user-attacker")
        patternRepository.save(pattern)

        val result = confirmPatternUseCase(userId, "pat-other")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `getPatternsUseCase observes patterns for active user`() = runBlocking {
        val pattern = createSamplePattern("pat-obs")
        patternRepository.save(pattern)

        val observed = getPatternsUseCase.observe(userId).first()
        assertEquals(1, observed.size)
        assertEquals("pat-obs", observed[0].id)
    }

    @Test
    fun `repeated confirm on already confirmed pattern is idempotent and does not emit duplicate event`() = runBlocking {
        val pattern = createSamplePattern("pat-rep", confirmationState = MemoryConfirmationState.USER_CONFIRMED, status = PatternStatus.ACTIVE)
        patternRepository.save(pattern)

        val result = confirmPatternUseCase(userId, "pat-rep")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `confirm on already rejected pattern fails with IllegalStateException`() = runBlocking {
        val pattern = createSamplePattern("pat-rej", confirmationState = MemoryConfirmationState.USER_REJECTED, status = PatternStatus.DELETED)
        patternRepository.save(pattern)

        val result = confirmPatternUseCase(userId, "pat-rej")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `repeated refute on already refuted pattern is idempotent and does not emit duplicate event`() = runBlocking {
        val pattern = createSamplePattern("pat-ref-rep", confirmationState = MemoryConfirmationState.USER_REJECTED, status = PatternStatus.DELETED)
        patternRepository.save(pattern)

        val result = refutePatternUseCase(userId, "pat-ref-rep")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    @Test
    fun `repeated dismiss on already dismissed pattern is idempotent and does not emit duplicate event`() = runBlocking {
        val pattern = createSamplePattern("pat-dis-rep", confirmationState = MemoryConfirmationState.USER_REJECTED, status = PatternStatus.INACTIVE)
        patternRepository.save(pattern)

        val result = dismissPatternUseCase(userId, "pat-dis-rep")
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser(userId) as Result.Success).data
        assertEquals(0, events.size)
    }

    private class TestPatternRepository(
        private val eventRepository: FakeEventRepository
    ) : PatternRepository {
        private val patterns = mutableMapOf<String, Pattern>()

        fun save(pattern: Pattern) {
            patterns[pattern.id] = pattern
        }

        fun get(id: String): Pattern? = patterns[id]

        override suspend fun getPattern(id: String): Result<Pattern?, Exception> =
            Result.Success(patterns[id])

        override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> =
            Result.Success(patterns.values.filter { it.userId == userId })

        override fun observePatternsForUser(userId: String): Flow<List<Pattern>> =
            flowOf(patterns.values.filter { it.userId == userId && it.status != PatternStatus.DELETED })

        override suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception> =
            Result.Success(patterns.values.filter { it.userId == userId && it.type == type })

        override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> =
            Result.Success(patterns.values.filter { it.userId == userId && it.status == status })

        override suspend fun getPatternsByStatus(userId: String, status: PatternStatus, limit: Int): Result<List<Pattern>, Exception> =
            Result.Success(patterns.values.filter { it.userId == userId && it.status == status }.take(limit))

        override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> {
            patterns[pattern.id] = pattern
            return Result.Success(Unit)
        }

        override suspend fun updatePattern(pattern: Pattern): Result<Unit, Exception> {
            patterns[pattern.id] = pattern
            return Result.Success(Unit)
        }

        override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> {
            val p = patterns[id]
            if (p != null) patterns[id] = p.copy(confidence = confidence, lastObservedAt = lastObservedAt)
            return Result.Success(Unit)
        }

        override suspend fun updatePatternState(
            id: String,
            userId: String,
            confirmationState: MemoryConfirmationState,
            status: PatternStatus,
            updatedAt: Long
        ): Result<Unit, Exception> = updatePatternStateWithEvent(id, userId, confirmationState, status, updatedAt, null)

        override suspend fun updatePatternStateWithEvent(
            id: String,
            userId: String,
            confirmationState: MemoryConfirmationState,
            status: PatternStatus,
            updatedAt: Long,
            event: com.sanket_satpute_20.ironmind.domain.model.Event?
        ): Result<Unit, Exception> {
            val p = patterns[id] ?: return Result.Failure(IllegalArgumentException("Pattern not found: $id"))
            if (p.userId != userId) return Result.Failure(IllegalStateException("Pattern $id does not belong to user $userId"))
            if (p.confirmationState == confirmationState && p.status == status) {
                return Result.Success(Unit)
            }
            patterns[id] = p.copy(
                confirmationState = confirmationState,
                status = status,
                updatedAt = updatedAt
            )
            if (event != null) {
                eventRepository.saveEvent(event)
            }
            return Result.Success(Unit)
        }

        override suspend fun deletePattern(id: String): Result<Unit, Exception> {
            patterns.remove(id)
            return Result.Success(Unit)
        }

        override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception> =
            Result.Success(patterns.values.find { it.userId == userId && it.fingerprint == fingerprint })
    }
}

