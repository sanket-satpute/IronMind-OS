package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionEquivalencePolicyTest {

    private val policy = InterventionEquivalencePolicy()

    private fun createCandidate(
        objective: InterventionObjective = InterventionObjective.INITIATE_ACTION,
        targetEntityType: String? = "GOAL",
        targetEntityId: String? = "goal-1",
        interventionType: InterventionType = InterventionType.BREAK_DOWN
    ) = InterventionRecommendationCandidate(
        interventionType = interventionType,
        objective = objective,
        rationale = "Test rationale",
        suggestedAction = "Test action",
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        confidence = 0.9f
    )

    private fun createHistory(
        objective: InterventionObjective? = InterventionObjective.INITIATE_ACTION,
        targetEntityType: String? = "GOAL",
        targetEntityId: String? = "goal-1",
        interventionType: InterventionType = InterventionType.BREAK_DOWN,
        status: InterventionRecommendationStatus = InterventionRecommendationStatus.PENDING,
        createdAt: Long = 0L
    ) = InterventionRecommendation(
        id = "hist-1",
        userId = "user-1",
        interventionType = interventionType,
        objective = objective ?: InterventionObjective.INITIATE_ACTION, // Mocked for null test manually later
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        rationale = "History rationale",
        suggestedAction = "History action",
        status = status,
        createdAt = createdAt
    )

    // A. Equivalent recommendations
    @Test
    fun `isEquivalent returns true for same objective and target`() {
        val candidate = createCandidate()
        val history = createHistory()
        assertTrue(policy.isEquivalent(candidate, history))
    }

    // B. Different objective
    @Test
    fun `isEquivalent returns false for different objective`() {
        val candidate = createCandidate(objective = InterventionObjective.INITIATE_ACTION)
        val history = createHistory(objective = InterventionObjective.REDUCE_FRICTION)
        assertFalse(policy.isEquivalent(candidate, history))
    }

    // C. Different targetEntityId
    @Test
    fun `isEquivalent returns false for different targetEntityId`() {
        val candidate = createCandidate(targetEntityId = "goal-1")
        val history = createHistory(targetEntityId = "goal-2")
        assertFalse(policy.isEquivalent(candidate, history))
    }

    // D. Different targetEntityType
    @Test
    fun `isEquivalent returns false for different targetEntityType`() {
        val candidate = createCandidate(targetEntityType = "GOAL")
        val history = createHistory(targetEntityType = "COMMITMENT")
        assertFalse(policy.isEquivalent(candidate, history))
    }

    // E. Same semantic identity but different interventionType
    @Test
    fun `isEquivalent returns true even if interventionType differs`() {
        val candidate = createCandidate(interventionType = InterventionType.BREAK_DOWN)
        val history = createHistory(interventionType = InterventionType.REASSURE)
        assertTrue(policy.isEquivalent(candidate, history))
    }

    // F. Null objective on either side (Note: candidate cannot have null due to type system, but history could if we bypassed the mock, wait history is also non-null in domain. Wait, the domain model enforces non-null. However, the rule says "Historical recommendations with objective == null MUST NOT participate". But InterventionRecommendation.objective is non-null! Wait, InterventionRecommendationEntity can have null, but the Mapper returns null for the entire InterventionRecommendation. So the domain object WILL NEVER have a null objective. The rule is effectively handled by the Mapper! Still, we can test that the policy evaluates correctly if it could happen. Since the type system forbids it, we can just skip or add a comment).

    // G. Existing equivalent PENDING => suppressed
    @Test
    fun `evaluateSuppression suppresses candidate if equivalent is PENDING`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.PENDING)
        val result = policy.evaluateSuppression(candidate, listOf(history), false, 1000L)
        assertEquals(SuppressionResult.SuppressedAntiStacking, result)
    }

    // H. Existing REJECTED within 24h => suppressed
    @Test
    fun `evaluateSuppression suppresses candidate if REJECTED within 24h`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        val currentTime = 1000L + InterventionEquivalencePolicy.REJECTED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(history), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentRejection, result)
    }

    // I. Existing REJECTED exactly at 24h boundary => allowed
    @Test
    fun `evaluateSuppression allows candidate if REJECTED exactly at 24h boundary`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        val currentTime = 1000L + InterventionEquivalencePolicy.REJECTED_COOLDOWN_MS
        val result = policy.evaluateSuppression(candidate, listOf(history), false, currentTime)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // J. Existing REJECTED after 24h => allowed
    @Test
    fun `evaluateSuppression allows candidate if REJECTED after 24h`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        val currentTime = 1000L + InterventionEquivalencePolicy.REJECTED_COOLDOWN_MS + 1L
        val result = policy.evaluateSuppression(candidate, listOf(history), false, currentTime)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // K. Existing IGNORED within 6h => suppressed
    @Test
    fun `evaluateSuppression suppresses candidate if IGNORED within 6h`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 1000L)
        val currentTime = 1000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(history), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentIgnored, result)
    }

    // L. Existing IGNORED exactly at 6h boundary => allowed
    @Test
    fun `evaluateSuppression allows candidate if IGNORED exactly at 6h boundary`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 1000L)
        val currentTime = 1000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS
        val result = policy.evaluateSuppression(candidate, listOf(history), false, currentTime)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // M. Existing IGNORED after 6h => allowed
    @Test
    fun `evaluateSuppression allows candidate if IGNORED after 6h`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 1000L)
        val currentTime = 1000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS + 1L
        val result = policy.evaluateSuppression(candidate, listOf(history), false, currentTime)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // N. Existing ACCEPTED => allowed
    @Test
    fun `evaluateSuppression allows candidate if ACCEPTED`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.ACCEPTED)
        val result = policy.evaluateSuppression(candidate, listOf(history), false, 1000L)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // O. Existing EXPIRED => allowed
    @Test
    fun `evaluateSuppression allows candidate if EXPIRED`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.EXPIRED)
        val result = policy.evaluateSuppression(candidate, listOf(history), false, 1000L)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // P. Target completed => suppressed
    @Test
    fun `evaluateSuppression suppresses candidate if target is completed`() {
        val candidate = createCandidate()
        val result = policy.evaluateSuppression(candidate, emptyList(), isTargetCompleted = true, currentTimeMs = 1000L)
        assertEquals(SuppressionResult.SuppressedTargetCompleted, result)
    }

    // Q. Target not completed => continue normal policy evaluation
    @Test
    fun `evaluateSuppression continues if target not completed`() {
        val candidate = createCandidate()
        val history = createHistory(status = InterventionRecommendationStatus.PENDING)
        val result = policy.evaluateSuppression(candidate, listOf(history), isTargetCompleted = false, currentTimeMs = 1000L)
        assertEquals(SuppressionResult.SuppressedAntiStacking, result) // proves it continued evaluation
    }

    // R. Multiple historical recommendations with same status => homogeneous behavior
    @Test
    fun `evaluateSuppression preserves existing behavior for multiple same statuses`() {
        val candidate = createCandidate()
        val hist1 = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        val hist2 = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 2000L)
        
        // At T = 2000L + 24h - 1, the newer one suppresses, the older one would have expired
        val currentTime = 2000L + InterventionEquivalencePolicy.REJECTED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(hist1, hist2), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentRejection, result)
    }

    // 1. ACCEPTED then REJECTED -> REJECTED effective -> 24h suppression
    @Test
    fun `evaluateSuppression ACCEPTED then REJECTED results in REJECTED suppression`() {
        val candidate = createCandidate()
        val histOld = createHistory(status = InterventionRecommendationStatus.ACCEPTED, createdAt = 1000L)
        val histNew = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 2000L)
        
        val currentTime = 2000L + InterventionEquivalencePolicy.REJECTED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(histOld, histNew), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentRejection, result)
    }

    // 2. REJECTED then ACCEPTED -> ACCEPTED effective -> no suppression
    @Test
    fun `evaluateSuppression REJECTED then ACCEPTED results in Allowed`() {
        val candidate = createCandidate()
        val histOld = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        val histNew = createHistory(status = InterventionRecommendationStatus.ACCEPTED, createdAt = 2000L)
        
        // Even though we are within 24h of the old rejection, the new ACCEPTED overrides it
        val currentTime = 1000L + 1000L 
        val result = policy.evaluateSuppression(candidate, listOf(histOld, histNew), false, currentTime)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // 3. REJECTED then IGNORED -> IGNORED effective -> 6h suppression
    @Test
    fun `evaluateSuppression REJECTED then IGNORED results in IGNORED suppression`() {
        val candidate = createCandidate()
        val histOld = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        val histNew = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 2000L)
        
        // Within 6h of IGNORED
        val currentTime = 2000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(histOld, histNew), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentIgnored, result)
    }

    // 4. IGNORED then REJECTED -> REJECTED effective -> 24h suppression
    @Test
    fun `evaluateSuppression IGNORED then REJECTED results in REJECTED suppression`() {
        val candidate = createCandidate()
        val histOld = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 1000L)
        val histNew = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 2000L)
        
        // Within 24h of REJECTED, but past 6h of IGNORED
        val currentTime = 2000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS + 1000L
        val result = policy.evaluateSuppression(candidate, listOf(histOld, histNew), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentRejection, result)
    }

    // 5. ACCEPTED then IGNORED -> IGNORED effective -> 6h suppression
    @Test
    fun `evaluateSuppression ACCEPTED then IGNORED results in IGNORED suppression`() {
        val candidate = createCandidate()
        val histOld = createHistory(status = InterventionRecommendationStatus.ACCEPTED, createdAt = 1000L)
        val histNew = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 2000L)
        
        val currentTime = 2000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(histOld, histNew), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentIgnored, result)
    }

    // 7. More than two equivalent records with mixed statuses
    @Test
    fun `evaluateSuppression multiple mixed statuses uses newest record`() {
        val candidate = createCandidate()
        val hist1 = createHistory(status = InterventionRecommendationStatus.ACCEPTED, createdAt = 1000L)
        val hist2 = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 2000L)
        val hist3 = createHistory(status = InterventionRecommendationStatus.IGNORED, createdAt = 3000L)
        
        // Newest is IGNORED
        val currentTime = 3000L + InterventionEquivalencePolicy.IGNORED_COOLDOWN_MS - 1L
        val result = policy.evaluateSuppression(candidate, listOf(hist1, hist2, hist3), false, currentTime)
        assertEquals(SuppressionResult.SuppressedRecentIgnored, result)
    }

    // 10. Different semantic identities must remain independent
    @Test
    fun `evaluateSuppression different semantic identities remain independent`() {
        val candidate = createCandidate(objective = InterventionObjective.INITIATE_ACTION)
        val histOther = createHistory(objective = InterventionObjective.REDUCE_FRICTION, status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        
        // Unrelated rejection does not suppress candidate
        val result = policy.evaluateSuppression(candidate, listOf(histOther), false, 1000L)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // 11. Non-equivalent target IDs must remain independent
    @Test
    fun `evaluateSuppression different target IDs remain independent`() {
        val candidate = createCandidate(targetEntityId = "goal-1")
        val histOther = createHistory(targetEntityId = "goal-2", status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        
        val result = policy.evaluateSuppression(candidate, listOf(histOther), false, 1000L)
        assertEquals(SuppressionResult.Allowed, result)
    }

    // 13. Equal-createdAt behavior
    @Test
    fun `evaluateSuppression equal createdAt values with differing statuses returns UnresolvedPrecedence`() {
        val candidate = createCandidate()
        // Two conflicting records at exact same timestamp
        val hist1 = createHistory(status = InterventionRecommendationStatus.ACCEPTED, createdAt = 1000L)
        val hist2 = createHistory(status = InterventionRecommendationStatus.REJECTED, createdAt = 1000L)
        
        val result = policy.evaluateSuppression(candidate, listOf(hist1, hist2), false, 1000L)
        assertEquals(SuppressionResult.UnresolvedPrecedence, result)
    }

    // S. InterventionType changes while objective + target identical => still suppressed
    @Test
    fun `evaluateSuppression suppresses based on equivalence despite differing InterventionType`() {
        val candidate = createCandidate(interventionType = InterventionType.REASSURE)
        val history = createHistory(interventionType = InterventionType.BREAK_DOWN, status = InterventionRecommendationStatus.PENDING, createdAt = 1000L)
        
        val result = policy.evaluateSuppression(candidate, listOf(history), false, 1000L)
        assertEquals(SuppressionResult.SuppressedAntiStacking, result)
    }
}
