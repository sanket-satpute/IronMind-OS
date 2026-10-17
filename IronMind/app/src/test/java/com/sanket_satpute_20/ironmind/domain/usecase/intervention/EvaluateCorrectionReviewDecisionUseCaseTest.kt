package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.execution.background.DefaultEventProcessor
import com.sanket_satpute_20.ironmind.execution.background.EventProcessingOutcome
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvaluateCorrectionReviewDecisionUseCaseTest {

    private lateinit var useCase: EvaluateCorrectionReviewDecisionUseCase

    private val testUser = "user-123"
    private val testEventId = "evt-456"
    private val testRecId = "rec-789"
    private val testPatternHypothesis = CausalHypothesisReference("pat-101", CausalHypothesisType.PATTERN)
    private val testBarrierHypothesis = CausalHypothesisReference("bar-202", CausalHypothesisType.BARRIER)
    private val testValidText = "I wasn't avoiding exercise, my knee was injured"

    @Before
    fun setUp() {
        useCase = EvaluateCorrectionReviewDecisionUseCase()
    }

    private fun createValidEligible(
        userId: String = testUser,
        eventId: String = testEventId,
        recommendationId: String = testRecId,
        causalHypothesis: CausalHypothesisReference = testPatternHypothesis,
        scope: CorrectionScope = CorrectionScope.INFERENCE_CORRECTION,
        correctedText: String = testValidText
    ): CorrectionEligibilityResult.Eligible {
        return CorrectionEligibilityResult.Eligible(
            userId = userId,
            eventId = eventId,
            recommendationId = recommendationId,
            causalHypothesis = causalHypothesis,
            correctionScope = scope,
            correctedText = correctedText
        )
    }

    // =========================================================================
    // 1. Ineligible inputs produce NotReviewable
    // =========================================================================

    @Test
    fun `ineligible input cannot produce a reviewable decision for every reason`() {
        CorrectionIneligibilityReason.values().forEach { reason ->
            val ineligible = CorrectionEligibilityResult.Ineligible(
                reason = reason,
                detail = "Detail for $reason"
            )

            val decision = useCase(ineligible)

            assertTrue(
                "Ineligible reason $reason must produce NotReviewable",
                decision is CorrectionReviewDecision.NotReviewable
            )
            val notReviewable = decision as CorrectionReviewDecision.NotReviewable
            assertEquals(reason, notReviewable.ineligibilityReason)
            assertEquals("Detail for $reason", notReviewable.detail)
            assertFalse("No action must be taken for ineligible", decision.actionTaken)
            assertFalse("Hypothesis must not be declared false for ineligible", decision.hypothesisDeclaredFalse)
        }
    }

    // =========================================================================
    // 2. Eligible input produces only DeferredForConsideration
    // =========================================================================

    @Test
    fun `eligible pattern hypothesis produces only neutral deferred consideration outcome`() {
        val eligible = createValidEligible(causalHypothesis = testPatternHypothesis)

        val decision = useCase(eligible)

        assertTrue(
            "Eligible input must produce DeferredForConsideration",
            decision is CorrectionReviewDecision.DeferredForConsideration
        )
        val deferred = decision as CorrectionReviewDecision.DeferredForConsideration
        assertEquals(testUser, deferred.userId)
        assertEquals(testEventId, deferred.eventId)
        assertEquals(testRecId, deferred.recommendationId)
        assertEquals(testPatternHypothesis, deferred.causalHypothesis)
        assertEquals(CorrectionScope.INFERENCE_CORRECTION, deferred.correctionScope)
        assertEquals(testValidText, deferred.correctedText)
        assertFalse("actionTaken must be false", deferred.actionTaken)
        assertFalse("hypothesisDeclaredFalse must be false", deferred.hypothesisDeclaredFalse)
        assertTrue(deferred.rationale.contains("V2.16-C3.8A"))
    }

    @Test
    fun `eligible barrier hypothesis produces only neutral deferred consideration outcome`() {
        val eligible = createValidEligible(causalHypothesis = testBarrierHypothesis)

        val decision = useCase(eligible)

        assertTrue(decision is CorrectionReviewDecision.DeferredForConsideration)
        val deferred = decision as CorrectionReviewDecision.DeferredForConsideration
        assertEquals(testBarrierHypothesis, deferred.causalHypothesis)
        assertFalse("actionTaken must be false", deferred.actionTaken)
        assertFalse("hypothesisDeclaredFalse must be false", deferred.hypothesisDeclaredFalse)
    }

    // =========================================================================
    // 3 & 4. Invariant: Hypothesis never claimed false, rejected, or modified
    // =========================================================================

    @Test
    fun `decision never claims hypothesis is false or rejected across all outcomes`() {
        val eligibleDecision = useCase(createValidEligible())
        assertFalse(eligibleDecision.hypothesisDeclaredFalse)
        assertFalse(eligibleDecision.actionTaken)

        val ineligibleDecision = useCase(
            CorrectionEligibilityResult.Ineligible(CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE)
        )
        assertFalse(ineligibleDecision.hypothesisDeclaredFalse)
        assertFalse(ineligibleDecision.actionTaken)

        val nullDecision = useCase(null as CorrectionEligibilityResult?)
        assertFalse(nullDecision.hypothesisDeclaredFalse)
        assertFalse(nullDecision.actionTaken)
    }

    // =========================================================================
    // 5. Determinism
    // =========================================================================

    @Test
    fun `evaluation is strictly deterministic across repeated invocations`() {
        val eligible = createValidEligible()
        val firstDecision = useCase(eligible)

        repeat(100) {
            val repeatDecision = useCase(eligible)
            assertEquals("Repeated evaluation must be identical", firstDecision, repeatDecision)
        }
    }

    // =========================================================================
    // 6. Malformed and unsupported inputs fail closed
    // =========================================================================

    @Test
    fun `null eligibility result fails closed to NotReviewable`() {
        val decision = useCase(null as CorrectionEligibilityResult?)
        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        assertNull((decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
        assertEquals("Eligibility result must not be null", decision.detail)
    }

    @Test
    fun `eligible with blank userId fails closed to NotReviewable`() {
        val malformed = createValidEligible(userId = "   ")
        val decision = useCase(malformed)
        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        assertEquals(CorrectionIneligibilityReason.EVENT_OWNERSHIP_MISMATCH, (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
    }

    @Test
    fun `eligible with blank eventId fails closed to NotReviewable`() {
        val malformed = createValidEligible(eventId = "")
        val decision = useCase(malformed)
        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        assertEquals(CorrectionIneligibilityReason.EVENT_NOT_FOUND, (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
    }

    @Test
    fun `eligible with blank recommendationId fails closed to NotReviewable`() {
        val malformed = createValidEligible(recommendationId = " ")
        val decision = useCase(malformed)
        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        assertEquals(CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND, (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
    }

    @Test
    fun `eligible with blank causalHypothesis id fails closed to NotReviewable`() {
        val mockRef = mockk<CausalHypothesisReference>()
        every { mockRef.id } returns "   "
        val malformed = createValidEligible(causalHypothesis = mockRef)
        val decision = useCase(malformed)
        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        assertEquals(CorrectionIneligibilityReason.NO_CAUSAL_HYPOTHESIS_REFERENCE, (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
    }

    @Test
    fun `eligible with unsupported scope fails closed to NotReviewable`() {
        listOf(
            CorrectionScope.INTERVENTION_CORRECTION,
            CorrectionScope.PREFERENCE,
            CorrectionScope.CONSTRAINT,
            CorrectionScope.EXPLICIT_GENERAL_RULE
        ).forEach { unsupportedScope ->
            val malformed = createValidEligible(scope = unsupportedScope)
            val decision = useCase(malformed)
            assertTrue(decision is CorrectionReviewDecision.NotReviewable)
            assertEquals(
                CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
                (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason
            )
        }
    }

    @Test
    fun `eligible with blank or whitespace-only text fails closed to NotReviewable`() {
        val malformed = createValidEligible(correctedText = "    ")
        val decision = useCase(malformed)
        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        assertEquals(CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT, (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
    }

    @Test
    fun `eligible with generic boilerplate text fails closed to NotReviewable`() {
        listOf(
            "User marked as corrected",
            "Marked as corrected",
            "corrected",
            "USER CORRECTED"
        ).forEach { boilerplate ->
            val malformed = createValidEligible(correctedText = boilerplate)
            val decision = useCase(malformed)
            assertTrue(decision is CorrectionReviewDecision.NotReviewable)
            assertEquals(
                CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
                (decision as CorrectionReviewDecision.NotReviewable).ineligibilityReason
            )
        }
    }

    // =========================================================================
    // 7. Verification of zero database / repository writes
    // =========================================================================

    @Test
    fun `use case does not call mutation APIs or persist events during pipeline evaluation`() = runBlocking {
        val eventRepo: EventRepository = mockk(relaxed = true)
        val recRepo: InterventionRecommendationRepository = mockk(relaxed = true)
        val patternRepo: PatternRepository = mockk(relaxed = true)
        val barrierRepo: BarrierRepository = mockk(relaxed = true)

        val event = Event(
            id = testEventId,
            userId = testUser,
            type = EventType.INTERVENTION_OVERRIDDEN,
            entityType = "Intervention",
            entityId = "goal-1",
            occurredAt = 1000L,
            recordedAt = 1000L,
            source = EntitySource.SYSTEM,
            metadata = CorrectionMetadataParser.serialize(
                scope = CorrectionScope.INFERENCE_CORRECTION,
                type = "BREAK_DOWN",
                recommendation = "Step",
                correctedText = testValidText
            ),
            causationId = testRecId
        )

        val recommendation = InterventionRecommendation(
            id = testRecId,
            userId = testUser,
            interventionType = com.sanket_satpute_20.ironmind.domain.ai.InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityType = "GOAL",
            targetEntityId = "goal-1",
            rationale = "Fatigue",
            suggestedAction = "Step",
            status = InterventionRecommendationStatus.OVERRIDDEN,
            createdAt = 1000L,
            expiresAt = 9000L,
            causalHypothesis = testPatternHypothesis
        )

        val pattern = Pattern(
            id = testPatternHypothesis.id,
            userId = testUser,
            fingerprint = "fp-1",
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Fatigue pattern",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.8f,
            evidenceCount = 3,
            evidenceReferences = null,
            firstObservedAt = 1000L,
            lastObservedAt = 2000L,
            status = PatternStatus.ACTIVE,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        coEvery { eventRepo.getEvent(testEventId) } returns Result.Success(event)
        coEvery { recRepo.getRecommendation(testRecId) } returns Result.Success(recommendation)
        coEvery { patternRepo.getPattern(testPatternHypothesis.id) } returns Result.Success(pattern)

        val eligibilityUseCase = EvaluateCorrectionEligibilityUseCase(
            eventRepository = eventRepo,
            recommendationRepository = recRepo,
            patternRepository = patternRepo,
            barrierRepository = barrierRepo
        )

        val pipelineUseCase = EvaluateCorrectionReviewDecisionUseCase(
            evaluateCorrectionEligibilityUseCase = eligibilityUseCase
        )

        val decision = pipelineUseCase.evaluate(testUser, testEventId)

        assertTrue(decision is CorrectionReviewDecision.DeferredForConsideration)
        assertFalse(decision.actionTaken)
        assertFalse(decision.hypothesisDeclaredFalse)

        // Strict verification: zero writes or mutations executed
        coVerify(exactly = 0) { patternRepo.updatePattern(any()) }
        coVerify(exactly = 0) { patternRepo.updatePatternConfidence(any(), any(), any()) }
        coVerify(exactly = 0) { patternRepo.deletePattern(any()) }
        coVerify(exactly = 0) { patternRepo.savePattern(any()) }

        coVerify(exactly = 0) { barrierRepo.saveBarrier(any()) }

        coVerify(exactly = 0) { eventRepo.saveEvent(any()) }
        coVerify(exactly = 0) { eventRepo.saveEventWithLearnerStatus(any()) }
    }

    // =========================================================================
    // 8. DefaultEventProcessor safe no-op behavior unchanged
    // =========================================================================

    @Test
    fun `existing DefaultEventProcessor safe no-op behavior is preserved for INTERVENTION_OVERRIDDEN`() = runBlocking {
        val processor = DefaultEventProcessor()
        val overrideEvent = Event(
            id = "evt-noop",
            userId = testUser,
            type = EventType.INTERVENTION_OVERRIDDEN,
            entityType = "Intervention",
            entityId = "rec-1",
            occurredAt = 1000L,
            recordedAt = 1000L,
            source = EntitySource.SYSTEM,
            metadata = "scope=INFERENCE_CORRECTION,correctedText=No op text",
            causationId = "rec-1"
        )

        val outcome = processor.processEvent(overrideEvent)

        assertTrue(outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("UNVERIFIED_CORRECTION_SAFE_NO_OP", (outcome as EventProcessingOutcome.SuccessNoChange).reason)
    }

    // =========================================================================
    // 9. Pipeline evaluation without configured eligibility use case fails closed
    // =========================================================================

    @Test
    fun `evaluate without configured eligibility use case fails closed`() = runBlocking {
        val standaloneUseCase = EvaluateCorrectionReviewDecisionUseCase()
        val decision = standaloneUseCase.evaluate(testUser, testEventId)

        assertTrue(decision is CorrectionReviewDecision.NotReviewable)
        val notReviewable = decision as CorrectionReviewDecision.NotReviewable
        assertEquals(CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED, notReviewable.ineligibilityReason)
    }

    @Test
    fun `convenience overloads accept typed Eligible and Ineligible properly`() {
        val eligible = createValidEligible()
        val eligibleDecision = useCase(eligible)
        assertTrue(eligibleDecision is CorrectionReviewDecision.DeferredForConsideration)

        val ineligible = CorrectionEligibilityResult.Ineligible(CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT)
        val ineligibleDecision = useCase(ineligible)
        assertTrue(ineligibleDecision is CorrectionReviewDecision.NotReviewable)
        assertEquals(CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT, (ineligibleDecision as CorrectionReviewDecision.NotReviewable).ineligibilityReason)
    }
}
