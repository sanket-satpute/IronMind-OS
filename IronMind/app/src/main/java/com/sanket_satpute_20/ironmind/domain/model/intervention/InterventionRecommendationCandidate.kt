package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType

/**
 * Domain-level transient candidate representing untrusted AI output.
 * It is NOT a persisted domain recommendation and is NOT executable.
 */
data class InterventionRecommendationCandidate(
    val interventionType: InterventionType,
    val rationale: String,
    val suggestedAction: String,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val confidence: Float
)
