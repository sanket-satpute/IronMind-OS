package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetCorrectionReviewCandidatesUseCaseTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var evaluateCorrectionReviewDecisionUseCase: EvaluateCorrectionReviewDecisionUseCase
    private lateinit var useCase: GetCorrectionReviewCandidatesUseCase

    private val testUser = "user-123"
    private val testPatternHypothesis = CausalHypothesisReference("pat-101", CausalHypothesisType.PATTERN)
    private val testBarrierHypothesis = CausalHypothesisReference("bar-202", CausalHypothesisType.BARRIER)

    @Before
    fun setUp() {
        eventRepository = mockk(relaxed = true)
        evaluateCorrectionReviewDecisionUseCase = mockk(relaxed = true)
        useCase = GetCorrectionReviewCandidatesUseCase(
            eventRepository = eventRepository,
            evaluateCorrectionReviewDecisionUseCase = evaluateCorrectionReviewDecisionUseCase
        )
    }

    private fun createEvent(
        id: String,
        userId: String = testUser,
        occurredAt: Long = 1000L,
        causationId: String = "rec-$id"
    ) = Event(
        id = id,
        userId = userId,
        type = EventType.INTERVENTION_OVERRIDDEN,
        entityType = "Intervention",
        entityId = "entity-$id",
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        source = EntitySource.SYSTEM,
        metadata = "scope=INFERENCE_CORRECTION,correctedText=Reason was wrong",
        causationId = causationId
    )

    private fun createDeferredDecision(
        eventId: String,
        userId: String = testUser,
        recommendationId: String = "rec-$eventId",
        causalHypothesis: CausalHypothesisReference = testPatternHypothesis,
        correctedText: String = "Reason was wrong"
    ) = CorrectionReviewDecision.DeferredForConsideration(
        userId = userId,
        eventId = eventId,
        recommendationId = recommendationId,
        causalHypothesis = causalHypothesis,
        correctionScope = CorrectionScope.INFERENCE_CORRECTION,
        correctedText = correctedText,
        actionTaken = false,
        hypothesisDeclaredFalse = false
    )

    // =========================================================================
    // 1. Input Validation
    // =========================================================================

    @Test
    fun `blank userId fails without querying repository`() = runBlocking {
        val result = useCase(userId = "   ")

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message!!.contains("userId must not be blank"))

        coVerify(exactly = 0) { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invalid time range where startTime equals endTime fails without querying repository`() = runBlocking {
        val result = useCase(userId = testUser, startTime = 5000L, endTime = 5000L)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message!!.contains("startTime (5000) must be strictly less than endTime (5000)"))

        coVerify(exactly = 0) { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invalid time range where startTime exceeds endTime fails without querying repository`() = runBlocking {
        val result = useCase(userId = testUser, startTime = 9000L, endTime = 5000L)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message!!.contains("startTime (9000) must be strictly less than endTime (5000)"))

        coVerify(exactly = 0) { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `zero limit fails without querying repository`() = runBlocking {
        val result = useCase(userId = testUser, limit = 0)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message!!.contains("limit (0) must be greater than zero"))

        coVerify(exactly = 0) { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `negative limit fails without querying repository`() = runBlocking {
        val result = useCase(userId = testUser, limit = -5)

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalArgumentException)
        assertTrue(error.message!!.contains("limit (-5) must be greater than zero"))

        coVerify(exactly = 0) { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) }
    }

    // =========================================================================
    // 2. Repository Query & Filtering Semantics
    // =========================================================================

    @Test
    fun `repository query failure propagates as failure`() = runBlocking {
        val expectedException = RuntimeException("Database read error")
        coEvery {
            eventRepository.getEventsForTimeWindow(
                userId = testUser,
                startTime = any(),
                endTime = any(),
                types = any(),
                limit = any(),
                orderAsc = any()
            )
        } returns Result.Failure(expectedException)

        val result = useCase(userId = testUser)

        assertTrue(result is Result.Failure)
        assertEquals(expectedException, (result as Result.Failure).error)
    }

    @Test
    fun `empty event result returns success with empty candidate list`() = runBlocking {
        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(emptyList())

        val result = useCase(userId = testUser)

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
        coVerify(exactly = 0) { evaluateCorrectionReviewDecisionUseCase.evaluate(any(), any(), any()) }
    }

    @Test
    fun `only INTERVENTION_OVERRIDDEN events are requested`() = runBlocking {
        val typesSlot = slot<List<EventType>>()
        coEvery {
            eventRepository.getEventsForTimeWindow(
                userId = testUser,
                startTime = 0L,
                endTime = Long.MAX_VALUE,
                types = capture(typesSlot),
                limit = 100,
                orderAsc = false
            )
        } returns Result.Success(emptyList())

        useCase(userId = testUser)

        assertEquals(listOf(EventType.INTERVENTION_OVERRIDDEN), typesSlot.captured)
    }

    @Test
    fun `ordinary ineligible events are skipped safely`() = runBlocking {
        val event1 = createEvent("evt-1")
        val event2 = createEvent("evt-2")
        val event3 = createEvent("evt-3")

        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(listOf(event1, event2, event3))

        // event1 is eligible
        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-1")
        } returns createDeferredDecision("evt-1")

        // event2 is ineligible: boilerplate text
        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-2")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
            detail = "Boilerplate text"
        )

        // event3 is ineligible: hypothesis not in reviewable state
        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-3")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
            detail = "Hypothesis expired"
        )

        val result = useCase(userId = testUser)

        assertTrue(result is Result.Success)
        val candidates = (result as Result.Success).data
        assertEquals(1, candidates.size)
        assertEquals("evt-1", candidates[0].eventId)
    }

    @Test
    fun `DeferredForConsideration events produce correctly populated candidates`() = runBlocking {
        val event = createEvent("evt-100", occurredAt = 123456789L)
        val decision = createDeferredDecision(
            eventId = "evt-100",
            recommendationId = "rec-custom",
            causalHypothesis = testBarrierHypothesis,
            correctedText = "Specific obstacle explanation"
        )

        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(listOf(event))

        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-100")
        } returns decision

        val result = useCase(userId = testUser)

        assertTrue(result is Result.Success)
        val candidates = (result as Result.Success).data
        assertEquals(1, candidates.size)

        val candidate = candidates[0]
        assertEquals(testUser, candidate.userId)
        assertEquals("evt-100", candidate.eventId)
        assertEquals("rec-custom", candidate.recommendationId)
        assertEquals(testBarrierHypothesis, candidate.causalHypothesis)
        assertEquals(CorrectionScope.INFERENCE_CORRECTION, candidate.correctionScope)
        assertEquals("Specific obstacle explanation", candidate.correctedText)
        assertEquals(123456789L, candidate.occurredAt)
        assertEquals(decision, candidate.decision)
        assertFalse("actionTaken must remain false", candidate.actionTaken)
        assertFalse("hypothesisDeclaredFalse must remain false", candidate.hypothesisDeclaredFalse)
    }

    // =========================================================================
    // 3. Operational Failure Handling (DATA_RETRIEVAL_FAILED)
    // =========================================================================

    @Test
    fun `DATA_RETRIEVAL_FAILED evaluation fails the overall operation and is not silently skipped`() = runBlocking {
        val event1 = createEvent("evt-1")
        val event2 = createEvent("evt-2")

        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(listOf(event1, event2))

        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-1")
        } returns createDeferredDecision("evt-1")

        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-2")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
            detail = "PatternRepository query failed due to SQLite lock"
        )

        val result = useCase(userId = testUser)

        assertTrue("Operational failure must cause overall Result.Failure", result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is IllegalStateException)
        assertTrue(error.message!!.contains("DATA_RETRIEVAL_FAILED"))
        assertTrue(error.message!!.contains("evt-2"))
        assertTrue(error.message!!.contains("SQLite lock"))
    }

    // =========================================================================
    // 4. Deterministic Ordering & Tie-Breaking
    // =========================================================================

    @Test
    fun `results are ordered by occurredAt descending`() = runBlocking {
        val eventOld = createEvent("evt-old", occurredAt = 1000L)
        val eventMid = createEvent("evt-mid", occurredAt = 2000L)
        val eventNew = createEvent("evt-new", occurredAt = 3000L)

        // Return from repo in mixed/unsorted order to verify explicit sorting
        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(listOf(eventMid, eventOld, eventNew))

        coEvery { evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, any()) } answers {
            createDeferredDecision(secondArg())
        }

        val result = useCase(userId = testUser)

        assertTrue(result is Result.Success)
        val candidates = (result as Result.Success).data
        assertEquals(3, candidates.size)
        assertEquals("evt-new", candidates[0].eventId)
        assertEquals("evt-mid", candidates[1].eventId)
        assertEquals("evt-old", candidates[2].eventId)
    }

    @Test
    fun `equal timestamps are ordered by event ID ascending as deterministic tiebreaker`() = runBlocking {
        val eventC = createEvent("evt-c", occurredAt = 5000L)
        val eventA = createEvent("evt-a", occurredAt = 5000L)
        val eventB = createEvent("evt-b", occurredAt = 5000L)

        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(listOf(eventC, eventA, eventB))

        coEvery { evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, any()) } answers {
            createDeferredDecision(secondArg())
        }

        val result = useCase(userId = testUser)

        assertTrue(result is Result.Success)
        val candidates = (result as Result.Success).data
        assertEquals(3, candidates.size)
        // Timestamp ties broken by eventId ascending: "evt-a", "evt-b", "evt-c"
        assertEquals("evt-a", candidates[0].eventId)
        assertEquals("evt-b", candidates[1].eventId)
        assertEquals("evt-c", candidates[2].eventId)
    }

    @Test
    fun `repeated evaluations return the same ordered output deterministically`() = runBlocking {
        val events = listOf(
            createEvent("evt-1", occurredAt = 2000L),
            createEvent("evt-2", occurredAt = 2000L),
            createEvent("evt-3", occurredAt = 5000L),
            createEvent("evt-4", occurredAt = 1000L)
        )

        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(events)

        coEvery { evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, any()) } answers {
            createDeferredDecision(secondArg())
        }

        val firstResult = useCase(userId = testUser)
        assertTrue(firstResult is Result.Success)
        val firstCandidates = (firstResult as Result.Success).data

        repeat(50) {
            val repeatResult = useCase(userId = testUser)
            assertTrue(repeatResult is Result.Success)
            assertEquals(firstCandidates, (repeatResult as Result.Success).data)
        }
    }

    // =========================================================================
    // 5. Bounded Result Semantics (Pre-filter Query Limit)
    // =========================================================================

    @Test
    fun `query limit applies before eligibility filtering as a bounded candidate set`() = runBlocking {
        // Suppose the database limit is set to 2.
        // Event 1 is eligible.
        // Event 2 is ineligible (generic boilerplate).
        // Event 3 would be eligible, but database query with limit=2 only returns events 1 and 2.
        // The use case must return a bounded list of 1 candidate, not 2.
        val event1 = createEvent("evt-1", occurredAt = 3000L)
        val event2 = createEvent("evt-2", occurredAt = 2000L)

        coEvery {
            eventRepository.getEventsForTimeWindow(
                userId = testUser,
                startTime = any(),
                endTime = any(),
                types = any(),
                limit = 2,
                orderAsc = false
            )
        } returns Result.Success(listOf(event1, event2))

        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-1")
        } returns createDeferredDecision("evt-1")

        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-2")
        } returns CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT
        )

        val result = useCase(userId = testUser, limit = 2)

        assertTrue(result is Result.Success)
        val candidates = (result as Result.Success).data
        assertEquals("Fewer than limit candidates returned due to pre-filter limit", 1, candidates.size)
        assertEquals("evt-1", candidates[0].eventId)
    }

    // =========================================================================
    // 6. Zero Mutation Assertions
    // =========================================================================

    @Test
    fun `use case does not call write or delete methods on any repository`() = runBlocking {
        val event1 = createEvent("evt-1", occurredAt = 1000L)
        coEvery {
            eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any())
        } returns Result.Success(listOf(event1))

        coEvery {
            evaluateCorrectionReviewDecisionUseCase.evaluate(testUser, "evt-1")
        } returns createDeferredDecision("evt-1")

        val result = useCase(userId = testUser)
        assertTrue(result is Result.Success)

        // Strict verification: zero writes or mutations executed
        coVerify(exactly = 0) { eventRepository.saveEvent(any()) }
        coVerify(exactly = 0) { eventRepository.saveEventWithLearnerStatus(any()) }
    }
}
