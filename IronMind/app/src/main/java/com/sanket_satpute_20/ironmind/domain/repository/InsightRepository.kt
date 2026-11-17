package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3.4: Repository for storing and retrieving Personal Insights.
 */
interface InsightRepository : Repository {

    /**
     * Persists or updates a PersonalInsight record.
     */
    suspend fun saveInsight(insight: PersonalInsight): Result<PersonalInsight, Exception>

    /**
     * Retrieves an insight by ID.
     */
    suspend fun getInsightById(id: String): Result<PersonalInsight?, Exception>

    /**
     * Retrieves all insights for a user.
     */
    suspend fun getInsightsForUser(userId: String): Result<List<PersonalInsight>, Exception>

    /**
     * Observes real-time updates of personal insights for a user.
     */
    fun observeInsightsForUser(userId: String): Flow<List<PersonalInsight>>

    /**
     * Updates the user review status, feedback, and reviewed timestamp of an insight.
     */
    suspend fun updateReviewStatus(
        id: String,
        userId: String,
        status: InsightReviewStatus,
        feedback: String? = null,
        reviewedAt: Long = System.currentTimeMillis()
    ): Result<Unit, Exception>

    /**
     * Explicitly updates the factual confirmation state and epistemic authority of an insight.
     * Decoupled from user review actions (USEFUL, DISMISSED, REVIEW_LATER).
     */
    suspend fun updateFactualConfirmation(
        id: String,
        userId: String,
        confirmationState: MemoryConfirmationState,
        correction: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<Unit, Exception>

    /**
     * Dismisses an insight without deleting underlying evidence.
     */
    suspend fun dismissInsight(
        id: String,
        userId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Result<Unit, Exception> = updateReviewStatus(
        id = id,
        userId = userId,
        status = InsightReviewStatus.DISMISSED,
        feedback = null,
        reviewedAt = timestamp
    )
}
