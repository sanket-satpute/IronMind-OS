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
 * Explicit result contract for app usage queries, distinguishing success, empty results,
 * permission status, hardware/provider availability, and query failure.
 */
sealed interface AppUsageProviderResult {
    data class Success(
        val events: List<AppUsageEvent>,
        val activeSession: ActiveForegroundSession? = null,
        val isTruncated: Boolean = false
    ) : AppUsageProviderResult {
        val hasEvents: Boolean get() = events.isNotEmpty()
    }

    data object PermissionDenied : AppUsageProviderResult
    data object ProviderUnavailable : AppUsageProviderResult

    data class Failure(
        val diagnosticCategory: String = "USAGE_STATS_QUERY_FAILED",
        val cause: Throwable? = null
    ) : AppUsageProviderResult
}

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

    /**
     * Explicit query contract returning [AppUsageProviderResult].
     * Distinguishes success (with or without events), permission denial, provider unreachability,
     * and query failure.
     */
    fun queryAppUsage(
        fromTimeMillis: Long,
        toTimeMillis: Long,
        initialActiveSession: ActiveForegroundSession? = null
    ): AppUsageProviderResult {
        if (!isPermissionGranted()) {
            return AppUsageProviderResult.PermissionDenied
        }
        return try {
            val reconstruction = getAppUsageBetween(fromTimeMillis, toTimeMillis, initialActiveSession)
            AppUsageProviderResult.Success(reconstruction.events, reconstruction.activeSession)
        } catch (e: Exception) {
            AppUsageProviderResult.Failure(
                diagnosticCategory = "QUERY_EXECUTION_FAILED",
                cause = e
            )
        }
    }
}
