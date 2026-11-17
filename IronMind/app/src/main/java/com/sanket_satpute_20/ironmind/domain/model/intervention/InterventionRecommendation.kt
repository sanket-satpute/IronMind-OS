package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority

/**
 * The validated domain representation of an intervention recommendation.
 */
data class InterventionRecommendation(
    val id: String,
    val userId: String,
    val interventionType: InterventionType,
    val objective: com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val rationale: String,
    val suggestedAction: String,
    val status: InterventionRecommendationStatus = InterventionRecommendationStatus.PENDING,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val causalHypothesis: CausalHypothesisReference? = null,
    val expectedOutcome: String? = null,
    val suggestedMeasurement: String? = null,
    val epistemicAuthority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
)
