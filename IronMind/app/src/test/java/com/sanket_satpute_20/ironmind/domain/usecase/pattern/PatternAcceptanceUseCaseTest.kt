package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.*
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.*
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class PatternAcceptanceUseCaseTest {

    private val zoneId = ZoneId.of("UTC")
    private val userId = "user1"

    @Test
    fun `test concurrent acceptance correctly uses Mutex to serialize evaluate and persist`() = runBlocking {
        val mockRepo = object : PatternRepository {
            var savedPattern: Pattern? = null
            var saveCount = AtomicInteger(0)
            var updateCount = AtomicInteger(0)
            var getCount = AtomicInteger(0)

            override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception> {
                getCount.incrementAndGet()
                // Simulate some delay for concurrent racing
                delay(50)
                return Result.Success(savedPattern)
            }

            override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> {
                saveCount.incrementAndGet()
                savedPattern = pattern
                delay(50)
                return Result.Success(Unit)
            }

            override suspend fun updatePattern(pattern: Pattern): Result<Unit, Exception> {
                updateCount.incrementAndGet()
                savedPattern = pattern
                delay(50)
                return Result.Success(Unit)
            }

            override suspend fun getPattern(id: String) = Result.Success(null)
            override suspend fun getPatternsForUser(userId: String) = Result.Success(emptyList<Pattern>())
            override suspend fun getPatternsByType(userId: String, type: PatternType) = Result.Success(emptyList<Pattern>())
            override suspend fun getPatternsByStatus(userId: String, status: PatternStatus) = Result.Success(emptyList<Pattern>())
            override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long) = Result.Success(Unit)
            override suspend fun deletePattern(id: String) = Result.Success(Unit)
        }

        val sufficiencyEvaluator = EvidenceSufficiencyEvaluator()
        val evaluator = PatternAcceptanceEvaluator(sufficiencyEvaluator)
        val useCase = PatternAcceptanceUseCase(evaluator, mockRepo, idGenerator = { "fixedId" })

        val candidate = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, "desc")
        val evidence1 = listOf(
            ValidatedEvidence(EvidenceReference("obs1", EvidenceSourceType.OBSERVATION), 0L),
            ValidatedEvidence(EvidenceReference("obs2", EvidenceSourceType.OBSERVATION), 86400000L),
            ValidatedEvidence(EvidenceReference("obs3", EvidenceSourceType.OBSERVATION), 86400000L * 2)
        )
        val evidence2 = listOf(ValidatedEvidence(EvidenceReference("obs4", EvidenceSourceType.OBSERVATION), 1000L))

        // Launch two concurrent acceptance requests for the same fingerprint
        val job1 = launch {
            useCase(userId, candidate, evidence1, 0L, zoneId)
        }
        val job2 = launch {
            useCase(userId, candidate, evidence2, 0L, zoneId)
        }

        job1.join()
        job2.join()

        // Because of Mutex:
        // Request 1 finds null, evaluates NEW, saves Pattern.
        // Request 2 finds the saved Pattern, evaluates EXISTING, updates Pattern (merging evidence).
        assertEquals(2, mockRepo.getCount.get())
        assertEquals(1, mockRepo.saveCount.get())
        assertEquals(1, mockRepo.updateCount.get())

        // The final pattern should have evidence from both requests (merged)
        val finalPattern = mockRepo.savedPattern
        assertEquals(4, finalPattern?.evidenceCount)
    }

    @Test
    fun `test fingerprint user isolation`() = runBlocking {
        val mockRepo = object : PatternRepository {
            var getCalledWithUserId: String? = null
            var getCalledWithFingerprint: String? = null

            override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception> {
                getCalledWithUserId = userId
                getCalledWithFingerprint = fingerprint
                return Result.Success(null)
            }
            override suspend fun savePattern(pattern: Pattern) = Result.Success(Unit)
            override suspend fun updatePattern(pattern: Pattern) = Result.Success(Unit)
            override suspend fun getPattern(id: String) = Result.Success(null)
            override suspend fun getPatternsForUser(userId: String) = Result.Success(emptyList<Pattern>())
            override suspend fun getPatternsByType(userId: String, type: PatternType) = Result.Success(emptyList<Pattern>())
            override suspend fun getPatternsByStatus(userId: String, status: PatternStatus) = Result.Success(emptyList<Pattern>())
            override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long) = Result.Success(Unit)
            override suspend fun deletePattern(id: String) = Result.Success(Unit)
        }
        val evaluator = PatternAcceptanceEvaluator(EvidenceSufficiencyEvaluator())
        val useCase = PatternAcceptanceUseCase(evaluator, mockRepo)

        // 1. User 1 with " DESC "
        val candidate1 = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, " DESC ")
        useCase("user1", candidate1, emptyList(), 0L, zoneId)

        assertEquals("user1", mockRepo.getCalledWithUserId)
        assertEquals("user1_POSTPONEMENT_PATTERN_desc", mockRepo.getCalledWithFingerprint)

        // 2. User 1 with "desc" (same normalized identity => same fingerprint)
        val candidate2 = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, "desc")
        useCase("user1", candidate2, emptyList(), 0L, zoneId)
        assertEquals("user1_POSTPONEMENT_PATTERN_desc", mockRepo.getCalledWithFingerprint)

        // 3. User 2 with identical candidate => different fingerprint
        useCase("user2", candidate1, emptyList(), 0L, zoneId)
        assertEquals("user2", mockRepo.getCalledWithUserId)
        assertEquals("user2_POSTPONEMENT_PATTERN_desc", mockRepo.getCalledWithFingerprint)
    }
}
