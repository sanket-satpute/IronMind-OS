package com.sanket_satpute_20.ironmind.domain.model.context

enum class CrossDomainCorrelationType {
    CONCURRENT_DIGITAL_ACTIVITY_DURING_PENDING_TASK,
    HIGH_USAGE_COINCIDING_WITH_MISSED_COMMITMENT,
    FOCUS_PROTECTION_DURING_COMPLETED_TASK,
    UNSCHEDULED_APP_SPIKE_DURING_GOAL_WINDOW
}

data class CrossDomainCorrelation(
    val id: String,
    val title: String,
    val description: String,
    val productivityEntityId: String,
    val productivityEntityType: String, // "TASK", "COMMITMENT", "GOAL", "PLAN"
    val productivityEntityTitle: String,
    val digitalAppPackage: String?,
    val digitalUsageDurationMs: Long?,
    val observedWindowStartMs: Long,
    val observedWindowEndMs: Long,
    val correlationType: CrossDomainCorrelationType,
    val confidence: Float,
    val authority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
    val disclaimer: String = "Tentative correlation hypothesis only. Does not imply causation, intent, or confirmed distraction."
)
