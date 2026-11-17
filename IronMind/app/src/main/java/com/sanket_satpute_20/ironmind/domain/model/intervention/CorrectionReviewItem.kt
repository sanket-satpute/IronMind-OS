package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * User-facing review status for an eligible correction candidate.
 */
enum class CorrectionReviewStatus {
    PENDING,
    APPROVED,
    REFUTED,
    DISMISSED
}

/**
 * Durable domain representation of a correction review candidate (Phase 7.3).
 *
 * Epistemic & Autonomy Guarantees (DATA_CONTRACT Sec 334, AUTONOMY_POLICY Sec 308):
 * - Persisted durably in Room database.
 * - Tracks user review decisions without automatically mutating underlying Pattern or Barrier hypotheses.
 * - Guarantees recoverable candidate identity, causation pointer, scope, and user isolation.
 */
data class CorrectionReviewItem(
    val id: String,
    val userId: String,
    val sourceEventId: String,
    val recommendationId: String,
    val causalHypothesis: CausalHypothesisReference,
    val correctionScope: CorrectionScope,
    val correctedText: String,
    val decisionState: String,
    val reviewStatus: CorrectionReviewStatus,
    val reviewFeedback: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val reviewedAt: Long? = null,
    val schemaVersion: Int = 1
)
