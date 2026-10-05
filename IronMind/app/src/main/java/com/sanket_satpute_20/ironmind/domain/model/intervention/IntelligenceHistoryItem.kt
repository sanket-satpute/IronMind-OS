package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType

enum class IntelligenceHistoryResponse {
    ACCEPTED,
    REJECTED,
    IGNORED,
    CORRECTED
}

data class IntelligenceHistoryItem(
    val recommendationTimestamp: Long,
    val interventionType: InterventionType,
    val objective: InterventionObjective,
    val rationale: String,
    val suggestedAction: String,
    val recommendationStatus: InterventionRecommendationStatus,
    val effectiveResponse: IntelligenceHistoryResponse?,
    val responseTimestamp: Long?,
    val correctionText: String?
)
