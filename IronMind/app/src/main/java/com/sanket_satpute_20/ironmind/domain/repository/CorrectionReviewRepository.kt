package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewCandidate
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for managing eligible correction review candidates (Phase 7.3 / Sprint V2.16-C3.8).
 *
 * Epistemic boundaries:
 * - Durable Room persistence for candidate reviews and explicit human review actions.
 * - Guarantees recoverable candidate identity, causation pointer, scope, and user isolation.
 * - Zero autonomous mutation of Pattern or Barrier hypotheses.
 */
interface CorrectionReviewRepository {

    /**
     * Persists an eligible correction review item into durable Room storage.
     * Uses idempotent insert/upsert strategy so retries cannot duplicate records.
     */
    suspend fun persistCandidate(candidate: CorrectionReviewItem): Result<Unit, Exception>

    /**
     * Retrieves all pending correction review items for [userId].
     */
    suspend fun getPendingReviews(
        userId: String,
        limit: Int = 100
    ): Result<List<CorrectionReviewItem>, Exception>

    /**
     * Retrieves a single durable correction review item by review ID.
     */
    suspend fun getReview(
        userId: String,
        reviewId: String
    ): Result<CorrectionReviewItem?, Exception>

    /**
     * Retrieves a single durable correction review item by source event ID.
     */
    suspend fun getReviewByEventId(
        userId: String,
        eventId: String
    ): Result<CorrectionReviewItem?, Exception>

    /**
     * Observes pending correction review items for [userId] as a reactive Flow.
     */
    fun observePendingReviews(userId: String): Flow<List<CorrectionReviewItem>>

    /**
     * Updates the user review decision (APPROVED, REFUTED, DISMISSED) for a given review item.
     * Enforces valid state transitions and user isolation.
     */
    suspend fun updateReviewDecision(
        userId: String,
        reviewId: String,
        newStatus: CorrectionReviewStatus,
        feedback: String? = null,
        reviewedAt: Long
    ): Result<Unit, Exception>

    /**
     * Deletes all correction reviews for [userId] upon local data erasure.
     */
    suspend fun deleteAllForUser(userId: String): Result<Unit, Exception>

    // ── Existing projection methods (backward-compatible) ───────────────────

    /**
     * Retrieves all eligible correction review candidates for [userId] within [startTime]..[endTime],
     * ordered deterministically by occurredAt descending.
     */
    suspend fun getReviewCandidates(
        userId: String,
        startTime: Long = 0L,
        endTime: Long = Long.MAX_VALUE,
        limit: Int = 100
    ): Result<List<CorrectionReviewCandidate>, Exception>

    /**
     * Retrieves a single eligible review candidate by eventId.
     * Returns null if the event does not exist, belongs to another user, or is ineligible.
     */
    suspend fun getReviewCandidate(
        userId: String,
        eventId: String
    ): Result<CorrectionReviewCandidate?, Exception>

    /**
     * Verifies that the eligible correction review candidate for [userId] and [eventId] is
     * durably recorded in the database. Fails if the underlying record is missing or cannot be retrieved.
     */
    suspend fun verifyCandidatePersisted(
        userId: String,
        eventId: String
    ): Result<Unit, Exception>
}
