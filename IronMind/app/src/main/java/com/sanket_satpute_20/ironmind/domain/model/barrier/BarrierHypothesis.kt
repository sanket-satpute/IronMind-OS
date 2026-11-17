package com.sanket_satpute_20.ironmind.domain.model.barrier

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference

/**
 * Sprint V2.9 & Phase 3.3: The domain representation of a Barrier Hypothesis.
 * Strictly distinguishes AI hypotheses from user-confirmed barriers.
 */
data class BarrierHypothesis(
    val id: String,
    val userId: String,
    val category: BarrierCategory,
    val description: String,
    val confirmationState: BarrierConfirmationState,
    val status: BarrierStatus,
    val sourceReflectionId: String,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val title: String = "",
    val supportingPatternIds: List<String> = emptyList(),
    val evidenceReferences: List<EvidenceReference> = emptyList(),
    val evidenceCount: Int = 0,
    val distinctDays: Int = 0,
    val confidence: Float = 0.5f,
    val epistemicAuthority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
    val sourceProvenance: List<String> = emptyList(),
    val sourceFreshness: SourceFreshness = SourceFreshness.CURRENT
)
