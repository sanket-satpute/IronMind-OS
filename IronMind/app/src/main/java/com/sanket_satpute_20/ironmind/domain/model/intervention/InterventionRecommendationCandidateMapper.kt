package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput

/**
 * Smallest safe mapping boundary.
 * Drops AI-owned execution permission, status, timestamps, reasoning, etc.
 */
fun AIOutput.InterventionRecommendation.toCandidate(): InterventionRecommendationCandidate {
    return InterventionRecommendationCandidate(
        interventionType = this.interventionType,
        rationale = this.reason,
        suggestedAction = this.recommendation,
        targetEntityId = this.targetEntityId,
        confidence = this.confidence
    )
}
