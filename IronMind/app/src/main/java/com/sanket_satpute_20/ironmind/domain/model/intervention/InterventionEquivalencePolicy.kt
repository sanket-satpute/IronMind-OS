package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Pure deterministic policy abstraction for evaluating semantic equivalence
 * and suppression rules for Intervention Recommendations (V2.12).
 */
class InterventionEquivalencePolicy {

    companion object {
        const val REJECTED_COOLDOWN_MS = 24L * 60 * 60 * 1000
        const val IGNORED_COOLDOWN_MS = 6L * 60 * 60 * 1000
    }

    /**
     * Evaluates if two recommendations share the same semantic purpose.
     */
    fun isEquivalent(
        candidate: InterventionRecommendationCandidate,
        history: InterventionRecommendation
    ): Boolean {
        return candidate.objective == history.objective &&
               candidate.targetEntityType == history.targetEntityType &&
               candidate.targetEntityId == history.targetEntityId
    }

    /**
     * Evaluates whether the candidate should be suppressed based on historical equivalents
     * and target completion state.
     */
    fun evaluateSuppression(
        candidate: InterventionRecommendationCandidate,
        historyList: List<InterventionRecommendation>,
        isTargetCompleted: Boolean,
        currentTimeMs: Long
    ): SuppressionResult {
        // 1. Target completion override (hard suppression)
        if (isTargetCompleted) {
            return SuppressionResult.SuppressedTargetCompleted
        }

        // 2. Filter history to semantically equivalent items
        val equivalents = historyList.filter { isEquivalent(candidate, it) }
        
        if (equivalents.isEmpty()) {
            return SuppressionResult.Allowed
        }

        // 3. Precedence check
        // We do NOT silently invent a 'most recent wins' rule if multiple statuses exist.
        val uniqueStatuses = equivalents.map { it.status }.toSet()
        if (uniqueStatuses.size > 1) {
            return SuppressionResult.UnresolvedPrecedence
        }

        // Since statuses are identical (or there's only 1 equivalent), 
        // evaluating the most recent one correctly applies the latest cooldown timer.
        val relevantHistory = equivalents.maxByOrNull { it.createdAt } ?: return SuppressionResult.Allowed

        return when (relevantHistory.status) {
            InterventionRecommendationStatus.PENDING -> {
                SuppressionResult.SuppressedAntiStacking
            }
            InterventionRecommendationStatus.REJECTED -> {
                if (currentTimeMs < relevantHistory.createdAt + REJECTED_COOLDOWN_MS) {
                    SuppressionResult.SuppressedRecentRejection
                } else {
                    SuppressionResult.Allowed
                }
            }
            InterventionRecommendationStatus.IGNORED -> {
                if (currentTimeMs < relevantHistory.createdAt + IGNORED_COOLDOWN_MS) {
                    SuppressionResult.SuppressedRecentIgnored
                } else {
                    SuppressionResult.Allowed
                }
            }
            InterventionRecommendationStatus.ACCEPTED -> SuppressionResult.Allowed
            InterventionRecommendationStatus.EXPIRED -> SuppressionResult.Allowed
        }
    }
}

sealed class SuppressionResult {
    object Allowed : SuppressionResult()
    object SuppressedAntiStacking : SuppressionResult()
    object SuppressedRecentRejection : SuppressionResult()
    object SuppressedRecentIgnored : SuppressionResult()
    object SuppressedTargetCompleted : SuppressionResult()
    object UnresolvedPrecedence : SuppressionResult()
}
