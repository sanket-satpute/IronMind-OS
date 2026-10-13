package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus

fun InterventionRecommendationEntity.toDomain(): InterventionRecommendation? {
    if (objective == null) return null
    
    val parsedObjective = try {
        com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.valueOf(objective)
    } catch (e: IllegalArgumentException) {
        return null
    }

    val parsedCausalHypothesis = if (!causalHypothesisId.isNullOrBlank() && !causalHypothesisType.isNullOrBlank()) {
        try {
            val type = com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType.valueOf(causalHypothesisType)
            com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference(
                id = causalHypothesisId,
                type = type
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    } else {
        null
    }

    return InterventionRecommendation(
        id = id,
        userId = userId,
        interventionType = InterventionType.valueOf(interventionType),
        objective = parsedObjective,
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        rationale = rationale,
        suggestedAction = suggestedAction,
        status = InterventionRecommendationStatus.valueOf(status),
        createdAt = createdAt,
        expiresAt = expiresAt,
        causalHypothesis = parsedCausalHypothesis
    )
}

fun InterventionRecommendation.toEntity(): InterventionRecommendationEntity = InterventionRecommendationEntity(
    id = id,
    userId = userId,
    interventionType = interventionType.name,
    objective = objective.name,
    targetEntityId = targetEntityId,
    targetEntityType = targetEntityType,
    rationale = rationale,
    suggestedAction = suggestedAction,
    status = status.name,
    createdAt = createdAt,
    expiresAt = expiresAt,
    causalHypothesisId = causalHypothesis?.id,
    causalHypothesisType = causalHypothesis?.type?.name
)
