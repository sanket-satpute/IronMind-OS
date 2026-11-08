package com.sanket_satpute_20.ironmind.domain.provider

/**
 * A discrete app usage session: one app, one foreground period.
 * Only the package name and timing are recorded — not the content of the app.
 * This observes behavior, not identity.
 */
data class AppUsageEvent(
    val packageName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val totalDurationMillis: Long
)

/**
 * Foreground session state preserved across observation collection windows.
 */
data class ActiveForegroundSession(
    val packageName: String,
    val startTimeMillis: Long
)

/**
 * Result of reconstructing app usage events over a bounded time window.
 */
data class AppUsageReconstructionResult(
    val events: List<AppUsageEvent>,
    val activeSession: ActiveForegroundSession?
)

/**
 * Abstraction over the Android UsageStatsManager.
 * Hides the platform dependency from the domain layer.
 */
interface AppUsageObservationProvider {
    /**
     * Returns true if the PACKAGE_USAGE_STATS permission has been granted
     * by the user via Settings → Special App Access → Usage Access.
     */
    fun isPermissionGranted(): Boolean

    /**
     * Returns app usage events since [fromTimeMillis].
     * Returns an empty list if permission is not granted or data is unavailable.
     */
    fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent>

    /**
     * Returns reconstructed app usage events between [fromTimeMillis] and [toTimeMillis].
     *
     * If an app was already in the foreground at [fromTimeMillis], its state is supplied in [initialActiveSession].
     * If an app is still in the foreground at [toTimeMillis], its state is returned in [AppUsageReconstructionResult.activeSession].
     */
    fun getAppUsageBetween(
        fromTimeMillis: Long,
        toTimeMillis: Long,
        initialActiveSession: ActiveForegroundSession? = null
    ): AppUsageReconstructionResult {
        val events = getAppUsageSince(fromTimeMillis).filter {
            it.startTimeMillis < toTimeMillis && it.endTimeMillis >= fromTimeMillis
        }
        return AppUsageReconstructionResult(events, null)
    }
}
