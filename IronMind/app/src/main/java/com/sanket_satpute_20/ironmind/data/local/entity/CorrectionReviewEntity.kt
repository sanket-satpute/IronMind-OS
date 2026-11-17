package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope

@Entity(
    tableName = "correction_reviews",
    indices = [
        Index(value = ["userId", "sourceEventId"], unique = true),
        Index(value = ["userId", "reviewStatus"]),
        Index(value = ["userId", "createdAt"])
    ]
)
data class CorrectionReviewEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sourceEventId: String,
    val recommendationId: String,
    val causalHypothesisId: String,
    val causalHypothesisType: String,
    val correctionScope: String,
    val correctedText: String,
    val decisionState: String,
    val reviewStatus: String,
    val reviewFeedback: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val reviewedAt: Long?,
    val schemaVersion: Int = 1
)

fun CorrectionReviewEntity.toDomainModel(): CorrectionReviewItem {
    return CorrectionReviewItem(
        id = id,
        userId = userId,
        sourceEventId = sourceEventId,
        recommendationId = recommendationId,
        causalHypothesis = CausalHypothesisReference(
            id = causalHypothesisId,
            type = CausalHypothesisType.valueOf(causalHypothesisType)
        ),
        correctionScope = CorrectionScope.valueOf(correctionScope),
        correctedText = correctedText,
        decisionState = decisionState,
        reviewStatus = CorrectionReviewStatus.valueOf(reviewStatus),
        reviewFeedback = reviewFeedback,
        createdAt = createdAt,
        updatedAt = updatedAt,
        reviewedAt = reviewedAt,
        schemaVersion = schemaVersion
    )
}

fun CorrectionReviewItem.toEntity(): CorrectionReviewEntity {
    return CorrectionReviewEntity(
        id = id,
        userId = userId,
        sourceEventId = sourceEventId,
        recommendationId = recommendationId,
        causalHypothesisId = causalHypothesis.id,
        causalHypothesisType = causalHypothesis.type.name,
        correctionScope = correctionScope.name,
        correctedText = correctedText,
        decisionState = decisionState,
        reviewStatus = reviewStatus.name,
        reviewFeedback = reviewFeedback,
        createdAt = createdAt,
        updatedAt = updatedAt,
        reviewedAt = reviewedAt,
        schemaVersion = schemaVersion
    )
}
