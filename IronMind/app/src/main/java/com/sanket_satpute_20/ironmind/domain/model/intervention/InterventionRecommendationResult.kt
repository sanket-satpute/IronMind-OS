package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Result representation for intervention recommendation generation.
 */
sealed interface InterventionRecommendationResult {

    /**
     * Domain-level absence of an eligible recommendation.
     */
    data object NoRecommendation : InterventionRecommendationResult

    /**
     * A validated recommendation was generated.
     */
    data class Recommended(
        val recommendation: InterventionRecommendation
    ) : InterventionRecommendationResult
}
