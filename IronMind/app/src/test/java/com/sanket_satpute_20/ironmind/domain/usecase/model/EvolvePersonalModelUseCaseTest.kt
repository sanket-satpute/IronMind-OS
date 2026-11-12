package com.sanket_satpute_20.ironmind.domain.usecase.model

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class EvolvePersonalModelUseCaseTest {

    private val mockAi = mockk<IronMindAI>()
    private val mockContextEngine = mockk<ContextEngine>()
    private val mockPatternRepo = mockk<PatternRepository>()
    private val mockEvaluatePatternCandidatesUseCase = mockk<EvaluatePatternCandidatesUseCase>()
    private val mockUserProfileRepo = mockk<UserProfileRepository>()

    private val userId = "user-test-456"

    private fun createDummySnapshot(hasEvidence: Boolean = true) = ContextSnapshot(
        timestamp = 1000L,
        dayOfWeek = 1,
        activeCommitments = if (hasEvidence) listOf(
            com.sanket_satpute_20.ironmind.domain.model.Commitment(
                id = "c1",
                userId = userId,
                title = "Study",
                description = "",
                priority = 1,
                source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
                status = com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus.COMMITTED,
                committedAt = 1000L,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        ) else emptyList(),
        recentEvents = emptyList(),
        factualContextSnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            appUsage = null,
            activity = null,
            calendar = null,
            location = null,
            notifications = null
        ),
        recentReflections = emptyList(),
        recentPatterns = emptyList(),
        activeProtectionSession = null,
        activeGoals = emptyList()
    )

    @Test
    fun `valid evolved candidates are delegated to EvaluatePatternCandidatesUseCase with user timezone`() = runBlocking {
        val snapshot = createDummySnapshot()
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)

        val candidate = PatternCandidate(
            type = PatternType.CONTEXT_PATTERN,
            description = "Evening work leads to task completion delays"
        )
        val aiOutput = AIOutput.ModelEvolution(
            evolvedCandidates = listOf(candidate),
            confidence = 0.85f
        )
        coEvery { mockAi.process(any()) } returns Result.Success(aiOutput)

        val profile = UserProfile(
            id = userId,
            createdAt = 1000L,
            updatedAt = 1000L,
            displayName = "Test User",
            timezone = "America/New_York",
            createdFrom = "test",
            status = "active"
        )
        coEvery { mockUserProfileRepo.getProfile(userId) } returns Result.Success(profile)
        coEvery {
            mockEvaluatePatternCandidatesUseCase.invoke(userId, listOf(candidate), ZoneId.of("America/New_York"))
        } returns Result.Success(Unit)

        val useCase = EvolvePersonalModelUseCase(
            ironMindAI = mockAi,
            contextEngine = mockContextEngine,
            patternRepository = mockPatternRepo,
            evaluatePatternCandidatesUseCase = mockEvaluatePatternCandidatesUseCase,
            userProfileRepository = mockUserProfileRepo
        )

        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            mockEvaluatePatternCandidatesUseCase.invoke(userId, listOf(candidate), ZoneId.of("America/New_York"))
        }
    }

    @Test
    fun `candidate evaluation failure propagates cleanly`() = runBlocking {
        val snapshot = createDummySnapshot()
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)

        val candidate = PatternCandidate(
            type = PatternType.TIME_PATTERN,
            description = "Weekend productivity drop"
        )
        val aiOutput = AIOutput.ModelEvolution(
            evolvedCandidates = listOf(candidate),
            confidence = 0.85f
        )
        coEvery { mockAi.process(any()) } returns Result.Success(aiOutput)
        coEvery { mockUserProfileRepo.getProfile(userId) } returns Result.Success(null)

        val evalException = RuntimeException("Pattern acceptance pipeline failed")
        coEvery {
            mockEvaluatePatternCandidatesUseCase.invoke(userId, listOf(candidate), any())
        } returns Result.Failure(evalException)

        val useCase = EvolvePersonalModelUseCase(
            ironMindAI = mockAi,
            contextEngine = mockContextEngine,
            patternRepository = mockPatternRepo,
            evaluatePatternCandidatesUseCase = mockEvaluatePatternCandidatesUseCase,
            userProfileRepository = mockUserProfileRepo
        )

        val result = useCase(userId)

        assertTrue(result is Result.Failure)
        assertEquals(evalException, (result as Result.Failure).error)
    }

    @Test
    fun `empty evolved candidates list completes successfully without invoking candidate evaluator`() = runBlocking {
        val snapshot = createDummySnapshot()
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)

        val aiOutput = AIOutput.ModelEvolution(
            evolvedCandidates = emptyList(),
            confidence = 0.9f
        )
        coEvery { mockAi.process(any()) } returns Result.Success(aiOutput)

        val useCase = EvolvePersonalModelUseCase(
            ironMindAI = mockAi,
            contextEngine = mockContextEngine,
            patternRepository = mockPatternRepo,
            evaluatePatternCandidatesUseCase = mockEvaluatePatternCandidatesUseCase,
            userProfileRepository = mockUserProfileRepo
        )

        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) {
            mockEvaluatePatternCandidatesUseCase.invoke(any(), any(), any())
        }
    }

    @Test
    fun `context engine failure fails closed`() = runBlocking {
        val contextError = RuntimeException("SQLite lock failure during context snapshot")
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Failure(contextError)

        val useCase = EvolvePersonalModelUseCase(
            ironMindAI = mockAi,
            contextEngine = mockContextEngine,
            patternRepository = mockPatternRepo,
            evaluatePatternCandidatesUseCase = mockEvaluatePatternCandidatesUseCase
        )

        val result = useCase(userId)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error.message?.contains("Failed to gather context") == true)
    }

    @Test
    fun `insufficient context evidence returns success without invoking AI`() = runBlocking {
        val emptySnapshot = createDummySnapshot(hasEvidence = false)
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(emptySnapshot)

        val useCase = EvolvePersonalModelUseCase(
            ironMindAI = mockAi,
            contextEngine = mockContextEngine,
            patternRepository = mockPatternRepo,
            evaluatePatternCandidatesUseCase = mockEvaluatePatternCandidatesUseCase
        )

        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { mockAi.process(any()) }
    }

    @Test
    fun `memories alone without primary evidence do not trigger AI inference`() = runBlocking {
        val mem = com.sanket_satpute_20.ironmind.domain.model.Memory(
            id = "mem-1", userId = userId, type = "fact", content = "Likes coding",
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
            confidence = 1.0f, evidenceCount = 1, firstObservedAt = 100L, lastObservedAt = 200L,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_CONFIRMED,
            status = com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.ACTIVE,
            expiresAt = null, createdAt = 100L, updatedAt = 200L
        )

        // Snapshot with memories but NO primary evidence (no commitments, events, patterns, reflections, or activity)
        val snapshotWithOnlyMemories = createDummySnapshot(hasEvidence = false).copy(
            activeMemories = listOf(mem)
        )
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshotWithOnlyMemories)

        val useCase = EvolvePersonalModelUseCase(
            ironMindAI = mockAi,
            contextEngine = mockContextEngine,
            patternRepository = mockPatternRepo,
            evaluatePatternCandidatesUseCase = mockEvaluatePatternCandidatesUseCase
        )

        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { mockAi.process(any()) }
    }

    @Test
    fun `empty factual aggregations with zero observation counts do not invoke AI`() = runBlocking {
        val zeroCountFactual = FactualContextSnapshot(
            userId = userId,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            appUsage = com.sanket_satpute_20.ironmind.domain.model.observation.AppUsageObservationAggregation(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                observationCount = 0, totalDurationMs = 0L, uniquePackageCount = 0
            ),
            activity = com.sanket_satpute_20.ironmind.domain.model.observation.ActivityObservationAggregation(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                observationCount = 0, distinctActivityStateCount = 0,
                perStateObservationCounts = emptyMap(), firstOccurrenceTimestamp = null, lastOccurrenceTimestamp = null
            ),
            calendar = null,
            location = null,
            notifications = null
        )
        val snapshot = createDummySnapshot(hasEvidence = false).copy(factualContextSnapshot = zeroCountFactual)
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)

        val useCase = EvolvePersonalModelUseCase(mockAi, mockContextEngine, mockPatternRepo, mockEvaluatePatternCandidatesUseCase)
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { mockAi.process(any()) }
    }

    @Test
    fun `placeholder UNKNOWN activity observations alone do not invoke AI`() = runBlocking {
        val unknownOnlyActivityFactual = FactualContextSnapshot(
            userId = userId,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            appUsage = com.sanket_satpute_20.ironmind.domain.model.observation.AppUsageObservationAggregation(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                observationCount = 0, totalDurationMs = 0L, uniquePackageCount = 0
            ),
            activity = com.sanket_satpute_20.ironmind.domain.model.observation.ActivityObservationAggregation(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                observationCount = 4, distinctActivityStateCount = 1,
                perStateObservationCounts = mapOf("UNKNOWN" to 4),
                firstOccurrenceTimestamp = 100L, lastOccurrenceTimestamp = 900L
            ),
            calendar = null,
            location = null,
            notifications = null
        )
        val snapshot = createDummySnapshot(hasEvidence = false).copy(factualContextSnapshot = unknownOnlyActivityFactual)
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)

        val useCase = EvolvePersonalModelUseCase(mockAi, mockContextEngine, mockPatternRepo, mockEvaluatePatternCandidatesUseCase)
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { mockAi.process(any()) }
    }

    @Test
    fun `valid appUsage observationCount triggers AI even when activity is unavailable or UNKNOWN`() = runBlocking {
        val validAppUsageFactual = FactualContextSnapshot(
            userId = userId,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            appUsage = com.sanket_satpute_20.ironmind.domain.model.observation.AppUsageObservationAggregation(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                observationCount = 2, totalDurationMs = 300_000L, uniquePackageCount = 1
            ),
            activity = com.sanket_satpute_20.ironmind.domain.model.observation.ActivityObservationAggregation(
                userId = userId, startTimeMs = 0L, endTimeMs = 1000L,
                observationCount = 3, distinctActivityStateCount = 1,
                perStateObservationCounts = mapOf("UNKNOWN" to 3),
                firstOccurrenceTimestamp = 100L, lastOccurrenceTimestamp = 900L
            ),
            calendar = null,
            location = null,
            notifications = null
        )
        val snapshot = createDummySnapshot(hasEvidence = false).copy(factualContextSnapshot = validAppUsageFactual)
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(snapshot)
        coEvery { mockAi.process(any()) } returns Result.Success(AIOutput.ModelEvolution(emptyList(), 0.8f))

        val useCase = EvolvePersonalModelUseCase(mockAi, mockContextEngine, mockPatternRepo, mockEvaluatePatternCandidatesUseCase)
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { mockAi.process(any()) }
    }

    @Test
    fun `snapshot user mismatch fails closed without invoking AI`() = runBlocking {
        val mismatchedSnapshot = createDummySnapshot(hasEvidence = true).let {
            it.copy(factualContextSnapshot = it.factualContextSnapshot.copy(userId = "other-user"))
        }
        coEvery { mockContextEngine.getCurrentContext(userId) } returns Result.Success(mismatchedSnapshot)

        val useCase = EvolvePersonalModelUseCase(mockAi, mockContextEngine, mockPatternRepo, mockEvaluatePatternCandidatesUseCase)
        val result = useCase(userId)

        assertTrue(result is Result.Failure)
        coVerify(exactly = 0) { mockAi.process(any()) }
    }
}
