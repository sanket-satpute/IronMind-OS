package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.digital.AppUsageItem
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyAppUsageIntelligence
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyUsageComparison
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Use case to compute factual Daily App Usage Intelligence for a user on a given day.
 *
 * Epistemic & Architectural Guarantees:
 * - Missing Usage Access permission is never mapped to 0ms usage; it is reported as null duration
 *   with PERMISSION_DENIED or PERMISSION_NOT_REQUESTED availability.
 * - Empty usage with valid permission is explicitly distinguished (EMPTY_NO_DATA with 0ms).
 * - High usage applications are never branded with judgmental labels ("distracting", "addictive", "lazy").
 * - Comparisons against previous days are only computed when sufficient baseline data actually exists.
 * - Ties in rank ordering are deterministically broken using packageName ascending.
 * - Statistics reflect on-demand sampled queries, not real-time event streaming.
 */
class GetDailyAppUsageIntelligenceUseCase(
    private val appUsageObservationProvider: AppUsageObservationProvider? = null,
    private val appUsageSettingsRepository: AppUsageObservationSettingsRepository? = null,
    private val observationRepository: ObservationRepository? = null,
    private val clock: Clock
) {
    companion object {
        const val DEFAULT_TOP_APPS_LIMIT = 5
        const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    suspend operator fun invoke(
        userId: String,
        targetDayMillis: Long = clock.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        customWindowStartMs: Long? = null,
        customWindowEndMs: Long? = null,
        topAppsLimit: Int = DEFAULT_TOP_APPS_LIMIT
    ): Result<DailyAppUsageIntelligence, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }

        val (windowStartMs, windowEndMs) = if (customWindowStartMs != null && customWindowEndMs != null) {
            if (customWindowStartMs >= customWindowEndMs) {
                return Result.Failure(IllegalArgumentException("customWindowStartMs must be less than customWindowEndMs"))
            }
            Pair(customWindowStartMs, customWindowEndMs)
        } else {
            val localDate = Instant.ofEpochMilli(targetDayMillis).atZone(zoneId).toLocalDate()
            val startOfDay = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfDay = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            Pair(startOfDay, endOfDay)
        }

        val now = clock.currentTimeMillis()

        // 1. Consent check: User setting toggle
        val isSettingEnabled = appUsageSettingsRepository?.getSettings(userId)?.let {
            if (it is Result.Success) it.data.isEnabled else true
        } ?: true

        if (!isSettingEnabled) {
            return Result.Success(
                DailyAppUsageIntelligence(
                    userId = userId,
                    targetDayStartMs = windowStartMs,
                    targetDayEndMs = windowEndMs,
                    permissionStatus = UsagePermissionStatus.DENIED,
                    availability = SourceAvailability.PERMISSION_NOT_REQUESTED,
                    totalObservedDurationMs = null, // Strictly null, NEVER zero
                    totalSessionsCount = null,
                    appUsages = emptyList(),
                    mostUsedApps = emptyList(),
                    comparisonWithPreviousDay = null,
                    dataFreshnessMs = null,
                    isFresh = false,
                    isRealTime = false,
                    notes = "App usage observation is disabled in user settings."
                )
            )
        }

        // 2. Platform Permission check: PACKAGE_USAGE_STATS
        val isPermissionGranted = appUsageObservationProvider?.isPermissionGranted() ?: false
        if (!isPermissionGranted) {
            val availability = if (appUsageObservationProvider == null) {
                SourceAvailability.UNAVAILABLE_HARDWARE
            } else {
                SourceAvailability.PERMISSION_DENIED
            }
            val note = if (appUsageObservationProvider == null) {
                "UsageStatsManager is not available on this device."
            } else {
                "Usage Access permission (PACKAGE_USAGE_STATS) is not granted in Android system settings."
            }
            return Result.Success(
                DailyAppUsageIntelligence(
                    userId = userId,
                    targetDayStartMs = windowStartMs,
                    targetDayEndMs = windowEndMs,
                    permissionStatus = UsagePermissionStatus.DENIED,
                    availability = availability,
                    totalObservedDurationMs = null, // Strictly null, NEVER zero
                    totalSessionsCount = null,
                    appUsages = emptyList(),
                    mostUsedApps = emptyList(),
                    comparisonWithPreviousDay = null,
                    dataFreshnessMs = null,
                    isFresh = false,
                    isRealTime = false,
                    notes = note
                )
            )
        }

        // 3. Collect usage events for the target day window
        val events = collectEventsForWindow(userId, windowStartMs, windowEndMs)

        if (events.isEmpty()) {
            return Result.Success(
                DailyAppUsageIntelligence(
                    userId = userId,
                    targetDayStartMs = windowStartMs,
                    targetDayEndMs = windowEndMs,
                    permissionStatus = UsagePermissionStatus.GRANTED,
                    availability = SourceAvailability.EMPTY_NO_DATA,
                    totalObservedDurationMs = 0L, // Valid observed 0ms because permission was granted
                    totalSessionsCount = 0,
                    appUsages = emptyList(),
                    mostUsedApps = emptyList(),
                    comparisonWithPreviousDay = null,
                    dataFreshnessMs = null,
                    isFresh = true,
                    isRealTime = false,
                    notes = "No app usage events observed for this day window."
                )
            )
        }

        // 4. Group and compute per-app usage metrics
        val grouped = events.groupBy { it.packageName }
        val appItems = mutableListOf<AppUsageItem>()
        var totalDayDurationMs = 0L
        var latestEventEndTime: Long? = null

        for ((pkg, pkgEvents) in grouped) {
            var pkgDuration = 0L
            var firstObserved: Long? = null
            var lastObserved: Long? = null

            for (ev in pkgEvents) {
                // Clamping to target day window boundaries for strict mathematical accuracy
                val clampedStart = maxOf(ev.startTimeMillis, windowStartMs)
                val clampedEnd = minOf(ev.endTimeMillis, windowEndMs)
                val duration = (clampedEnd - clampedStart).coerceAtLeast(0L)

                pkgDuration += duration

                if (firstObserved == null || ev.startTimeMillis < firstObserved) {
                    firstObserved = ev.startTimeMillis
                }
                if (lastObserved == null || ev.endTimeMillis > lastObserved) {
                    lastObserved = ev.endTimeMillis
                }
            }

            if (lastObserved != null && (latestEventEndTime == null || lastObserved > latestEventEndTime)) {
                latestEventEndTime = lastObserved
            }

            totalDayDurationMs += pkgDuration

            appItems.add(
                AppUsageItem(
                    packageName = pkg,
                    appName = null,
                    totalDurationMs = pkgDuration,
                    sessionCount = pkgEvents.size,
                    firstObservedAt = firstObserved,
                    lastObservedAt = lastObserved,
                    epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
                )
            )
        }

        // Deterministic sort: totalDurationMs descending, then packageName ascending
        val sortedAppItems = appItems
            .map { item ->
                val percentage = if (totalDayDurationMs > 0L) {
                    (item.totalDurationMs.toFloat() / totalDayDurationMs.toFloat()) * 100f
                } else 0f
                item.copy(percentageOfTotal = percentage)
            }
            .sortedWith(
                compareByDescending<AppUsageItem> { it.totalDurationMs }
                    .thenBy { it.packageName }
            )

        val mostUsed = sortedAppItems.take(topAppsLimit)

        // 5. Previous day comparison (only when prior baseline data exists)
        val dayWindowDuration = windowEndMs - windowStartMs
        val prevDayStartMs = windowStartMs - dayWindowDuration
        val prevDayEndMs = windowStartMs

        val previousDayEvents = collectEventsForWindow(userId, prevDayStartMs, prevDayEndMs)
        val comparison = if (previousDayEvents.isNotEmpty()) {
            var prevTotalMs = 0L
            for (ev in previousDayEvents) {
                val clampedStart = maxOf(ev.startTimeMillis, prevDayStartMs)
                val clampedEnd = minOf(ev.endTimeMillis, prevDayEndMs)
                prevTotalMs += (clampedEnd - clampedStart).coerceAtLeast(0L)
            }

            val deltaMs = totalDayDurationMs - prevTotalMs
            val percentageDelta = if (prevTotalMs > 0L) {
                ((deltaMs.toFloat()) / prevTotalMs.toFloat()) * 100f
            } else null

            val note = formatComparisonNote(deltaMs, percentageDelta)

            DailyUsageComparison(
                previousDayDurationMs = prevTotalMs,
                currentDayDurationMs = totalDayDurationMs,
                durationDeltaMs = deltaMs,
                percentageDelta = percentageDelta,
                comparisonNote = note
            )
        } else {
            null // Baseline missing or empty: do not fabricate false comparison
        }

        val freshnessMs = latestEventEndTime?.let { (now - it).coerceAtLeast(0L) }
        val isFresh = freshnessMs == null || freshnessMs <= STALE_THRESHOLD_MS

        return Result.Success(
            DailyAppUsageIntelligence(
                userId = userId,
                targetDayStartMs = windowStartMs,
                targetDayEndMs = windowEndMs,
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                totalObservedDurationMs = totalDayDurationMs,
                totalSessionsCount = events.size,
                appUsages = sortedAppItems,
                mostUsedApps = mostUsed,
                comparisonWithPreviousDay = comparison,
                dataFreshnessMs = freshnessMs,
                isFresh = isFresh,
                isRealTime = false,
                notes = "Observed ${sortedAppItems.size} applications over ${formatDuration(totalDayDurationMs)}."
            )
        )
    }

    private suspend fun collectEventsForWindow(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): List<AppUsageEvent> {
        val resultList = mutableListOf<AppUsageEvent>()

        // Source A: On-demand UsageStatsManager query via AppUsageObservationProvider
        if (appUsageObservationProvider != null && appUsageObservationProvider.isPermissionGranted()) {
            val providerEvents = appUsageObservationProvider.getAppUsageSince(startTimeMs)
            val windowEvents = providerEvents.filter {
                it.startTimeMillis < endTimeMs && it.endTimeMillis >= startTimeMs
            }
            resultList.addAll(windowEvents)
        }

        // Source B: ObservationRepository fallback if on-demand query returned nothing (e.g., historical day)
        if (resultList.isEmpty() && observationRepository != null) {
            val obsResult = observationRepository.getObservationsForTimeWindow(
                userId = userId,
                type = ObservationType.APP_USAGE_SESSION,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
            if (obsResult is Result.Success) {
                for (obs in obsResult.data) {
                    if (obs.userId != userId) continue
                    val durationStr = obs.context.removePrefix("duration_ms=")
                    val duration = durationStr.toLongOrNull() ?: 0L
                    resultList.add(
                        AppUsageEvent(
                            packageName = obs.value,
                            startTimeMillis = obs.occurredAt,
                            endTimeMillis = obs.occurredAt + duration,
                            totalDurationMillis = duration
                        )
                    )
                }
            }
        }

        return resultList
    }

    private fun formatComparisonNote(deltaMs: Long, percentageDelta: Float?): String {
        val absDeltaFormatted = formatDuration(abs(deltaMs))
        val pctFormatted = percentageDelta?.let { String.format("%.1f%%", abs(it)) }

        return when {
            deltaMs > 0L -> {
                if (pctFormatted != null) {
                    "Total usage is $absDeltaFormatted (+$pctFormatted) higher than the previous day."
                } else {
                    "Total usage is $absDeltaFormatted higher than the previous day."
                }
            }
            deltaMs < 0L -> {
                if (pctFormatted != null) {
                    "Total usage is $absDeltaFormatted (-$pctFormatted) lower than the previous day."
                } else {
                    "Total usage is $absDeltaFormatted lower than the previous day."
                }
            }
            else -> "Total usage is identical to the previous day."
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 60000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
