package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus

fun InterventionRecommendationEntity.toDomain(): InterventionRecommendation = InterventionRecommendation(
    id = id,
    userId = userId,
    interventionType = InterventionType.valueOf(interventionType),
    targetEntityId = targetEntityId,
    targetEntityType = targetEntityType,
    rationale = rationale,
    suggestedAction = suggestedAction,
    status = InterventionRecommendationStatus.valueOf(status),
    createdAt = createdAt,
    expiresAt = expiresAt
)

fun InterventionRecommendation.toEntity(): InterventionRecommendationEntity = InterventionRecommendationEntity(
    id = id,
    userId = userId,
    interventionType = interventionType.name,
    targetEntityId = targetEntityId,
    targetEntityType = targetEntityType,
    rationale = rationale,
    suggestedAction = suggestedAction,
    status = status.name,
    createdAt = createdAt,
    expiresAt = expiresAt
)
