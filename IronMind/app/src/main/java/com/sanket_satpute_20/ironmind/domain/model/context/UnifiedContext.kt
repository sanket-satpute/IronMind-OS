package com.sanket_satpute_20.ironmind.domain.model.context

data class UnifiedContext(
    val userId: String,
    val generatedAt: Long,
    val windowStartTimeMs: Long = 0L,
    val windowEndTimeMs: Long = 0L,
    val productivity: ProductivityContext = ProductivityContext(),
    val digitalBehaviour: DigitalBehaviourContext = DigitalBehaviourContext(
        permissionStatus = UsagePermissionStatus.GRANTED,
        availability = SourceAvailability.AVAILABLE
    ),
    val correlations: List<CrossDomainCorrelation> = emptyList(),
    val provenanceMap: Map<String, SourceProvenanceAndFreshness> = emptyMap(),
    val isFresh: Boolean = true,
    val isDegraded: Boolean = false
)
