package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.PatternDao
import com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatternRepositoryImplTest {

    private lateinit var fakeDao: FakePatternDao
    private lateinit var repository: PatternRepositoryImpl

    class FakePatternDao : PatternDao {
        val store = mutableMapOf<String, PatternEntity>()

        override fun getPatternById(id: String): PatternEntity? = store[id]

        override fun getPatternsForUser(userId: String): List<PatternEntity> =
            store.values.filter { it.userId == userId }

        override fun getPatternsByType(userId: String, type: String): List<PatternEntity> =
            store.values.filter { it.userId == userId && it.type == type }

        override fun getPatternsByStatus(userId: String, status: String): List<PatternEntity> =
            store.values.filter { it.userId == userId && it.status == status }

        override fun insertPattern(pattern: PatternEntity) {
            store[pattern.id] = pattern
        }

        override fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long, updatedAt: Long) {
            store[id]?.let { store[id] = it.copy(confidence = confidence, lastObservedAt = lastObservedAt, updatedAt = updatedAt) }
        }

        override fun deletePattern(id: String) {
            store.remove(id)
        }

        override fun getPatternCount(): Flow<Int> = flowOf(store.size)

        override fun deletePatternsForUser(userId: String) {
            store.values.removeIf { it.userId == userId }
        }

        override fun deleteExpiredPatternsOlderThan(userId: String, thresholdTime: Long) {
            store.values.removeIf { it.userId == userId && it.updatedAt < thresholdTime }
        }
    }

    @Before
    fun setup() {
        fakeDao = FakePatternDao()
        repository = PatternRepositoryImpl(fakeDao)
    }

    private fun makePattern(id: String = "p1", userId: String = "user-1") = Pattern(
        id = id,
        userId = userId,
        type = PatternType.POSTPONEMENT_PATTERN,
        description = "Tends to postpone gym",
        conditions = null,
        predictedBehavior = null,
        confidence = 0.8f,
        evidenceCount = 10,
        evidenceReferences = listOf("obs-1", "obs-2"),
        firstObservedAt = 1000L,
        lastObservedAt = 2000L,
        status = PatternStatus.ACTIVE,
        confirmationState = MemoryConfirmationState.UNCONFIRMED,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Test
    fun `savePattern and getPattern roundtrip`() = runTest {
        val pattern = makePattern()
        repository.savePattern(pattern)
        val result = repository.getPattern("p1")
        assertTrue(result is Result.Success)
        val retrieved = (result as Result.Success).data
        assertNotNull(retrieved)
        assertEquals("p1", retrieved?.id)
        assertEquals(0.8f, retrieved?.confidence)
        assertEquals(listOf("obs-1", "obs-2"), retrieved?.evidenceReferences)
    }

    @Test
    fun `getPatternsForUser returns all user patterns`() = runTest {
        repository.savePattern(makePattern("p1", "user-1"))
        repository.savePattern(makePattern("p2", "user-1"))
        repository.savePattern(makePattern("p3", "user-2"))
        val result = repository.getPatternsForUser("user-1")
        assertTrue(result is Result.Success)
        assertEquals(2, (result as Result.Success).data.size)
    }

    @Test
    fun `updatePatternConfidence updates correctly`() = runTest {
        val pattern = makePattern()
        repository.savePattern(pattern)
        repository.updatePatternConfidence("p1", 0.4f, 3000L)
        val result = repository.getPattern("p1")
        val retrieved = (result as Result.Success).data
        assertEquals(0.4f, retrieved?.confidence)
    }

    @Test
    fun `deletePattern removes entry`() = runTest {
        val pattern = makePattern()
        repository.savePattern(pattern)
        repository.deletePattern("p1")
        val result = repository.getPattern("p1")
        val retrieved = (result as Result.Success).data
        assertNull(retrieved)
    }
}
