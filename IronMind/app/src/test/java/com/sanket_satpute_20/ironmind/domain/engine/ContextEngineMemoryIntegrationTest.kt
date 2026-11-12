package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeMemoryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextEngineMemoryIntegrationTest {

    private lateinit var clock: FakeClock
    private val commitmentRepository: CommitmentRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase = mockk(relaxed = true)
    private val reflectionRepository: ReflectionRepository = mockk(relaxed = true)
    private val protectionRepository: ProtectionRepository = mockk(relaxed = true)
    private val goalRepository: GoalRepository = mockk(relaxed = true)
    private val patternRepository: PatternRepository = mockk(relaxed = true)
    private lateinit var memoryRepository: FakeMemoryRepository

    private lateinit var contextEngine: ContextEngineImpl

    private val userA = "user-alice"
    private val userB = "user-bob"

    @Before
    fun setUp() {
        clock = FakeClock(1_700_000_000_000L)
        memoryRepository = FakeMemoryRepository()

        val mockFactualSnapshot = mockk<FactualContextSnapshot>(relaxed = true)
        coEvery { buildFactualContextSnapshotUseCase(any(), any(), any()) } returns Result.Success(mockFactualSnapshot)
        coEvery { commitmentRepository.getCommitmentsForUser(any()) } returns Result.Success(emptyList())
        coEvery { eventRepository.getEventsForUser(any()) } returns Result.Success(emptyList())
        coEvery { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { reflectionRepository.getReflectionsForDateRange(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getActiveProtectionSessionsForUser(any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getProtectionSessionsForUser(any()) } returns Result.Success(emptyList())
        coEvery { goalRepository.getGoalsForUser(any()) } returns Result.Success(emptyList())
        coEvery { patternRepository.getPatternsForUser(any()) } returns Result.Success(emptyList())

        contextEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = commitmentRepository,
            eventRepository = eventRepository,
            buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository,
            patternRepository = patternRepository,
            memoryRepository = memoryRepository
        )
    }

    private fun createMemory(
        id: String,
        userId: String = userA,
        content: String = "Test memory",
        confidence: Float = 0.8f,
        lastObservedAt: Long = 1_000L,
        status: MemoryStatus = MemoryStatus.ACTIVE,
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED
    ) = Memory(
        id = id,
        userId = userId,
        type = "habit",
        content = content,
        source = EntitySource.AI,
        confidence = confidence,
        evidenceCount = 1,
        firstObservedAt = 500L,
        lastObservedAt = lastObservedAt,
        confirmationState = confirmationState,
        status = status,
        expiresAt = null,
        createdAt = 500L,
        updatedAt = lastObservedAt
    )

    @Test
    fun `attaches active eligible memories to context snapshot`() = runTest {
        memoryRepository.saveMemory(createMemory("m1", userA, "Morning meditation", confidence = 0.9f))
        memoryRepository.saveMemory(createMemory("m2", userA, "Runs in the evening", confidence = 0.85f))

        val result = contextEngine.getCurrentContext(userA)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        assertEquals(2, snapshot.activeMemories.size)
        assertEquals("m1", snapshot.activeMemories[0].id)
        assertEquals("m2", snapshot.activeMemories[1].id)
    }

    @Test
    fun `excludes rejected, deleted, expired, and inactive memories`() = runTest {
        // Eligible
        memoryRepository.saveMemory(createMemory("valid-1", userA, status = MemoryStatus.ACTIVE, confirmationState = MemoryConfirmationState.USER_CONFIRMED))
        memoryRepository.saveMemory(createMemory("valid-2", userA, status = MemoryStatus.ACTIVE, confirmationState = MemoryConfirmationState.UNCONFIRMED))

        // Excluded by confirmation state
        memoryRepository.saveMemory(createMemory("rejected", userA, status = MemoryStatus.ACTIVE, confirmationState = MemoryConfirmationState.USER_REJECTED))

        // Excluded by status
        memoryRepository.saveMemory(createMemory("deleted", userA, status = MemoryStatus.DELETED))
        memoryRepository.saveMemory(createMemory("expired", userA, status = MemoryStatus.EXPIRED))
        memoryRepository.saveMemory(createMemory("inactive", userA, status = MemoryStatus.INACTIVE))

        val result = contextEngine.getCurrentContext(userA)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        val ids = snapshot.activeMemories.map { it.id }
        assertEquals(listOf("valid-1", "valid-2"), ids)
    }

    @Test
    fun `sorts memories by confidence descending and lastObservedAt descending, taking top 10`() = runTest {
        for (i in 1..15) {
            memoryRepository.saveMemory(
                createMemory(
                    id = "mem-$i",
                    userId = userA,
                    confidence = i * 0.05f, // 0.05 to 0.75
                    lastObservedAt = 1000L + i
                )
            )
        }

        val result = contextEngine.getCurrentContext(userA)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        assertEquals(10, snapshot.activeMemories.size)
        // Highest confidence first: mem-15 (0.75), mem-14 (0.70), ..., mem-6 (0.30)
        assertEquals("mem-15", snapshot.activeMemories.first().id)
        assertEquals("mem-6", snapshot.activeMemories.last().id)
    }

    @Test
    fun `strictly preserves user isolation in snapshot generation`() = runTest {
        memoryRepository.saveMemory(createMemory("alice-1", userId = userA, content = "Alice secret memory"))
        memoryRepository.saveMemory(createMemory("bob-1", userId = userB, content = "Bob secret memory"))

        val aliceSnapshot = (contextEngine.getCurrentContext(userA) as Result.Success).data
        val bobSnapshot = (contextEngine.getCurrentContext(userB) as Result.Success).data

        assertEquals(1, aliceSnapshot.activeMemories.size)
        assertEquals("alice-1", aliceSnapshot.activeMemories[0].id)

        assertEquals(1, bobSnapshot.activeMemories.size)
        assertEquals("bob-1", bobSnapshot.activeMemories[0].id)
    }

    @Test
    fun `epistemic transparency is preserved on snapshot memories`() = runTest {
        memoryRepository.saveMemory(createMemory("fact-1", userA, confirmationState = MemoryConfirmationState.USER_CONFIRMED))
        memoryRepository.saveMemory(createMemory("hypo-1", userA, confirmationState = MemoryConfirmationState.UNCONFIRMED))

        val snapshot = (contextEngine.getCurrentContext(userA) as Result.Success).data
        val fact = snapshot.activeMemories.first { it.id == "fact-1" }
        val hypo = snapshot.activeMemories.first { it.id == "hypo-1" }

        assertEquals(MemoryConfirmationState.USER_CONFIRMED, fact.confirmationState)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, hypo.confirmationState)
    }

    @Test
    fun `snapshot excludes patterns that are INACTIVE, DELETED, EXPIRED, or USER_REJECTED`() = runTest {
        val activeConfirmed = Pattern(
            id = "pat-active-conf", userId = userA, fingerprint = "fp1", type = PatternType.TIME_PATTERN, description = "Active Confirmed",
            conditions = null, predictedBehavior = null, confidence = 0.9f, evidenceCount = 2, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L, status = PatternStatus.ACTIVE, confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 100L, updatedAt = 500L
        )
        val activeUnconfirmed = Pattern(
            id = "pat-active-unconf", userId = userA, fingerprint = "fp2", type = PatternType.TIME_PATTERN, description = "Active Unconfirmed",
            conditions = null, predictedBehavior = null, confidence = 0.8f, evidenceCount = 2, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L, status = PatternStatus.ACTIVE, confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = 100L, updatedAt = 400L
        )
        val inactivePattern = Pattern(
            id = "pat-inactive", userId = userA, fingerprint = "fp3", type = PatternType.TIME_PATTERN, description = "Inactive",
            conditions = null, predictedBehavior = null, confidence = 0.8f, evidenceCount = 2, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L, status = PatternStatus.INACTIVE, confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = 100L, updatedAt = 300L
        )
        val deletedPattern = Pattern(
            id = "pat-deleted", userId = userA, fingerprint = "fp4", type = PatternType.TIME_PATTERN, description = "Deleted",
            conditions = null, predictedBehavior = null, confidence = 0.8f, evidenceCount = 2, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L, status = PatternStatus.DELETED, confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = 100L, updatedAt = 200L
        )
        val expiredPattern = Pattern(
            id = "pat-expired", userId = userA, fingerprint = "fp5", type = PatternType.TIME_PATTERN, description = "Expired",
            conditions = null, predictedBehavior = null, confidence = 0.1f, evidenceCount = 2, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L, status = PatternStatus.EXPIRED, confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = 100L, updatedAt = 100L
        )
        val rejectedPattern = Pattern(
            id = "pat-rejected", userId = userA, fingerprint = "fp6", type = PatternType.TIME_PATTERN, description = "User Rejected",
            conditions = null, predictedBehavior = null, confidence = 0.8f, evidenceCount = 2, evidenceReferences = emptyList(),
            firstObservedAt = 100L, lastObservedAt = 200L, status = PatternStatus.ACTIVE, confirmationState = MemoryConfirmationState.USER_REJECTED,
            createdAt = 100L, updatedAt = 600L
        )

        coEvery { patternRepository.getPatternsForUser(userA) } returns Result.Success(
            listOf(activeConfirmed, activeUnconfirmed, inactivePattern, deletedPattern, expiredPattern, rejectedPattern)
        )

        val snapshot = (contextEngine.getCurrentContext(userA) as Result.Success).data
        val patternIds = snapshot.recentPatterns.map { it.id }

        assertTrue(patternIds.contains("pat-active-conf"))
        assertTrue(patternIds.contains("pat-active-unconf"))
        org.junit.Assert.assertFalse(patternIds.contains("pat-inactive"))
        org.junit.Assert.assertFalse(patternIds.contains("pat-deleted"))
        org.junit.Assert.assertFalse(patternIds.contains("pat-expired"))
        org.junit.Assert.assertFalse(patternIds.contains("pat-rejected"))
    }
}
