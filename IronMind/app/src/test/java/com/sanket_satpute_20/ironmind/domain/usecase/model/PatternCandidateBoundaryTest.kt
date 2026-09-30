package com.sanket_satpute_20.ironmind.domain.usecase.model

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternCandidateBoundaryTest {

    @Test
    fun `Test 1 - AIOutput ModelEvolution contains List of PatternCandidate`() {
        val candidates = listOf(
            PatternCandidate(
                type = PatternType.CONTEXT_PATTERN,
                description = "User postpones tasks on Fridays",
                contradictionSignal = null
            )
        )
        
        val output = AIOutput.ModelEvolution(
            evolvedCandidates = candidates,
            confidence = 0.8f
        )
        
        assertEquals(1, output.evolvedCandidates.size)
        assertEquals("User postpones tasks on Fridays", output.evolvedCandidates.first().description)
    }

    @Test
    fun `Test 2 - Candidate contains only AI-owned semantics`() {
        val candidate = PatternCandidate(
            type = PatternType.TIME_PATTERN,
            description = "Candidate has no confidence or timestamps"
            // No confidence, status, confirmation, timestamps allowed in constructor
        )
        assertNotNull(candidate)
        assertNull(candidate.contradictionSignal)
        assertNull(candidate.conditions)
    }

    @Test
    fun `Test 4 and 5 - Candidate does not silently persist and contradiction is non-authoritative`() = runBlocking {
        val fakeRepo = FakePatternRepository()
        val fakeEngine = FakeContextEngine()
        val fakeAi = FakeBoundaryAI(
            AIOutput.ModelEvolution(
                evolvedCandidates = listOf(
                    PatternCandidate(
                        type = PatternType.CONTEXT_PATTERN,
                        description = "New pattern candidate",
                        contradictionSignal = "old-pattern-123"
                    )
                ),
                confidence = 0.9f
            )
        )
        
        val useCase = EvolvePersonalModelUseCase(fakeAi, fakeEngine, fakeRepo)
        
        val result = useCase("user1")
        assertTrue(result is Result.Success)
        
        // Assert that the repository is completely untouched.
        // The candidate must NOT be saved as a Pattern without domain validation.
        // The contradictionSignal must NOT automatically mutate or expire a Pattern.
        assertEquals(0, fakeRepo.savedPatterns.size)
    }
}

class FakePatternRepository : PatternRepository {
    val savedPatterns = mutableListOf<Pattern>()
    
    override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> {
        savedPatterns.add(pattern)
        return Result.Success(Unit)
    }

    override suspend fun updatePattern(pattern: Pattern): Result<Unit, Exception> {
        val index = savedPatterns.indexOfFirst { it.id == pattern.id }
        if (index != -1) savedPatterns[index] = pattern else savedPatterns.add(pattern)
        return Result.Success(Unit)
    }

    override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception> {
        return Result.Success(savedPatterns.find { it.fingerprint == fingerprint && it.userId == userId })
    }

    override suspend fun getPattern(id: String): Result<Pattern?, Exception> {
        return Result.Success(null)
    }

    override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> {
        return Result.Success(emptyList())
    }

    override suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception> {
        return Result.Success(emptyList())
    }

    override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> {
        return Result.Success(emptyList())
    }

    override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> {
        return Result.Success(Unit)
    }

    override suspend fun deletePattern(id: String): Result<Unit, Exception> {
        return Result.Success(Unit)
    }
}

class FakeContextEngine : ContextEngine {
    override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> {
        val factualSnapshot = com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot(
            userId = userId,
            startTimeMs = 0L,
            endTimeMs = 0L,
            appUsage = null,
            activity = null,
            calendar = null,
            location = null,
            notifications = null
        )
        return Result.Success(
            ContextSnapshot(
                timestamp = System.currentTimeMillis(),
                dayOfWeek = 1,
                activeCommitments = emptyList(),
                recentEvents = emptyList(),
                factualContextSnapshot = factualSnapshot,
                recentReflections = emptyList(),
                recentPatterns = emptyList(),
                activeProtectionSession = null,
                activeGoals = emptyList()
            )
        )
    }
}

class FakeBoundaryAI(val response: AIOutput) : IronMindAI {
    override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
        return Result.Success(response)
    }
}
