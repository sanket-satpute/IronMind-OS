package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightJsonCodec
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 3.4.1: Insight Epistemic & Review Lifecycle Hardening Test Suite.
 *
 * Proves:
 * 1. Marking an insight useful does not confirm its underlying claim.
 * 2. Dismissing an insight does not refute its underlying claim.
 * 3. Review later does not alter epistemic authority.
 * 4. Explicit factual confirmation is a separate operation.
 * 5. Explicit factual correction/refutation is a separate operation.
 * 6. Rejecting a recommendation does not dismiss its insight.
 * 7. Dismissing an insight does not reject its recommendation.
 * 8. Insight and recommendation review states persist independently.
 * 9. Legacy insight JSON remains readable with safe fallback.
 */
class InsightEpistemicAndReviewLifecycleHardeningTest {

    private val userId = "user-hardening-test"
    private val now = 1700000000000L

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = now
    }

    private lateinit var fakeInsightRepository: InMemoryInsightRepository
    private lateinit var fakeRecommendationRepository: InMemoryRecommendationRepository
    private lateinit var reviewUseCase: ReviewPersonalInsightUseCase

    @Before
    fun setUp() {
        fakeInsightRepository = InMemoryInsightRepository()
        fakeRecommendationRepository = InMemoryRecommendationRepository()
        reviewUseCase = ReviewPersonalInsightUseCase(
            insightRepository = fakeInsightRepository,
            recommendationRepository = fakeRecommendationRepository,
            clock = fakeClock
        )
    }

    private fun createSampleInsight(
        id: String,
        epistemicAuthority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED,
        reviewStatus: InsightReviewStatus = InsightReviewStatus.UNREVIEWED,
        linkedRecId: String? = null
    ): PersonalInsight {
        return PersonalInsight(
            id = id,
            userId = userId,
            category = InsightCategory.PRODUCTIVITY,
            title = "Test Insight $id",
            explanation = "Sample explanation",
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Observed 3 commitments",
                observationPeriod = "Nov 01 - Nov 07",
                supportingEvidenceDescription = "Evidence 3 items",
                whyItMatters = "Execution awareness",
                uncertaintyOrLimitations = "Tentative observation",
                suggestedNextSteps = "Keep reviewing"
            ),
            supportingEvidenceIds = listOf("ev-1", "ev-2", "ev-3"),
            linkedRecommendationId = linkedRecId,
            observationWindowStartMs = now - 604800000L,
            observationWindowEndMs = now,
            firstObservedAt = now - 604800000L,
            lastObservedAt = now,
            confidence = 0.75f,
            epistemicAuthority = epistemicAuthority,
            sourceProvenance = listOf("Room:commitment"),
            sourceFreshness = SourceFreshness.CURRENT,
            reviewStatus = reviewStatus,
            confirmationState = confirmationState,
            createdAt = now,
            updatedAt = now
        )
    }

    // =========================================================================
    // Scenario 1: Marking insight useful does NOT confirm underlying claim
    // =========================================================================

    @Test
    fun test1_markingInsightUseful_doesNotConfirmUnderlyingClaim() = runBlocking {
        val insight = createSampleInsight(
            id = "ins-useful-1",
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            confirmationState = MemoryConfirmationState.UNCONFIRMED
        )
        fakeInsightRepository.saveInsight(insight)

        val result = reviewUseCase.markUseful(insight.id, userId)
        assertTrue(result is Result.Success)

        val updated = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertNotNull(updated)
        // Review status is USEFUL
        assertEquals(InsightReviewStatus.USEFUL, updated!!.reviewStatus)
        // Underlying epistemic authority remains UNCONFIRMED / AI_HYPOTHESIS
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, updated.epistemicAuthority)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, updated.confirmationState)
    }

    // =========================================================================
    // Scenario 2: Dismissing insight does NOT refute underlying claim
    // =========================================================================

    @Test
    fun test2_dismissingInsight_doesNotRefuteUnderlyingClaim() = runBlocking {
        val insight = createSampleInsight(
            id = "ins-dismiss-1",
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            confirmationState = MemoryConfirmationState.UNCONFIRMED
        )
        fakeInsightRepository.saveInsight(insight)

        val result = reviewUseCase.dismiss(insight.id, userId)
        assertTrue(result is Result.Success)

        val updated = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertNotNull(updated)
        // Review status is DISMISSED
        assertEquals(InsightReviewStatus.DISMISSED, updated!!.reviewStatus)
        // Underlying epistemic authority remains DERIVED_CALCULATION (NOT USER_REJECTED)
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, updated.epistemicAuthority)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, updated.confirmationState)
    }

    // =========================================================================
    // Scenario 3: Review later does NOT alter epistemic authority
    // =========================================================================

    @Test
    fun test3_reviewLater_doesNotAlterEpistemicAuthority() = runBlocking {
        val insight = createSampleInsight(
            id = "ins-later-1",
            epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
            confirmationState = MemoryConfirmationState.SYSTEM_CONFIRMED
        )
        fakeInsightRepository.saveInsight(insight)

        val result = reviewUseCase.reviewLater(insight.id, userId)
        assertTrue(result is Result.Success)

        val updated = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertNotNull(updated)
        assertEquals(InsightReviewStatus.REVIEW_LATER, updated!!.reviewStatus)
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, updated.epistemicAuthority)
        assertEquals(MemoryConfirmationState.SYSTEM_CONFIRMED, updated.confirmationState)
    }

    // =========================================================================
    // Scenario 4: Explicit factual confirmation is a separate operation
    // =========================================================================

    @Test
    fun test4_explicitFactualConfirmation_isSeparateOperation() = runBlocking {
        val insight = createSampleInsight(
            id = "ins-fact-confirm-1",
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            reviewStatus = InsightReviewStatus.UNREVIEWED
        )
        fakeInsightRepository.saveInsight(insight)

        // Calling markUseful alone does not confirm factual claim
        reviewUseCase.markUseful(insight.id, userId)
        var current = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertEquals(MemoryConfirmationState.UNCONFIRMED, current!!.confirmationState)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, current.epistemicAuthority)

        // Explicit factual confirmation operation
        val confirmResult = reviewUseCase.confirmFactualClaim(insight.id, userId)
        assertTrue(confirmResult is Result.Success)

        current = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertEquals(MemoryConfirmationState.USER_CONFIRMED, current!!.confirmationState)
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, current.epistemicAuthority)
        // Review status remains USEFUL (or independent)
        assertEquals(InsightReviewStatus.USEFUL, current.reviewStatus)
    }

    // =========================================================================
    // Scenario 5: Explicit factual correction/refutation is a separate operation
    // =========================================================================

    @Test
    fun test5_explicitFactualCorrectionRefutation_isSeparateOperation() = runBlocking {
        val insight = createSampleInsight(
            id = "ins-fact-refute-1",
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            reviewStatus = InsightReviewStatus.UNREVIEWED
        )
        fakeInsightRepository.saveInsight(insight)

        val correctionText = "This pattern does not apply because tasks were dictated by external deadlines."
        val refuteResult = reviewUseCase.refuteFactualClaim(insight.id, userId, correctionText)
        assertTrue(refuteResult is Result.Success)

        val current = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertNotNull(current)
        assertEquals(MemoryConfirmationState.USER_REJECTED, current!!.confirmationState)
        assertEquals(correctionText, current.userFeedback)
        // Epistemic authority is NOT elevated to fact
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, current.epistemicAuthority)
        // Review status is independent (remains UNREVIEWED)
        assertEquals(InsightReviewStatus.UNREVIEWED, current.reviewStatus)
    }

    // =========================================================================
    // Scenario 6: Rejecting a recommendation does NOT dismiss its insight
    // =========================================================================

    @Test
    fun test6_rejectingRecommendation_doesNotDismissInsight() = runBlocking {
        val rec = InterventionRecommendation(
            id = "rec-hardening-1",
            userId = userId,
            interventionType = InterventionType.BREAK_DOWN,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-1",
            targetEntityType = "TASK",
            rationale = "Task is large",
            suggestedAction = "Split task into two parts",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )
        fakeRecommendationRepository.saveRecommendation(rec)

        val insight = createSampleInsight(
            id = "ins-rec-reject-1",
            reviewStatus = InsightReviewStatus.UNREVIEWED,
            linkedRecId = rec.id
        )
        fakeInsightRepository.saveInsight(insight)

        // Reject recommendation
        val rejectResult = reviewUseCase.rejectLinkedRecommendation(insight.id, userId)
        assertTrue(rejectResult is Result.Success)

        // Recommendation is REJECTED
        val updatedRec = (fakeRecommendationRepository.getRecommendation(rec.id) as Result.Success).data
        assertEquals(InterventionRecommendationStatus.REJECTED, updatedRec!!.status)

        // Insight is NOT dismissed!
        val updatedInsight = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertEquals("Insight must NOT be dismissed when recommendation is rejected", InsightReviewStatus.UNREVIEWED, updatedInsight!!.reviewStatus)
    }

    // =========================================================================
    // Scenario 7: Dismissing an insight does NOT reject its recommendation
    // =========================================================================

    @Test
    fun test7_dismissingInsight_doesNotRejectRecommendation() = runBlocking {
        val rec = InterventionRecommendation(
            id = "rec-hardening-2",
            userId = userId,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-2",
            targetEntityType = "TASK",
            rationale = "Schedule friction",
            suggestedAction = "Move to tomorrow",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )
        fakeRecommendationRepository.saveRecommendation(rec)

        val insight = createSampleInsight(
            id = "ins-rec-dismiss-1",
            reviewStatus = InsightReviewStatus.UNREVIEWED,
            linkedRecId = rec.id
        )
        fakeInsightRepository.saveInsight(insight)

        // User dismisses the insight
        val dismissResult = reviewUseCase.dismiss(insight.id, userId)
        assertTrue(dismissResult is Result.Success)

        // Insight is DISMISSED
        val updatedInsight = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertEquals(InsightReviewStatus.DISMISSED, updatedInsight!!.reviewStatus)

        // Recommendation status is untouched and remains PENDING
        val updatedRec = (fakeRecommendationRepository.getRecommendation(rec.id) as Result.Success).data
        assertEquals("Recommendation must remain PENDING when insight is dismissed", InterventionRecommendationStatus.PENDING, updatedRec!!.status)
    }

    // =========================================================================
    // Scenario 8: Insight and recommendation review states persist independently
    // =========================================================================

    @Test
    fun test8_insightAndRecommendationReviewStates_persistIndependently() = runBlocking {
        val rec = InterventionRecommendation(
            id = "rec-hardening-3",
            userId = userId,
            interventionType = InterventionType.REFLECT,
            objective = InterventionObjective.EXTRACT_INSIGHT,
            targetEntityId = null,
            targetEntityType = null,
            rationale = "Daily reflection",
            suggestedAction = "Take 2 mins to reflect",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )
        fakeRecommendationRepository.saveRecommendation(rec)

        val insight = createSampleInsight(
            id = "ins-independent-1",
            reviewStatus = InsightReviewStatus.UNREVIEWED,
            linkedRecId = rec.id
        )
        fakeInsightRepository.saveInsight(insight)

        // User finds insight useful
        reviewUseCase.markUseful(insight.id, userId)

        // User rejects recommendation
        reviewUseCase.rejectLinkedRecommendation(insight.id, userId)

        // Verify independent states
        val updatedInsight = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        val updatedRec = (fakeRecommendationRepository.getRecommendation(rec.id) as Result.Success).data

        assertEquals("Insight must remain USEFUL", InsightReviewStatus.USEFUL, updatedInsight!!.reviewStatus)
        assertEquals("Recommendation must be REJECTED", InterventionRecommendationStatus.REJECTED, updatedRec!!.status)
    }

    // =========================================================================
    // Scenario 9: Legacy insight JSON remains readable with safe fallback
    // =========================================================================

    @Test
    fun test9_legacyInsightJson_remainsReadable() {
        // Legacy JSON without "confirmationState" property
        val legacyJson = """
            {
                "v": 1,
                "category": "PRODUCTIVITY",
                "title": "Legacy Insight",
                "explanation": "Legacy insight without confirmationState field",
                "explainability": {
                    "observed": "Observed 5 commitments",
                    "period": "Last 7 days",
                    "evidence": "Commitment records",
                    "relevance": "Productivity tempo",
                    "uncertainty": "None",
                    "nextSteps": "Keep pace"
                },
                "patterns": ["pat-1"],
                "barriers": [],
                "evidenceIds": ["ev-1", "ev-2"],
                "winStart": 1699000000000,
                "winEnd": 1700000000000,
                "firstObserved": 1699000000000,
                "lastObserved": 1700000000000,
                "conf": 0.82,
                "auth": "DERIVED_CALCULATION",
                "provenance": ["Room:commitment"],
                "freshness": "CURRENT",
                "reviewStatus": "USEFUL",
                "createdAt": 1700000000000,
                "updatedAt": 1700000000000
            }
        """.trimIndent()

        val decoded = InsightJsonCodec.decodeFromJson(
            id = "ins-legacy-1",
            userId = userId,
            jsonString = legacyJson
        )

        assertNotNull("Legacy JSON must be safely decoded without errors", decoded)
        assertEquals("Legacy Insight", decoded!!.title)
        assertEquals(InsightReviewStatus.USEFUL, decoded.reviewStatus)
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, decoded.epistemicAuthority)
        // Safe fallback: confirmationState is UNCONFIRMED, never erroneously promoted to USER_CONFIRMED!
        assertEquals(MemoryConfirmationState.UNCONFIRMED, decoded.confirmationState)
        assertEquals(1, decoded.supportingPatternIds.size)
        assertEquals(2, decoded.supportingEvidenceIds.size)
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    class InMemoryInsightRepository : InsightRepository {
        private val insights = mutableMapOf<String, PersonalInsight>()
        private val flow = MutableStateFlow<List<PersonalInsight>>(emptyList())

        override suspend fun saveInsight(insight: PersonalInsight): Result<PersonalInsight, Exception> {
            insights[insight.id] = insight
            flow.value = insights.values.toList()
            return Result.Success(insight)
        }

        override suspend fun getInsightById(id: String): Result<PersonalInsight?, Exception> {
            return Result.Success(insights[id])
        }

        override suspend fun getInsightsForUser(userId: String): Result<List<PersonalInsight>, Exception> {
            return Result.Success(insights.values.filter { it.userId == userId })
        }

        override fun observeInsightsForUser(userId: String): Flow<List<PersonalInsight>> {
            return flow.map { list -> list.filter { it.userId == userId } }
        }

        override suspend fun updateReviewStatus(
            id: String,
            userId: String,
            status: InsightReviewStatus,
            feedback: String?,
            reviewedAt: Long
        ): Result<Unit, Exception> {
            val existing = insights[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            if (existing.userId != userId) {
                return Result.Failure(SecurityException("User mismatch"))
            }
            val updated = existing.copy(
                reviewStatus = status,
                userFeedback = feedback ?: existing.userFeedback,
                reviewedAt = reviewedAt,
                updatedAt = reviewedAt
            )
            insights[id] = updated
            flow.value = insights.values.toList()
            return Result.Success(Unit)
        }

        override suspend fun updateFactualConfirmation(
            id: String,
            userId: String,
            confirmationState: MemoryConfirmationState,
            correction: String?,
            timestamp: Long
        ): Result<Unit, Exception> {
            val existing = insights[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            if (existing.userId != userId) {
                return Result.Failure(SecurityException("User mismatch"))
            }
            val newAuthority = if (confirmationState == MemoryConfirmationState.USER_CONFIRMED) {
                EpistemicAuthority.USER_CONFIRMED_FACT
            } else {
                existing.epistemicAuthority
            }
            val updated = existing.copy(
                confirmationState = confirmationState,
                epistemicAuthority = newAuthority,
                userFeedback = correction ?: existing.userFeedback,
                updatedAt = timestamp
            )
            insights[id] = updated
            flow.value = insights.values.toList()
            return Result.Success(Unit)
        }
    }

    class InMemoryRecommendationRepository : InterventionRecommendationRepository {
        val recommendations = mutableListOf<InterventionRecommendation>()

        override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
            recommendations.removeAll { it.id == recommendation.id }
            recommendations.add(recommendation)
            return Result.Success(Unit)
        }

        override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
            return Result.Success(recommendations.firstOrNull { it.id == id })
        }

        override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
            return MutableStateFlow(recommendations.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING }).asStateFlow()
        }

        override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
            val idx = recommendations.indexOfFirst { it.id == id }
            if (idx >= 0) {
                recommendations[idx] = recommendations[idx].copy(status = status)
            }
            return Result.Success(Unit)
        }

        override suspend fun getEquivalentRecommendations(
            userId: String,
            objective: String,
            targetEntityType: String?,
            targetEntityId: String?
        ): Result<List<InterventionRecommendation>, Exception> {
            return Result.Success(recommendations.filter {
                it.userId == userId &&
                        it.objective.name == objective &&
                        it.targetEntityType == targetEntityType &&
                        it.targetEntityId == targetEntityId
            })
        }

        override suspend fun getRecommendationsForUser(
            userId: String,
            startTime: Long,
            endTime: Long,
            limit: Int
        ): Result<List<InterventionRecommendation>, Exception> {
            return Result.Success(recommendations.filter { it.userId == userId && it.createdAt in startTime..endTime }.take(limit))
        }
    }
}

