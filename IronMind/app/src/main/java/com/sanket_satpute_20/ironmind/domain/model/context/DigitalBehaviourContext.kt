package com.sanket_satpute_20.ironmind.domain.model.context

import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession

data class AppUsageSummary(
    val packageName: String,
    val appName: String? = null,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstObservedAt: Long?,
    val lastObservedAt: Long?,
    val category: String? = null,
    val authority: EpistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
)

data class FocusActivitySummary(
    val activeSessions: List<ProtectionSession> = emptyList(),
    val recentSessions: List<ProtectionSession> = emptyList(),
    val totalFocusDurationMs: Long = 0L,
    val activeRules: List<ProtectionRule> = emptyList()
)

data class DigitalBehaviourContext(
    val permissionStatus: UsagePermissionStatus,
    val availability: SourceAvailability,
    val windowStartTimeMs: Long = 0L,
    val windowEndTimeMs: Long = 0L,
    val appUsages: List<AppUsageSummary> = emptyList(),
    val focusActivity: FocusActivitySummary = FocusActivitySummary(),
    val recentProtectionEvents: List<Event> = emptyList(),
    val lastObservedAt: Long? = null,
    val notes: String? = null,
    val usageIntervals: List<AppUsageInterval> = emptyList(),
    val usageTemporalSummary: AppUsageTemporalSummary = AppUsageTemporalSummary()
)
