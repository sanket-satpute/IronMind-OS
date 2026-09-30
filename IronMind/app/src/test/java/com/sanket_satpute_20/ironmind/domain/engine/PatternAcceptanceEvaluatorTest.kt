package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PatternAcceptanceEvaluatorTest {

    private val evaluator = PatternAcceptanceEvaluator(EvidenceSufficiencyEvaluator())
    private val zoneId = ZoneId.of("UTC")
    private val userId = "user1"
    private val currentTime = 1000L

    @Test
    fun `test new pattern with sufficient evidence returns AcceptedNew`() {
        val candidate = PatternCandidate(
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Delays workout",
            conditions = "evening",
            predictedBehavior = "skips workout"
        )
        
        val evidence = listOf(
            ValidatedEvidence(EvidenceReference("obs1", EvidenceSourceType.OBSERVATION), 0L),
            ValidatedEvidence(EvidenceReference("obs2", EvidenceSourceType.OBSERVATION), 1000L * 60 * 60 * 24 * 1), // 1 day later
            ValidatedEvidence(EvidenceReference("obs3", EvidenceSourceType.OBSERVATION), 1000L * 60 * 60 * 24 * 2)  // 2 days later
        )

        val result = evaluator.evaluate(
            candidate = candidate,
            evidence = evidence,
            existingPattern = null,
            newPatternId = "pattern1",
            currentTimeMillis = currentTime,
            zoneId = zoneId,
            userId = userId
        )

        assertTrue(result is PatternAcceptanceResult.AcceptedNew)
        val acceptedPattern = (result as PatternAcceptanceResult.AcceptedNew).pattern
        assertEquals("pattern1", acceptedPattern.id)
        assertEquals(0.8f, acceptedPattern.confidence, 0.001f)
        assertEquals(PatternStatus.ACTIVE, acceptedPattern.status)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, acceptedPattern.confirmationState)
        assertEquals(3, acceptedPattern.evidenceCount)
        assertEquals(0L, acceptedPattern.firstObservedAt)
    }

    @Test
    fun `test new pattern with insufficient evidence returns Rejected`() {
        val candidate = PatternCandidate(
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Delays workout"
        )
        // Only 2 pieces of evidence
        val evidence = listOf(
            ValidatedEvidence(EvidenceReference("obs1", EvidenceSourceType.OBSERVATION), 0L),
            ValidatedEvidence(EvidenceReference("obs2", EvidenceSourceType.OBSERVATION), 1000L * 60 * 60 * 24 * 1)
        )

        val result = evaluator.evaluate(candidate, evidence, null, "p1", currentTime, zoneId, userId)
        assertTrue(result is PatternAcceptanceResult.Rejected)
    }

    @Test
    fun `test invalid candidate returns Rejected`() {
        val candidate = PatternCandidate(
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "   " // Blank description
        )
        val result = evaluator.evaluate(candidate, emptyList(), null, "p1", currentTime, zoneId, userId)
        assertTrue(result is PatternAcceptanceResult.Rejected)
    }

    @Test
    fun `test existing pattern with no new evidence returns NoOp`() {
        val candidate = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, "desc")
        val evidenceRef = EvidenceReference("obs1", EvidenceSourceType.OBSERVATION)
        
        val existingPattern = Pattern(
            id = "p1", userId = userId, fingerprint = "fp", type = PatternType.POSTPONEMENT_PATTERN, description = "desc",
            conditions = null, predictedBehavior = null, confidence = 0.8f, evidenceCount = 1,
            evidenceReferences = listOf(evidenceRef), firstObservedAt = 0L, lastObservedAt = 0L,
            status = PatternStatus.ACTIVE, confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 0L, updatedAt = 0L
        )

        val evidence = listOf(ValidatedEvidence(evidenceRef, 0L))

        val result = evaluator.evaluate(candidate, evidence, existingPattern, "p2", currentTime, zoneId, userId)
        assertTrue(result is PatternAcceptanceResult.NoOp)
    }

    @Test
    fun `test existing pattern with new evidence returns UpdatedExisting`() {
        val candidate = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, "desc")
        val oldRef = EvidenceReference("obs1", EvidenceSourceType.OBSERVATION)
        val newRef = EvidenceReference("obs2", EvidenceSourceType.OBSERVATION)
        
        val existingPattern = Pattern(
            id = "p1", userId = userId, fingerprint = "fp", type = PatternType.POSTPONEMENT_PATTERN, description = "desc",
            conditions = null, predictedBehavior = null, confidence = 0.5f, evidenceCount = 1,
            evidenceReferences = listOf(oldRef), firstObservedAt = 0L, lastObservedAt = 0L,
            status = PatternStatus.ACTIVE, confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 0L, updatedAt = 0L
        )

        val evidence = listOf(
            ValidatedEvidence(oldRef, 0L),
            ValidatedEvidence(newRef, 1000L) // new evidence
        )

        val result = evaluator.evaluate(candidate, evidence, existingPattern, "p2", currentTime, zoneId, userId)
        assertTrue(result is PatternAcceptanceResult.UpdatedExisting)
        val updated = (result as PatternAcceptanceResult.UpdatedExisting).pattern
        assertEquals(2, updated.evidenceCount)
        assertEquals(0.8f, updated.confidence, 0.001f) // max(0.5, 0.8)
        assertEquals(1000L, updated.lastObservedAt)
        assertEquals(currentTime, updated.updatedAt)
    }

    @Test
    fun `test expired pattern revives to active on update`() {
        val candidate = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, "desc")
        val oldRef = EvidenceReference("obs1", EvidenceSourceType.OBSERVATION)
        val newRef = EvidenceReference("obs2", EvidenceSourceType.OBSERVATION)
        
        val existingPattern = Pattern(
            id = "p1", userId = userId, fingerprint = "fp", type = PatternType.POSTPONEMENT_PATTERN, description = "desc",
            conditions = null, predictedBehavior = null, confidence = 0.2f, evidenceCount = 1,
            evidenceReferences = listOf(oldRef), firstObservedAt = 0L, lastObservedAt = 0L,
            status = PatternStatus.EXPIRED, confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 0L, updatedAt = 0L
        )

        val evidence = listOf(ValidatedEvidence(newRef, 1000L))

        val result = evaluator.evaluate(candidate, evidence, existingPattern, "p2", currentTime, zoneId, userId)
        assertTrue(result is PatternAcceptanceResult.UpdatedExisting)
        val updated = (result as PatternAcceptanceResult.UpdatedExisting).pattern
        assertEquals(PatternStatus.ACTIVE, updated.status)
        assertEquals(0.8f, updated.confidence, 0.001f) // max(0.2, 0.8)
    }

    @Test
    fun `test deleted pattern is treated as new and requires full sufficiency`() {
        val candidate = PatternCandidate(PatternType.POSTPONEMENT_PATTERN, "desc")
        val oldRef = EvidenceReference("obs1", EvidenceSourceType.OBSERVATION)
        
        val existingPattern = Pattern(
            id = "p1", userId = userId, fingerprint = "fp", type = PatternType.POSTPONEMENT_PATTERN, description = "desc",
            conditions = null, predictedBehavior = null, confidence = 0.0f, evidenceCount = 1,
            evidenceReferences = listOf(oldRef), firstObservedAt = 0L, lastObservedAt = 0L,
            status = PatternStatus.DELETED, confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 0L, updatedAt = 0L
        )

        // Only 1 new evidence piece (insufficient for a NEW pattern)
        val evidence = listOf(ValidatedEvidence(EvidenceReference("obs2", EvidenceSourceType.OBSERVATION), 1000L))

        val result = evaluator.evaluate(candidate, evidence, existingPattern, "p2", currentTime, zoneId, userId)
        
        // Since DELETED is treated as NEW, and 1 evidence is insufficient, it should reject
        assertTrue(result is PatternAcceptanceResult.Rejected)
    }
}
