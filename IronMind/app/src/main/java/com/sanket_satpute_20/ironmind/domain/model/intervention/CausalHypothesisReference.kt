package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Strongly-typed domain pointer identifying the single primary causal hypothesis
 * that materially informed the generation of an intervention recommendation.
 */
data class CausalHypothesisReference(
    val id: String,
    val type: CausalHypothesisType
) {
    init {
        require(id.isNotBlank()) { "CausalHypothesisReference id must not be blank" }
    }
}
