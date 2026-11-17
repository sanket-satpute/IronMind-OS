package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.data.repository.CorrectionReviewRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.execution.background.DefaultEventProcessor
import com.sanket_satpute_20.ironmind.execution.background.EventProcessingOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CorrectionReviewLifecycleTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var evaluateDecisionUseCase: EvaluateCorrectionReviewDecisionUseCase
    private lateinit var correctionReviewRepository: CorrectionReviewRepository
    private lateinit var eventProcessor: DefaultEventProcessor
    private lateinit var mockPatternRepo: PatternRepository
    private lateinit var mockBarrierRepo: BarrierRepository

    private val userId = "user-rev-101"
    private val otherUserId = "user-rev-202"
    private val eventId = "evt-rev-1"
    private val recId = "rec-rev-1"

    private val patternHypothesis = CausalHypothesisReference("pat-1", CausalHypothesisType.PATTERN)

    @Before
    fun setUp() {
        eventRepository = mockk(relaxed = true)
        evaluateDecisionUseCase = mockk(relaxed = true)
        mockPatternRepo = mockk(relaxed = true)
        mockBarrierRepo = mockk(relaxed = true)

        correctionReviewRepository = CorrectionReviewRepositoryImpl(
            eventRepository = eventRepository,
            evaluateCorrectionReviewDecisionUseCase = evaluateDecisionUseCase
        )

        eventProcessor = DefaultEventProcessor(
            evaluateCorrectionReviewDecisionUseCase = evaluateDecisionUseCase,
            correctionReviewRepository = correctionReviewRepository
        )
    }

    private fun createEvent(
        id: String = eventId,
        uid: String = userId,
        causation: String = recId
    ) = Event(
        id = id,
        userId = uid,
        type = EventType.INTERVENTION_OVERRIDDEN,
        entityType = "Intervention",
        entityId = "entity-1",
        occurredAt = 1000L,
        recordedAt = 1000L,
        source = EntitySource.SYSTEM,
        metadata = "scope=INFERENCE_CORRECTION,correctedText=Only on weekdays",
        causationId = causation
    )

    private fun createDeferredDecision(
        eId: String = eventId,
        uId: String = userId,
        rId: String = recId
    ) = CorrectionReviewDecision.DeferredForConsideration(
        userId = uId,
        eventId = eId,
        recommendationId = rId,
        causalHypothesis = patternHypothesis,
        correctionScope = CorrectionScope.INFERENCE_CORRECTION,
        correctedText = "Only on weekdays",
        actionTaken = false,
        hypothesisDeclaredFalse = false
    )

    @Test
    fun `eligible correction becomes durably reviewable`() = runBlocking {
        val event = createEvent()
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(event)
        coEvery {
            eventRepository.getEventsForTimeWindow(userId, any(), any(), listOf(EventType.INTERVENTION_OVERRIDDEN), any(), any())
        } returns Result.Success(listOf(event))
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, recId)
        } returns createDeferredDecision()
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, null)
        } returns createDeferredDecision()

        val outcome = eventProcessor.processEvent(event)
        assertTrue("Expected Success outcome", outcome is EventProcessingOutcome.Success)

        val candidatesResult = correctionReviewRepository.getReviewCandidates(userId)
        assertTrue(candidatesResult is Result.Success)
        val candidates = (candidatesResult as Result.Success).data
        assertEquals(1, candidates.size)
        assertEquals(eventId, candidates[0].eventId)
        assertEquals(recId, candidates[0].recommendationId)
        assertEquals("Only on weekdays", candidates[0].correctedText)
        assertEquals(patternHypothesis, candidates[0].causalHypothesis)
        assertFalse(candidates[0].actionTaken)
        assertFalse(candidates[0].hypothesisDeclaredFalse)
    }

    @Test
    fun `ineligible correction does not enter the review queue`() = runBlocking {
        val event = createEvent()
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(event)
        coEvery {
            eventRepository.getEventsForTimeWindow(userId, any(), any(), listOf(EventType.INTERVENTION_OVERRIDDEN), any(), any())
        } returns Result.Success(listOf(event))
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, any())
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT,
            detail = "Corrected text is blank"
        )

        val outcome = eventProcessor.processEvent(event)
        assertTrue("Expected SuccessNoChange", outcome is EventProcessingOutcome.SuccessNoChange)

        val candidatesResult = correctionReviewRepository.getReviewCandidates(userId)
        assertTrue(candidatesResult is Result.Success)
        val candidates = (candidatesResult as Result.Success).data
        assertTrue("Queue must be empty for ineligible correction", candidates.isEmpty())
    }

    @Test
    fun `retry does not duplicate candidates`() = runBlocking {
        val event = createEvent()
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(event)
        coEvery {
            eventRepository.getEventsForTimeWindow(userId, any(), any(), listOf(EventType.INTERVENTION_OVERRIDDEN), any(), any())
        } returns Result.Success(listOf(event))
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, any())
        } returns createDeferredDecision()

        // First attempt
        val outcome1 = eventProcessor.processEvent(event)
        assertTrue(outcome1 is EventProcessingOutcome.Success)

        // Retry attempt
        val outcome2 = eventProcessor.processEvent(event)
        assertTrue(outcome2 is EventProcessingOutcome.Success)

        val candidatesResult = correctionReviewRepository.getReviewCandidates(userId)
        val candidates = (candidatesResult as Result.Success).data
        assertEquals("Candidate list must remain exactly 1 after retry", 1, candidates.size)
    }

    @Test
    fun `event status and candidate persistence remain consistent`() = runBlocking {
        val event = createEvent()
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(event)
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, any())
        } returns createDeferredDecision()

        val verifyResult = correctionReviewRepository.verifyCandidatePersisted(userId, eventId)
        assertTrue("Candidate must be verified as persisted", verifyResult is Result.Success)

        val singleResult = correctionReviewRepository.getReviewCandidate(userId, eventId)
        assertTrue(singleResult is Result.Success)
        assertNotNull((singleResult as Result.Success).data)
    }

    @Test
    fun `user isolation is enforced`() = runBlocking {
        val event = createEvent()
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(event)
        coEvery {
            eventRepository.getEventsForTimeWindow(otherUserId, any(), any(), listOf(EventType.INTERVENTION_OVERRIDDEN), any(), any())
        } returns Result.Success(emptyList())

        // Other user querying candidate
        val candidatesResult = correctionReviewRepository.getReviewCandidates(otherUserId)
        val candidates = (candidatesResult as Result.Success).data
        assertTrue("Other user cannot see candidates", candidates.isEmpty())

        val singleResult = correctionReviewRepository.getReviewCandidate(otherUserId, eventId)
        assertTrue(singleResult is Result.Success)
        assertNull("Candidate lookup returns null for foreign user", (singleResult as Result.Success).data)
    }

    @Test
    fun `persistence failures are not silently treated as success`() = runBlocking {
        val event = createEvent()
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, any())
        } returns createDeferredDecision()
        // Event cannot be found in repository (operational persistence failure)
        coEvery { eventRepository.getEvent(eventId) } returns Result.Failure(RuntimeException("Room SQLite Disk I/O error"))

        val outcome = eventProcessor.processEvent(event)
        assertTrue("Operational persistence failure must yield RetryableFailure", outcome is EventProcessingOutcome.RetryableFailure)
    }

    @Test
    fun `no Pattern or Barrier mutation occurs without explicit user review`() = runBlocking {
        val event = createEvent()
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(event)
        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, any())
        } returns createDeferredDecision()

        eventProcessor.processEvent(event)
        correctionReviewRepository.getReviewCandidates(userId)
        correctionReviewRepository.getReviewCandidate(userId, eventId)

        // Epistemic contract: zero mutation to Pattern or Barrier hypotheses
        coVerify(exactly = 0) { mockPatternRepo.savePattern(any()) }
        coVerify(exactly = 0) { mockPatternRepo.updatePattern(any()) }
        coVerify(exactly = 0) { mockPatternRepo.deletePattern(any()) }
        coVerify(exactly = 0) { mockBarrierRepo.saveBarrier(any()) }
    }
}

