package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatternEngineImplTest {

    private lateinit var clock: FakeClock
    private lateinit var repository: FakePatternRepository
    private lateinit var engine: PatternEngineImpl

    class FakeClock(var time: Long) : Clock {
        override fun currentTimeMillis() = time
    }

    class FakePatternRepository : PatternRepository {
        val store = mutableMapOf<String, Pattern>()

        override suspend fun getPattern(id: String): Result<Pattern?, Exception> =
            Result.Success(store[id])

        override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> =
            Result.Success(store.values.filter { it.userId == userId })

        override suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception> =
            Result.Success(store.values.filter { it.userId == userId && it.type == type })

        override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> =
            Result.Success(store.values.filter { it.userId == userId && it.status == status })

        override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> {
            store[pattern.id] = pattern
            return Result.Success(Unit)
        }

        override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> {
            store[id]?.let { store[id] = it.copy(confidence = confidence, lastObservedAt = lastObservedAt) }
            return Result.Success(Unit)
        }

        override suspend fun deletePattern(id: String): Result<Unit, Exception> {
            store.remove(id)
            return Result.Success(Unit)
        }
    }

    @Before
    fun setup() {
        clock = FakeClock(0L)
        repository = FakePatternRepository()
        engine = PatternEngineImpl(clock, repository)
    }

    private fun makePattern(
        id: String = "p1",
        confidence: Float = 0.8f,
        status: PatternStatus = PatternStatus.ACTIVE,
        lastObservedAt: Long = 0L
    ) = Pattern(
        id = id,
        userId = "user-1",
        type = PatternType.POSTPONEMENT_PATTERN,
        description = "Test pattern",
        conditions = null,
        predictedBehavior = null,
        confidence = confidence,
        evidenceCount = 5,
        evidenceReferences = null,
        firstObservedAt = 0L,
        lastObservedAt = lastObservedAt,
        status = status,
        confirmationState = MemoryConfirmationState.UNCONFIRMED,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `decayStalePatterns does not decay fresh pattern`() = runTest {
        val freshLastObserved = clock.time - (10L * 24L * 60L * 60L * 1000L) // 10 days ago
        repository.store["p1"] = makePattern(lastObservedAt = freshLastObserved)
        clock.time = 0L // now

        engine.decayStalePatterns("user-1")

        val pattern = repository.store["p1"]
        assertEquals(0.8f, pattern?.confidence)
        assertEquals(PatternStatus.ACTIVE, pattern?.status)
    }

    @Test
    fun `decayStalePatterns decays stale pattern confidence`() = runTest {
        // Stale: last observed 31 days ago, now is 0
        val staleLastObserved = -(31L * 24L * 60L * 60L * 1000L) // 31 days in the past relative to 0
        repository.store["p1"] = makePattern(confidence = 0.5f, lastObservedAt = staleLastObserved)
        clock.time = 0L

        engine.decayStalePatterns("user-1")

        // Confidence should be decayed (0.5 * 0.5 = 0.25), still above threshold 0.1 so still ACTIVE
        val pattern = repository.store["p1"]
        assertEquals(0.25f, pattern?.confidence)
        assertEquals(PatternStatus.ACTIVE, pattern?.status)
    }

    @Test
    fun `decayStalePatterns expires pattern when confidence drops below threshold`() = runTest {
        // Stale: last observed 31 days ago
        val staleLastObserved = -(31L * 24L * 60L * 60L * 1000L)
        // confidence = 0.1 → 0.1 * 0.5 = 0.05 < 0.1 threshold → EXPIRED
        repository.store["p1"] = makePattern(confidence = 0.15f, lastObservedAt = staleLastObserved)
        clock.time = 0L

        engine.decayStalePatterns("user-1")

        val pattern = repository.store["p1"]
        assertEquals(PatternStatus.EXPIRED, pattern?.status)
    }
}
