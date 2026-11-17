package com.sanket_satpute_20.ironmind.execution.background

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionIneligibilityReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewDecision
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionEventProcessingTest {

    private val mockUseCase = mockk<EvaluateCorrectionReviewDecisionUseCase>()
    private val processor = DefaultEventProcessor(evaluateCorrectionReviewDecisionUseCase = mockUseCase)

    private fun createOverrideEvent(
        userId: String = "user-123",
        eventId: String = "event-1",
        metadata: String? = "scope=INFERENCE_CORRECTION,type=HABIT,recommendation=Walk daily,correctedText=Only walk on weekends",
        causationId: String? = "rec-1"
    ) = Event(
        id = eventId,
        userId = userId,
        type = EventType.INTERVENTION_OVERRIDDEN,
        entityType = "Intervention",
        entityId = "rec-1",
        occurredAt = 1000L,
        recordedAt = 1000L,
        source = EntitySource.SYSTEM,
        metadata = metadata,
        causationId = causationId
    )

    @Test
    fun `valid correction evaluated to DeferredForConsideration produces EventProcessingOutcome Success`() = runBlocking {
        val event = createOverrideEvent()

        coEvery {
            mockUseCase.evaluate("user-123", "event-1", "rec-1")
        } returns CorrectionReviewDecision.DeferredForConsideration(
            userId = "user-123",
            eventId = "event-1",
            recommendationId = "rec-1",
            causalHypothesis = CausalHypothesisReference(id = "pat-1", type = CausalHypothesisType.PATTERN),
            correctionScope = CorrectionScope.INFERENCE_CORRECTION,
            correctedText = "Only walk on weekends",
            actionTaken = false,
            hypothesisDeclaredFalse = false
        )

        val outcome = processor.processEvent(event)

        assertTrue("Expected Success outcome for deferred review", outcome is EventProcessingOutcome.Success)
    }

    @Test
    fun `ineligible correction with generic boilerplate returns SuccessNoChange with reason`() = runBlocking {
        val event = createOverrideEvent(metadata = "scope=INFERENCE_CORRECTION,type=HABIT,recommendation=Walk,correctedText=User marked as corrected")

        coEvery {
            mockUseCase.evaluate("user-123", "event-1", "rec-1")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
            detail = "Correction text is generic boilerplate"
        )

        val outcome = processor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("INELIGIBLE: GENERIC_BOILERPLATE_TEXT", (outcome as EventProcessingOutcome.SuccessNoChange).reason)
    }

    @Test
    fun `ineligible correction with blank text returns SuccessNoChange with reason`() = runBlocking {
        val event = createOverrideEvent(metadata = "scope=INFERENCE_CORRECTION,type=HABIT,recommendation=Walk,correctedText=")

        coEvery {
            mockUseCase.evaluate("user-123", "event-1", "rec-1")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT,
            detail = "Correction text is blank"
        )

        val outcome = processor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("INELIGIBLE: BLANK_CORRECTION_TEXT", (outcome as EventProcessingOutcome.SuccessNoChange).reason)
    }

    @Test
    fun `ineligible correction with irrelevant scope returns SuccessNoChange with reason`() = runBlocking {
        val event = createOverrideEvent(metadata = "scope=INTERVENTION_CORRECTION,type=HABIT,recommendation=Walk,correctedText=Different text")

        coEvery {
            mockUseCase.evaluate("user-123", "event-1", "rec-1")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
            detail = "Correction scope is not relevant for hypothesis review"
        )

        val outcome = processor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("INELIGIBLE: IRRELEVANT_CORRECTION_SCOPE", (outcome as EventProcessingOutcome.SuccessNoChange).reason)
    }

    @Test
    fun `data retrieval failure during evaluation produces RetryableFailure`() = runBlocking {
        val event = createOverrideEvent()

        coEvery {
            mockUseCase.evaluate("user-123", "event-1", "rec-1")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
            detail = "Database connection pool exhausted"
        )

        val outcome = processor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.RetryableFailure)
        assertEquals("Database connection pool exhausted", (outcome as EventProcessingOutcome.RetryableFailure).message)
    }

    @Test
    fun `ownership mismatch produces PermanentFailure`() = runBlocking {
        val event = createOverrideEvent(userId = "user-attacker")

        coEvery {
            mockUseCase.evaluate("user-attacker", "event-1", "rec-1")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.EVENT_OWNERSHIP_MISMATCH,
            detail = "Event belongs to user-victim, expected user-attacker"
        )

        val outcome = processor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.PermanentFailure)
        assertEquals("Event belongs to user-victim, expected user-attacker", (outcome as EventProcessingOutcome.PermanentFailure).message)
    }

    @Test
    fun `unconfigured evaluation use case falls back safely to safe no-op`() = runBlocking {
        val unconfiguredProcessor = DefaultEventProcessor(evaluateCorrectionReviewDecisionUseCase = null)
        val event = createOverrideEvent()

        val outcome = unconfiguredProcessor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("UNVERIFIED_CORRECTION_SAFE_NO_OP", (outcome as EventProcessingOutcome.SuccessNoChange).reason)
    }
}

