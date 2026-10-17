package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionIneligibilityReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewCandidate
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewDecision
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository

/**
 * Deterministic, read-only query use case that discovers correction review candidates
 * for human consideration (Sprint V2.16-C3.8C).
 *
 * Epistemic & Architectural Guarantees:
 * - DATA_CONTRACT Sec 334 & AUTONOMY_POLICY Sec 308: Purely read-only; zero database writes,
 *   zero repository mutations, zero hypothesis confidence adjustments, and zero lifecycle transitions.
 * - In-Memory Projection: Computes candidates dynamically from historical events and current eligibility;
 *   does NOT create or persist a durable review queue.
 * - Bounded Result Semantics: The [limit] parameter applies to retrieved [EventType.INTERVENTION_OVERRIDDEN]
 *   events from the database before eligibility filtering. Consequently, fewer than [limit] candidates
 *   may be returned even if additional eligible candidates exist earlier in the time window.
 *   This is a bounded candidate set, not a guarantee of every eligible correction in history.
 * - Fail-Closed Operational Error Handling: Any underlying database or retrieval failure encountered
 *   during event lookup or individual candidate evaluation (DATA_RETRIEVAL_FAILED) immediately fails the
 *   entire query via [Result.Failure], preventing operational outages from masquerading as empty candidate lists.
 * - Deterministic Ordering: Candidates are ordered by [CorrectionReviewCandidate.occurredAt] descending,
 *   with timestamp ties broken deterministically by [CorrectionReviewCandidate.eventId] ascending.
 */
class GetCorrectionReviewCandidatesUseCase(
    private val eventRepository: EventRepository,
    private val evaluateCorrectionReviewDecisionUseCase: EvaluateCorrectionReviewDecisionUseCase
) {

    companion object {
        const val DEFAULT_LIMIT = 100
    }

    /**
     * Discovers eligible correction review candidates for [userId] within the specified time window.
     *
     * @param userId The non-blank user ID whose corrections are being queried.
     * @param startTime Inclusive start of time window (epoch millis). Default is 0L.
     * @param endTime Exclusive end of time window (epoch millis). Default is [Long.MAX_VALUE]. Must be > [startTime].
     * @param limit Maximum number of raw override events to retrieve from the database before eligibility filtering. Must be > 0.
     * @return [Result.Success] with the deterministic list of candidates, or [Result.Failure] on validation or operational errors.
     */
    suspend operator fun invoke(
        userId: String,
        startTime: Long = 0L,
        endTime: Long = Long.MAX_VALUE,
        limit: Int = DEFAULT_LIMIT
    ): Result<List<CorrectionReviewCandidate>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        if (startTime >= endTime) {
            return Result.Failure(
                IllegalArgumentException("startTime ($startTime) must be strictly less than endTime ($endTime)")
            )
        }
        if (limit <= 0) {
            return Result.Failure(IllegalArgumentException("limit ($limit) must be greater than zero"))
        }

        val trimmedUserId = userId.trim()

        val eventsResult = eventRepository.getEventsForTimeWindow(
            userId = trimmedUserId,
            startTime = startTime,
            endTime = endTime,
            types = listOf(EventType.INTERVENTION_OVERRIDDEN),
            limit = limit,
            orderAsc = false
        )

        if (eventsResult is Result.Failure) {
            return Result.Failure(eventsResult.error)
        }

        val events = (eventsResult as Result.Success).data
        val candidates = ArrayList<CorrectionReviewCandidate>(events.size)

        for (event in events) {
            val decision = evaluateCorrectionReviewDecisionUseCase.evaluate(
                userId = trimmedUserId,
                eventId = event.id
            )
            when (decision) {
                is CorrectionReviewDecision.DeferredForConsideration -> {
                    candidates.add(
                        CorrectionReviewCandidate(
                            userId = decision.userId,
                            eventId = decision.eventId,
                            recommendationId = decision.recommendationId,
                            causalHypothesis = decision.causalHypothesis,
                            correctionScope = decision.correctionScope,
                            correctedText = decision.correctedText,
                            occurredAt = event.occurredAt,
                            decision = decision
                        )
                    )
                }
                is CorrectionReviewDecision.NotReviewable -> {
                    if (decision.ineligibilityReason == CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED) {
                        return Result.Failure(
                            IllegalStateException(
                                "DATA_RETRIEVAL_FAILED: eventId=${event.id}, detail=${decision.detail ?: "Operational retrieval failure"}"
                            )
                        )
                    }
                    // Ordinary domain ineligibility: safely skip
                }
            }
        }

        val sortedCandidates = candidates.sortedWith(
            compareByDescending<CorrectionReviewCandidate> { it.occurredAt }
                .thenBy { it.eventId }
        )

        return Result.Success(sortedCandidates)
    }
}
