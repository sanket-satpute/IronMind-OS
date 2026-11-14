package com.sanket_satpute_20.ironmind.data.provider

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageProviderResult
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult

/**
 * Android implementation of [AppUsageObservationProvider].
 * Uses [UsageStatsManager] to query app foreground usage intervals.
 * Observes behavior (which app, for how long) — not content or identity.
 *
 * Requires PACKAGE_USAGE_STATS special permission (system-granted).
 *
 * Event Handling Details:
 * - Pairs [UsageEvents.Event.ACTIVITY_RESUMED] with [UsageEvents.Event.ACTIVITY_PAUSED] / [UsageEvents.Event.ACTIVITY_STOPPED].
 * - Supports boundary-crossing sessions via [ActiveForegroundSession] restored from persistent checkpoint.
 * - Implicitly closes the preceding foreground application if a different application resumes without an intervening pause.
 * - Maintains earliest start time if the same package emits multiple resume events during internal activity transitions.
 * - Emits open intervals at query boundary [toTimeMillis] without permanently closing the active session.
 */
class AndroidAppUsageObservationProvider(
    private val context: Context
) : AppUsageObservationProvider {

    override fun isPermissionGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageObservation] [PERMISSION_CHECK_FAILED] error=${e.message}")
            false
        }
    }

    override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> {
        return when (val result = queryAppUsage(fromTimeMillis, System.currentTimeMillis(), null)) {
            is AppUsageProviderResult.Success -> result.events
            is AppUsageProviderResult.Failure -> {
                throw (result.cause as? RuntimeException)
                    ?: IllegalStateException("App usage query failed: ${result.diagnosticCategory}", result.cause)
            }
            is AppUsageProviderResult.ProviderUnavailable -> {
                throw IllegalStateException("UsageStatsManager is unavailable")
            }
            is AppUsageProviderResult.PermissionDenied -> emptyList()
        }
    }

    override fun getAppUsageBetween(
        fromTimeMillis: Long,
        toTimeMillis: Long,
        initialActiveSession: ActiveForegroundSession?
    ): AppUsageReconstructionResult {
        return when (val result = queryAppUsage(fromTimeMillis, toTimeMillis, initialActiveSession)) {
            is AppUsageProviderResult.Success -> AppUsageReconstructionResult(
                events = result.events,
                activeSession = result.activeSession
            )
            is AppUsageProviderResult.Failure -> {
                throw (result.cause as? RuntimeException)
                    ?: IllegalStateException("App usage query failed: ${result.diagnosticCategory}", result.cause)
            }
            is AppUsageProviderResult.ProviderUnavailable -> {
                throw IllegalStateException("UsageStatsManager is unavailable")
            }
            is AppUsageProviderResult.PermissionDenied -> {
                AppUsageReconstructionResult(emptyList(), initialActiveSession)
            }
        }
    }

    override fun queryAppUsage(
        fromTimeMillis: Long,
        toTimeMillis: Long,
        initialActiveSession: ActiveForegroundSession?
    ): AppUsageProviderResult {
        if (fromTimeMillis >= toTimeMillis) {
            return AppUsageProviderResult.Success(emptyList(), initialActiveSession)
        }

        if (!isPermissionGranted()) {
            println("IronMindLifecycle [AppUsageObservation] [SKIPPED] reason=permission_not_granted")
            return AppUsageProviderResult.PermissionDenied
        }

        val usageStatsManager = try {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } catch (e: Exception) {
            null
        }

        if (usageStatsManager == null) {
            println("IronMindLifecycle [AppUsageObservation] [UNAVAILABLE] reason=usage_stats_service_null")
            return AppUsageProviderResult.ProviderUnavailable
        }

        return try {
            val usageEvents = usageStatsManager.queryEvents(fromTimeMillis, toTimeMillis)
            if (usageEvents == null) {
                return AppUsageProviderResult.Failure(
                    diagnosticCategory = "USAGE_EVENTS_NULL",
                    cause = null
                )
            }
            val eventList = mutableListOf<AppUsageEvent>()

            // Track active foreground sessions.
            // Key: packageName, Value: startTimeMillis of current foreground period
            val pendingStart = mutableMapOf<String, Long>()
            if (initialActiveSession != null) {
                pendingStart[initialActiveSession.packageName] = initialActiveSession.startTimeMillis
            }

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val eventTime = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // An Android device in standard single-window mode only hosts one foreground app.
                        // If App B resumes while App A was active without an intervening pause event,
                        // implicitly close App A at App B's resume timestamp.
                        val otherApps = pendingStart.keys.filter { it != pkg }
                        for (otherPkg in otherApps) {
                            val startTime = pendingStart.remove(otherPkg)
                            if (startTime != null && eventTime > startTime) {
                                eventList.add(
                                    AppUsageEvent(
                                        packageName = otherPkg,
                                        startTimeMillis = startTime,
                                        endTimeMillis = eventTime,
                                        totalDurationMillis = eventTime - startTime
                                    )
                                )
                            }
                        }

                        // For the resuming app: if it's already active, keep the original start time
                        // so internal activity navigations do not truncate accumulated duration.
                        if (!pendingStart.containsKey(pkg)) {
                            pendingStart[pkg] = eventTime
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val startTime = pendingStart.remove(pkg)
                        if (startTime != null && eventTime > startTime) {
                            eventList.add(
                                AppUsageEvent(
                                    packageName = pkg,
                                    startTimeMillis = startTime,
                                    endTimeMillis = eventTime,
                                    totalDurationMillis = eventTime - startTime
                                )
                            )
                        }
                    }
                }
            }

            // At the end of the query interval [toTimeMillis], account for any app still in the foreground.
            // Emit an open interval for the time up to toTimeMillis, and capture it as activeSession.
            var nextActiveSession: ActiveForegroundSession? = null
            for ((pkg, startTime) in pendingStart) {
                if (toTimeMillis > startTime) {
                    eventList.add(
                        AppUsageEvent(
                            packageName = pkg,
                            startTimeMillis = startTime,
                            endTimeMillis = toTimeMillis,
                            totalDurationMillis = toTimeMillis - startTime
                        )
                    )
                    nextActiveSession = ActiveForegroundSession(
                        packageName = pkg,
                        startTimeMillis = startTime
                    )
                }
            }

            println("IronMindLifecycle [AppUsageObservation] [COLLECTED] window=[$fromTimeMillis, $toTimeMillis] count=${eventList.size} active=${nextActiveSession?.packageName}")
            AppUsageProviderResult.Success(
                events = eventList,
                activeSession = nextActiveSession
            )
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageObservation] [COLLECTION_FAILED] error=${e.message}")
            AppUsageProviderResult.Failure(
                diagnosticCategory = "USAGE_STATS_QUERY_FAILED",
                cause = e
            )
        }
    }
}
