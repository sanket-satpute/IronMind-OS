package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType

data class InterventionRecord(
    val id: String,
    val userId: String,
    val type: InterventionType,
    val state: InterventionState,
    val title: String,
    val description: String,
    val resolutionReason: InterventionResolutionReason? = null,
    val contextData: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
