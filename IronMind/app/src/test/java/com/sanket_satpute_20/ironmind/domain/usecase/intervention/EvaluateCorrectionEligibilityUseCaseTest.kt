package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
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
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvaluateCorrectionEligibilityUseCaseTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var recommendationRepository: InterventionRecommendationRepository
    private lateinit var patternRepository: PatternRepository
    private lateinit var barrierRepository: BarrierRepository
    private lateinit var useCase: EvaluateCorrectionEligibilityUseCase

    private val testUser = "user-123"
    private val otherUser = "user-999"

    @Before
    fun setUp() {
        eventRepository = mockk(relaxed = true)
        recommendationRepository = mockk(relaxed = true)
        patternRepository = mockk(relaxed = true)
        barrierRepository = mockk(relaxed = true)

        useCase = EvaluateCorrectionEligibilityUseCase(
            eventRepository = eventRepository,
            recommendationRepository = recommendationRepository,
            patternRepository = patternRepository,
            barrierRepository = barrierRepository
        )
    }

    private fun createOverrideEvent(
        id: String = "evt-1",
        userId: String = testUser,
        type: EventType = EventType.INTERVENTION_OVERRIDDEN,
        causationId: String? = "rec-1",
        scope: CorrectionScope = CorrectionScope.INFERENCE_CORRECTION,
        correctedText: String? = "I wasn't tired, I had an urgent client call"
    ): Event {
        val metadata = CorrectionMetadataParser.serialize(
            scope = scope,
            type = "BREAK_DOWN",
            recommendation = "Take smaller step",
            correctedText = correctedText
        )
        return Event(
            id = id,
            userId = userId,
            type = type,
            entityType = "Intervention",
            entityId = "goal-1",
            occurredAt = 1000L,
            recordedAt = 1000L,
            source = EntitySource.SYSTEM,
            metadata = metadata,
            causationId = causationId
        )
    }

    private fun createRecommendation(
        id: String = "rec-1",
        userId: String = testUser,
        causalHypothesis: CausalHypothesisReference? = CausalHypothesisReference("pat-1", CausalHypothesisType.PATTERN)
    ): InterventionRecommendation {
        return InterventionRecommendation(
            id = id,
            userId = userId,
            interventionType = InterventionType.BREAK_DOWN,
            objective = InterventionObjective.INITIATE_ACTION,
            targetEntityType = "GOAL",
            targetEntityId = "goal-1",
            rationale = "User exhibits fatigue pattern",
            suggestedAction = "Take smaller step",
            status = InterventionRecommendationStatus.OVERRIDDEN,
            createdAt = 1000L,
            expiresAt = 9000L,
            causalHypothesis = causalHypothesis
        )
    }

    private fun createPattern(
        id: String = "pat-1",
        userId: String = testUser,
        status: PatternStatus = PatternStatus.ACTIVE,
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED,
        confidence: Float = 0.8f
    ): Pattern {
        return Pattern(
            id = id,
            userId = userId,
            fingerprint = "fp-1",
            type = PatternType.ENERGY_PATTERN,
            description = "Fatigue pattern in afternoon",
            conditions = null,
            predictedBehavior = null,
            confidence = confidence,
            evidenceCount = 3,
            evidenceReferences = null,
            firstObservedAt = 1000L,
            lastObservedAt = 2000L,
            status = status,
            confirmationState = confirmationState,
            createdAt = 1000L,
            updatedAt = 2000L
        )
    }

    private fun createBarrier(
        id: String = "bar-1",
        userId: String = testUser,
        status: BarrierStatus = BarrierStatus.ACTIVE,
        confirmationState: BarrierConfirmationState = BarrierConfirmationState.UNCONFIRMED
    ): BarrierHypothesis {
        return BarrierHypothesis(
            id = id,
            userId = userId,
            category = BarrierCategory.LOW_ENERGY,
            description = "Low energy after lunch",
            confirmationState = confirmationState,
            status = status,
            sourceReflectionId = "ref-1",
            firstObservedAt = 1000L,
            lastObservedAt = 2000L
        )
    }

    // 1. Wrong event type is ineligible
    @Test
    fun wrongEventType_isIneligible() = runBlocking {
        val event = createOverrideEvent(type = EventType.INTERVENTION_ACCEPTED)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.EVENT_TYPE_NOT_OVERRIDDEN,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun eventOwnershipMismatch_isIneligible() = runBlocking {
        val event = createOverrideEvent(userId = otherUser)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.EVENT_OWNERSHIP_MISMATCH,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 2. Irrelevant correction scope is ineligible
    @Test
    fun irrelevantScope_interventionCorrection_isIneligible() = runBlocking {
        val event = createOverrideEvent(scope = CorrectionScope.INTERVENTION_CORRECTION)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun irrelevantScope_preference_isIneligible() = runBlocking {
        val event = createOverrideEvent(scope = CorrectionScope.PREFERENCE)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun irrelevantScope_constraint_isIneligible() = runBlocking {
        val event = createOverrideEvent(scope = CorrectionScope.CONSTRAINT)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun irrelevantScope_explicitGeneralRule_isIneligible() = runBlocking {
        val event = createOverrideEvent(scope = CorrectionScope.EXPLICIT_GENERAL_RULE)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 3. Blank correction text is ineligible
    @Test
    fun blankCorrectionText_isIneligible() = runBlocking {
        val event = createOverrideEvent(correctedText = "   ")

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun nullCorrectionText_isIneligible() = runBlocking {
        val event = createOverrideEvent(correctedText = null)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 4. Boilerplate correction text is ineligible
    @Test
    fun boilerplateCorrectionText_userMarkedAsCorrected_isIneligible() = runBlocking {
        val event = createOverrideEvent(correctedText = "User marked as corrected")

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun boilerplateCorrectionText_caseInsensitive_isIneligible() = runBlocking {
        val event = createOverrideEvent(correctedText = "user marked as corrected")

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun boilerplateCorrectionText_markedAsCorrected_isIneligible() = runBlocking {
        val event = createOverrideEvent(correctedText = "marked as corrected")

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 5. Missing recommendation is handled deterministically
    @Test
    fun missingRecommendation_returnsIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-missing")
        coEvery { recommendationRepository.getRecommendation("rec-missing") } returns Result.Success(null)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun recommendationBelongingToDifferentUser_returnsIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-other")
        val rec = createRecommendation(id = "rec-other", userId = otherUser)
        coEvery { recommendationRepository.getRecommendation("rec-other") } returns Result.Success(rec)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 5b. Causation Integrity Tests
    @Test
    fun matchingExplicitAndEventCausationId_isEligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(id = "pat-1")
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event, recommendationId = "rec-1")
        assertTrue("Matching explicit and event causation ID must be eligible", result is CorrectionEligibilityResult.Eligible)
        assertEquals("rec-1", (result as CorrectionEligibilityResult.Eligible).recommendationId)
    }

    @Test
    fun mismatchedExplicitAndEventCausationId_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")

        val result = useCase(testUser, event, recommendationId = "rec-2")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_CAUSATION_MISMATCH,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun sameUserUnrelatedRecommendationMismatch_isIneligible() = runBlocking {
        // Both recommendations belong to testUser, but event was caused by rec-1
        val event = createOverrideEvent(causationId = "rec-1")
        val rec1 = createRecommendation(id = "rec-1", userId = testUser)
        val rec2 = createRecommendation(id = "rec-2", userId = testUser)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec1)
        coEvery { recommendationRepository.getRecommendation("rec-2") } returns Result.Success(rec2)

        val result = useCase(testUser, event, recommendationId = "rec-2")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_CAUSATION_MISMATCH,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun bothCausationAndExplicitRecommendationAbsent_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = null)

        val result = useCase(testUser, event, recommendationId = null)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun explicitIdProvidedWhenEventCausationNull_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = null)

        val result = useCase(testUser, event, recommendationId = "rec-1")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun explicitIdProvidedWhenEventCausationBlank_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "   ")

        val result = useCase(testUser, event, recommendationId = "rec-1")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 6. Missing causal reference is ineligible
    @Test
    fun recommendationWithNoCausalReference_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1", causalHypothesis = null)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.NO_CAUSAL_HYPOTHESIS_REFERENCE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 7. Missing hypothesis is handled safely
    @Test
    fun missingPatternHypothesis_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("pat-missing", CausalHypothesisType.PATTERN)
        )
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-missing") } returns Result.Success(null)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.REFERENCED_HYPOTHESIS_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun missingBarrierHypothesis_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-missing", CausalHypothesisType.BARRIER)
        )
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-missing") } returns Result.Success(null)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.REFERENCED_HYPOTHESIS_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 8. Hypothesis ownership mismatch is ineligible
    @Test
    fun patternOwnershipMismatch_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("pat-1", CausalHypothesisType.PATTERN)
        )
        val pattern = createPattern(id = "pat-1", userId = otherUser)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_OWNERSHIP_MISMATCH,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun barrierOwnershipMismatch_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        val barrier = createBarrier(id = "bar-1", userId = otherUser)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Success(barrier)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_OWNERSHIP_MISMATCH,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 9. Hypothesis not in reviewable state is ineligible
    @Test
    fun expiredPattern_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(status = PatternStatus.EXPIRED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun deletedPattern_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(status = PatternStatus.DELETED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun userRejectedPattern_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(confirmationState = MemoryConfirmationState.USER_REJECTED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun expiredBarrier_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        val barrier = createBarrier(status = BarrierStatus.EXPIRED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Success(barrier)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun rejectedBarrier_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        val barrier = createBarrier(confirmationState = BarrierConfirmationState.REJECTED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Success(barrier)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun dismissedBarrier_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        val barrier = createBarrier(confirmationState = BarrierConfirmationState.DISMISSED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Success(barrier)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun userConfirmedPattern_isEligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(confirmationState = MemoryConfirmationState.USER_CONFIRMED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue("Active user-confirmed pattern must remain eligible for review", result is CorrectionEligibilityResult.Eligible)
    }

    @Test
    fun systemConfirmedPattern_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(confirmationState = MemoryConfirmationState.SYSTEM_CONFIRMED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue("Unratified SYSTEM_CONFIRMED state must fail closed", result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun inactivePattern_isIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(status = PatternStatus.INACTIVE)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun confirmedBarrier_isEligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        val barrier = createBarrier(confirmationState = BarrierConfirmationState.CONFIRMED)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Success(barrier)

        val result = useCase(testUser, event)
        assertTrue("Active confirmed barrier must remain eligible for review", result is CorrectionEligibilityResult.Eligible)
    }

    // 10. Repository read failures do not produce a false positive
    @Test
    fun eventReadFailure_returnsIneligible() = runBlocking {
        coEvery { eventRepository.getEvent("evt-1") } returns Result.Failure(IllegalStateException("DB_TIMEOUT"))

        val result = useCase(testUser, "evt-1")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun recommendationReadFailure_returnsIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Failure(IllegalStateException("IO_ERROR"))

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun patternReadFailure_returnsIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(id = "rec-1")
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Failure(IllegalStateException("DISK_ERROR"))

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun barrierReadFailure_returnsIneligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1")
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Failure(IllegalStateException("DISK_ERROR"))

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 11. Valid, owned, reviewable hypotheses with meaningful corrections are eligible
    @Test
    fun validPatternCorrection_isEligible() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1", correctedText = "Schedule changed due to travel")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(id = "pat-1")

        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue("Valid pattern correction must be eligible", result is CorrectionEligibilityResult.Eligible)
        val eligible = result as CorrectionEligibilityResult.Eligible
        assertEquals(testUser, eligible.userId)
        assertEquals(event.id, eligible.eventId)
        assertEquals(rec.id, eligible.recommendationId)
        assertEquals("pat-1", eligible.causalHypothesis.id)
        assertEquals(CausalHypothesisType.PATTERN, eligible.causalHypothesis.type)
        assertEquals(CorrectionScope.INFERENCE_CORRECTION, eligible.correctionScope)
        assertEquals("Schedule changed due to travel", eligible.correctedText)
    }

    @Test
    fun validBarrierCorrection_isEligible() = runBlocking {
        val event = createOverrideEvent(
            causationId = "rec-1",
            correctedText = "I wasn't distracted, urgent production incident occurred"
        )
        val rec = createRecommendation(
            id = "rec-1",
            causalHypothesis = CausalHypothesisReference("bar-1", CausalHypothesisType.BARRIER)
        )
        val barrier = createBarrier(id = "bar-1")

        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { barrierRepository.getBarrier("bar-1") } returns Result.Success(barrier)

        val result = useCase(testUser, event)
        assertTrue("Valid barrier correction must be eligible", result is CorrectionEligibilityResult.Eligible)
        val eligible = result as CorrectionEligibilityResult.Eligible
        assertEquals(testUser, eligible.userId)
        assertEquals(event.id, eligible.eventId)
        assertEquals(rec.id, eligible.recommendationId)
        assertEquals("bar-1", eligible.causalHypothesis.id)
        assertEquals(CausalHypothesisType.BARRIER, eligible.causalHypothesis.type)
        assertEquals(CorrectionScope.INFERENCE_CORRECTION, eligible.correctionScope)
        assertEquals("I wasn't distracted, urgent production incident occurred", eligible.correctedText)
    }

    @Test
    fun invokingByEventId_resolvesAndEvaluatesSuccessfully() = runBlocking {
        val event = createOverrideEvent(id = "evt-lookup", causationId = "rec-1", correctedText = "Explanation here")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(id = "pat-1")

        coEvery { eventRepository.getEvent("evt-lookup") } returns Result.Success(event)
        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, "evt-lookup")
        assertTrue(result is CorrectionEligibilityResult.Eligible)
    }

    @Test
    fun invokingByEventId_withNullCausationAndExplicitRecommendation_isIneligible() = runBlocking {
        val event = createOverrideEvent(id = "evt-null-causation", causationId = null)
        coEvery { eventRepository.getEvent("evt-null-causation") } returns Result.Success(event)

        val result = useCase(testUser, "evt-null-causation", recommendationId = "rec-1")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    @Test
    fun invokingByEventId_withMismatchedExplicitRecommendation_isIneligible() = runBlocking {
        val event = createOverrideEvent(id = "evt-mismatch", causationId = "rec-1")
        coEvery { eventRepository.getEvent("evt-mismatch") } returns Result.Success(event)

        val result = useCase(testUser, "evt-mismatch", recommendationId = "rec-2")
        assertTrue(result is CorrectionEligibilityResult.Ineligible)
        assertEquals(
            CorrectionIneligibilityReason.RECOMMENDATION_CAUSATION_MISMATCH,
            (result as CorrectionEligibilityResult.Ineligible).reason
        )
    }

    // 12. Read-only guarantee: Evaluating a correction does NOT invoke any repository mutation
    @Test
    fun evaluationIsPurelyReadOnly_invokesZeroMutations_andPreservesHypothesisState() = runBlocking {
        val event = createOverrideEvent(causationId = "rec-1", correctedText = "Legitimate explanation")
        val rec = createRecommendation(id = "rec-1")
        val pattern = createPattern(id = "pat-1", confidence = 0.85f, status = PatternStatus.ACTIVE)

        coEvery { recommendationRepository.getRecommendation("rec-1") } returns Result.Success(rec)
        coEvery { patternRepository.getPattern("pat-1") } returns Result.Success(pattern)

        val result = useCase(testUser, event)
        assertTrue(result is CorrectionEligibilityResult.Eligible)

        // Strict mutation checks: Assert NO repository mutations occurred
        coVerify(exactly = 0) { eventRepository.saveEvent(any()) }
        coVerify(exactly = 0) { eventRepository.saveEventWithLearnerStatus(any()) }
        coVerify(exactly = 0) { recommendationRepository.saveRecommendation(any()) }
        coVerify(exactly = 0) { recommendationRepository.updateRecommendationStatus(any(), any()) }
        coVerify(exactly = 0) { patternRepository.savePattern(any()) }
        coVerify(exactly = 0) { patternRepository.updatePattern(any()) }
        coVerify(exactly = 0) { patternRepository.updatePatternConfidence(any(), any(), any()) }
        coVerify(exactly = 0) { patternRepository.deletePattern(any()) }
        coVerify(exactly = 0) { barrierRepository.saveBarrier(any()) }

        // State invariant: Pattern confidence and status are unaltered
        assertEquals(0.85f, pattern.confidence, 0.001f)
        assertEquals(PatternStatus.ACTIVE, pattern.status)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, pattern.confirmationState)
    }
}
