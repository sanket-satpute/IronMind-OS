package com.sanket_satpute_20.ironmind.domain.model.insight

import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness

/**
 * Phase 3.4: Category of personal intelligence insights.
 */
enum class InsightCategory {
    PRODUCTIVITY,
    GOAL_EXECUTION,
    TASK_SCHEDULING,
    DIGITAL_BEHAVIOUR,
    FOCUS,
    CROSS_DOMAIN,
    INTERVENTION_OUTCOME
}

/**
 * User review and lifecycle status for a personal insight.
 */
enum class InsightReviewStatus {
    UNREVIEWED,
    USEFUL,
    DISMISSED,
    REVIEW_LATER
}

/**
 * Structured explainability block accompanying every displayed insight.
 * Satisfies the 6 mandatory questions:
 * 1. What was observed?
 * 2. Over what period?
 * 3. What evidence supports this?
 * 4. Why does this matter?
 * 5. What is uncertain or unavailable?
 * 6. What can the user do next?
 */
data class ExplainabilityBlock(
    val whatWasObserved: String,
    val observationPeriod: String,
    val supportingEvidenceDescription: String,
    val whyItMatters: String,
    val uncertaintyOrLimitations: String,
    val suggestedNextSteps: String
)

/**
 * Domain model representing an explainable, evidence-backed personal insight.
 *
 * Distinguishes observed facts, derived patterns, AI hypotheses, and user-confirmed data.
 */
data class PersonalInsight(
    val id: String,
    val userId: String,
    val category: InsightCategory,
    val title: String,
    val explanation: String,
    val explainabilityBlock: ExplainabilityBlock,
    val supportingPatternIds: List<String> = emptyList(),
    val supportingBarrierIds: List<String> = emptyList(),
    val supportingEvidenceIds: List<String> = emptyList(),
    val linkedRecommendationId: String? = null,
    val observationWindowStartMs: Long,
    val observationWindowEndMs: Long,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val confidence: Float,
    val epistemicAuthority: EpistemicAuthority,
    val sourceProvenance: List<String> = emptyList(),
    val sourceFreshness: SourceFreshness = SourceFreshness.CURRENT,
    val reviewStatus: InsightReviewStatus = InsightReviewStatus.UNREVIEWED,
    val confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED,
    val userFeedback: String? = null,
    val reviewedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int = 1
)
