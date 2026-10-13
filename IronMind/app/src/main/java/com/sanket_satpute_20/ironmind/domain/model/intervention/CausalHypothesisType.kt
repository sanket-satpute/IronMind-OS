package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Discriminator defining the authoritative domain entity types eligible
 * to serve as causal hypotheses for intervention recommendations.
 */
enum class CausalHypothesisType {
    PATTERN,
    BARRIER
}
