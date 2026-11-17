package com.sanket_satpute_20.ironmind.domain.model.pattern

import com.sanket_satpute_20.ironmind.domain.common.IntelligenceMetadataCodec
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority

data class ObservationWindow(
    val startTimeMs: Long,
    val endTimeMs: Long
)

/**
 * Structured model for an evidence-backed discovered behaviour pattern.
 * Strictly adheres to Phase 3.2 Evidence & Epistemic requirements.
 */
data class DiscoveredPattern(
    val id: String,
    val userId: String,
    val category: PatternCategory,
    val type: PatternDiscoveryType,
    val description: String,
    val observationWindow: ObservationWindow,
    val evidenceReferences: List<EvidenceReference>,
    val evidenceCount: Int,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val confidence: Float,
    val lifecycleState: PatternLifecycleState,
    val epistemicAuthority: EpistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
    val sourceProvenance: List<String> = emptyList(),
    val nonCausalDisclaimer: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Pattern id must not be blank" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(description.isNotBlank()) { "Pattern description must not be blank" }
        require(confidence in 0.0f..1.0f) { "Confidence must be between 0.0 and 1.0" }
        if (category == PatternCategory.CROSS_DOMAIN) {
            require(epistemicAuthority == EpistemicAuthority.AI_HYPOTHESIS) {
                "Cross-domain patterns must be classified as AI_HYPOTHESIS"
            }
            require(!nonCausalDisclaimer.isNullOrBlank()) {
                "Cross-domain patterns must include a non-causal disclaimer"
            }
        }
    }

    /**
     * Converts to the persistent domain [Pattern] for storage in [com.sanket_satpute_20.ironmind.domain.repository.PatternRepository].
     */
    fun toDomainPattern(clockTimeMs: Long = lastObservedAt): Pattern {
        val normalizedDesc = description.trim().lowercase()
        val fingerprint = "${userId}_${category.name}_${type.name}_${normalizedDesc.take(40)}"
        return Pattern(
            id = id,
            userId = userId,
            fingerprint = fingerprint,
            type = type.defaultLegacyType,
            description = description,
            conditions = IntelligenceMetadataCodec.encodePatternConditions(
                category = category,
                lifecycleState = lifecycleState,
                sourceProvenance = sourceProvenance
            ),
            predictedBehavior = nonCausalDisclaimer,
            confidence = confidence,
            evidenceCount = evidenceCount,
            evidenceReferences = evidenceReferences,
            firstObservedAt = firstObservedAt,
            lastObservedAt = lastObservedAt,
            status = lifecycleState.toLegacyPatternStatus(),
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = firstObservedAt,
            updatedAt = clockTimeMs
        )
    }
}
