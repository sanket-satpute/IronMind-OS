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
}
