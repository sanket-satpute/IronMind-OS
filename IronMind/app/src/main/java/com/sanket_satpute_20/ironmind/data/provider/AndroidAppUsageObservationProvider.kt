package com.sanket_satpute_20.ironmind.data.provider

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider

/**
 * Android implementation of [AppUsageObservationProvider].
 * Uses [UsageStatsManager] to query app foreground usage intervals.
 * Observes behavior (which app, for how long) — not content or identity.
 *
 * Requires PACKAGE_USAGE_STATS special permission (system-granted).
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
        if (!isPermissionGranted()) {
            println("IronMindLifecycle [AppUsageObservation] [SKIPPED] reason=permission_not_granted")
            return emptyList()
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()

            val usageEvents = usageStatsManager.queryEvents(fromTimeMillis, now)
            val eventList = mutableListOf<AppUsageEvent>()

            // Track ACTIVITY_RESUMED events to pair with ACTIVITY_PAUSED
            val pendingStart = mutableMapOf<String, Long>()

            val event = android.app.usage.UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val pkg = event.packageName ?: continue

                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        pendingStart[pkg] = event.timeStamp
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val startTime = pendingStart.remove(pkg)
                        if (startTime != null && event.timeStamp > startTime) {
                            eventList.add(
                                AppUsageEvent(
                                    packageName = pkg,
                                    startTimeMillis = startTime,
                                    endTimeMillis = event.timeStamp,
                                    totalDurationMillis = event.timeStamp - startTime
                                )
                            )
                        }
                    }
                }
            }

            // Close any sessions still open (app still in foreground)
            for ((pkg, startTime) in pendingStart) {
                eventList.add(
                    AppUsageEvent(
                        packageName = pkg,
                        startTimeMillis = startTime,
                        endTimeMillis = now,
                        totalDurationMillis = now - startTime
                    )
                )
            }

            println("IronMindLifecycle [AppUsageObservation] [COLLECTED] count=${eventList.size}")
            eventList
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageObservation] [COLLECTION_FAILED] error=${e.message}")
            emptyList()
        }
    }
}
