package com.sanket_satpute_20.ironmind.domain.model.reflection

import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability

/**
 * Factual, window-scoped summary of observed digital usage for end-of-day reflection review.
 * Strictly adheres to epistemic authority distinctions:
 * - Missing permission != zero usage (represented by totalObservedUsageDurationMs = null when permission is denied)
 * - Correlation != causation (focusProtectionOverlapMs represents temporal intersection, not distraction)
 */
data class DigitalBehaviourSummary(
    val isUsagePermissionGranted: Boolean = false,
    val availability: SourceAvailability = SourceAvailability.AVAILABLE,
    val totalObservedUsageDurationMs: Long? = null,
    val topUsedAppPackage: String? = null,
    val topUsedAppName: String? = null,
    val topUsedAppDurationMs: Long? = null,
    val focusProtectionOverlapMs: Long? = null,
    val focusSessionCount: Int = 0,
    val authority: EpistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
    val notes: String? = null
)
