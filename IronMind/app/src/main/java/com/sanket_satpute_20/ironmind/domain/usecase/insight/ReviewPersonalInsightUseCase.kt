package com.sanket_satpute_20.ironmind.domain.usecase.insight

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 3.4: Use case managing user review actions for personal insights.
 *
 * Enforces key safety invariants:
 * 1. Dismissing an insight never deletes underlying evidence.
 * 2. Accepting an insight or linked recommendation never automatically confirms an associated barrier.
 * 3. Accepting a recommendation updates its status to ACCEPTED but NEVER claims intervention success.
 */
class ReviewPersonalInsightUseCase(
    private val insightRepository: InsightRepository,
    private val recommendationRepository: InterventionRecommendationRepository,
    private val clock: Clock
) {

    suspend fun markUseful(insightId: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        insightRepository.updateReviewStatus(
            id = insightId,
            userId = userId,
            status = InsightReviewStatus.USEFUL,
            reviewedAt = clock.currentTimeMillis()
        )
    }

    suspend fun dismiss(insightId: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        insightRepository.dismissInsight(
            id = insightId,
            userId = userId,
            timestamp = clock.currentTimeMillis()
        )
    }

    suspend fun reviewLater(insightId: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        insightRepository.updateReviewStatus(
            id = insightId,
            userId = userId,
            status = InsightReviewStatus.REVIEW_LATER,
            reviewedAt = clock.currentTimeMillis()
        )
    }

    suspend fun provideFeedback(insightId: String, userId: String, feedback: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        insightRepository.updateReviewStatus(
            id = insightId,
            userId = userId,
            status = InsightReviewStatus.USEFUL,
            feedback = feedback,
            reviewedAt = clock.currentTimeMillis()
        )
    }

    suspend fun acceptLinkedRecommendation(insightId: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val insightResult = insightRepository.getInsightById(insightId)
            if (insightResult is Result.Failure) return@withContext Result.Failure(insightResult.error)
            val insight = (insightResult as Result.Success).data
                ?: return@withContext Result.Failure(IllegalArgumentException("Insight not found: $insightId"))

            val recId = insight.linkedRecommendationId
                ?: return@withContext Result.Failure(IllegalStateException("No linked recommendation for insight: $insightId"))

            // Update recommendation to ACCEPTED. Does NOT mark outcome as SUCCESS!
            // Insight reviewStatus and confirmationState remain independent.
            recommendationRepository.updateRecommendationStatus(recId, InterventionRecommendationStatus.ACCEPTED)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun rejectLinkedRecommendation(insightId: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val insightResult = insightRepository.getInsightById(insightId)
            if (insightResult is Result.Failure) return@withContext Result.Failure(insightResult.error)
            val insight = (insightResult as Result.Success).data
                ?: return@withContext Result.Failure(IllegalArgumentException("Insight not found: $insightId"))

            val recId = insight.linkedRecommendationId
                ?: return@withContext Result.Failure(IllegalStateException("No linked recommendation for insight: $insightId"))

            // Update recommendation to REJECTED.
            // Crucial: Rejecting a recommendation does NOT dismiss or alter the insight.
            recommendationRepository.updateRecommendationStatus(recId, InterventionRecommendationStatus.REJECTED)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun confirmFactualClaim(insightId: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        // Explicit factual confirmation operation
        insightRepository.updateFactualConfirmation(
            id = insightId,
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            correction = null,
            timestamp = clock.currentTimeMillis()
        )
    }

    suspend fun refuteFactualClaim(insightId: String, userId: String, correction: String? = null): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        // Explicit factual refutation / correction operation
        insightRepository.updateFactualConfirmation(
            id = insightId,
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_REJECTED,
            correction = correction,
            timestamp = clock.currentTimeMillis()
        )
    }
}
