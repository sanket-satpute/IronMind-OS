package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intervention_recommendations")
data class InterventionRecommendationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val interventionType: String,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val rationale: String,
    val suggestedAction: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long?
)
