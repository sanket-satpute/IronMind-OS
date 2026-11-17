package com.sanket_satpute_20.ironmind.domain.model.briefing

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority

/**
 * Phase 3.5: Represents an obstacle/barrier hypothesis that has sufficient evidence.
 *
 * Distinguishes AI hypotheses with sufficient evidence from user-confirmed barriers.
 */
data class EvidenceBackedBarrierItem(
    val barrierId: String,
    val category: BarrierCategory,
    val description: String,
    val confidence: Float,
    val supportingEvidenceCount: Int,
    val isUserConfirmed: Boolean,
    val epistemicAuthority: EpistemicAuthority
)
