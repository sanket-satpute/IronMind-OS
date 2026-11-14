package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IntervalMath
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.AppUsageIntervalAnalyzer
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.digital.AppUsageItem
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyAppUsageIntelligence
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyUsageComparison
import com.sanket_satpute_20.ironmind.domain.model.digital.UsageSourceProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageProviderResult
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
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
    private val clock: Clock,
    private val checkpointStore: AppUsageCheckpointStore? = null
) {
    companion object {
        const val DEFAULT_TOP_APPS_LIMIT = 5
        const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private sealed interface WindowCollectionResult {
        data class Success(
            val events: List<AppUsageEvent>,
            val provenance: UsageSourceProvenance,
            val isPartial: Boolean = false
        ) : WindowCollectionResult

        data class EmptyConfirmed(
            val provenance: UsageSourceProvenance
        ) : WindowCollectionResult

        data class PartialWithNoEvents(
            val provenance: UsageSourceProvenance,
            val notes: String? = null
        ) : WindowCollectionResult

        data object PermissionDenied : WindowCollectionResult
        data object ProviderUnavailable : WindowCollectionResult
        data class Failure(
            val diagnosticCategory: String,
            val cause: Throwable? = null
        ) : WindowCollectionResult
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
                    sourceProvenance = UsageSourceProvenance.NONE,
                    notes = "App usage observation is disabled in user settings."
                )
            )
        }

        // 2. Collect and resolve usage events for the target day window
        val collectionResult = collectEventsForWindow(
            userId = userId,
            startTimeMs = windowStartMs,
            endTimeMs = windowEndMs
        )

        val (events, provenance, isPartialCoverage) = when (collectionResult) {
            is WindowCollectionResult.PermissionDenied -> {
                return Result.Success(
                    DailyAppUsageIntelligence(
                        userId = userId,
                        targetDayStartMs = windowStartMs,
                        targetDayEndMs = windowEndMs,
                        permissionStatus = UsagePermissionStatus.DENIED,
                        availability = SourceAvailability.PERMISSION_DENIED,
                        totalObservedDurationMs = null, // Strictly null, NEVER zero
                        totalSessionsCount = null,
                        appUsages = emptyList(),
                        mostUsedApps = emptyList(),
                        comparisonWithPreviousDay = null,
                        dataFreshnessMs = null,
                        isFresh = false,
                        isRealTime = false,
                        sourceProvenance = UsageSourceProvenance.NONE,
                        notes = "Usage Access permission (PACKAGE_USAGE_STATS) is not granted in Android system settings."
                    )
                )
            }
            is WindowCollectionResult.ProviderUnavailable -> {
                return Result.Success(
                    DailyAppUsageIntelligence(
                        userId = userId,
                        targetDayStartMs = windowStartMs,
                        targetDayEndMs = windowEndMs,
                        permissionStatus = UsagePermissionStatus.UNAVAILABLE,
                        availability = SourceAvailability.UNAVAILABLE_HARDWARE,
                        totalObservedDurationMs = null, // Strictly null, NEVER zero
                        totalSessionsCount = null,
                        appUsages = emptyList(),
                        mostUsedApps = emptyList(),
                        comparisonWithPreviousDay = null,
                        dataFreshnessMs = null,
                        isFresh = false,
                        isRealTime = false,
                        sourceProvenance = UsageSourceProvenance.NONE,
                        notes = "UsageStatsManager is not available on this device."
                    )
                )
            }
            is WindowCollectionResult.Failure -> {
                return Result.Success(
                    DailyAppUsageIntelligence(
                        userId = userId,
                        targetDayStartMs = windowStartMs,
                        targetDayEndMs = windowEndMs,
                        permissionStatus = UsagePermissionStatus.GRANTED,
                        availability = SourceAvailability.UNAVAILABLE_HARDWARE,
                        totalObservedDurationMs = null, // Strictly null, NEVER zero on query failure
                        totalSessionsCount = null,
                        appUsages = emptyList(),
                        mostUsedApps = emptyList(),
                        comparisonWithPreviousDay = null,
                        dataFreshnessMs = null,
                        isFresh = false,
                        isRealTime = false,
                        sourceProvenance = UsageSourceProvenance.NONE,
                        notes = "App usage query failed: ${collectionResult.diagnosticCategory}"
                    )
                )
            }
            is WindowCollectionResult.PartialWithNoEvents -> {
                return Result.Success(
                    DailyAppUsageIntelligence(
                        userId = userId,
                        targetDayStartMs = windowStartMs,
                        targetDayEndMs = windowEndMs,
                        permissionStatus = UsagePermissionStatus.GRANTED,
                        availability = SourceAvailability.PARTIAL_HISTORY,
                        totalObservedDurationMs = null, // Strictly null, NEVER zero on truncated/unknown usage
                        totalSessionsCount = null,
                        appUsages = emptyList(),
                        mostUsedApps = emptyList(),
                        comparisonWithPreviousDay = null,
                        dataFreshnessMs = null,
                        isFresh = true,
                        isRealTime = collectionResult.provenance == UsageSourceProvenance.LIVE_USAGE_STATS,
                        sourceProvenance = collectionResult.provenance,
                        notes = collectionResult.notes ?: "App usage data is incomplete due to query truncation."
                    )
                )
            }
            is WindowCollectionResult.EmptyConfirmed -> {
                return Result.Success(
                    DailyAppUsageIntelligence(
                        userId = userId,
                        targetDayStartMs = windowStartMs,
                        targetDayEndMs = windowEndMs,
                        permissionStatus = UsagePermissionStatus.GRANTED,
                        availability = SourceAvailability.EMPTY_NO_DATA,
                        totalObservedDurationMs = 0L, // Valid observed 0ms because permission was granted and query confirmed empty
                        totalSessionsCount = 0,
                        appUsages = emptyList(),
                        mostUsedApps = emptyList(),
                        comparisonWithPreviousDay = null,
                        dataFreshnessMs = null,
                        isFresh = true,
                        isRealTime = false,
                        sourceProvenance = collectionResult.provenance,
                        notes = "No app usage events observed for this day window."
                    )
                )
            }
            is WindowCollectionResult.Success -> {
                Triple(collectionResult.events, collectionResult.provenance, collectionResult.isPartial)
            }
        }

        // 3. Group and compute per-app usage metrics using authoritative union interval mathematics
        val grouped = events.groupBy { it.packageName }
        val appItems = mutableListOf<AppUsageItem>()
        var latestEventEndTime: Long? = null

        for ((pkg, pkgEvents) in grouped) {
            val validPkgIntervals = mutableListOf<IntervalMath.Interval>()
            var pkgFirstObserved: Long? = null
            var pkgLastObserved: Long? = null

            // Semantic deduplication per package: distinctBy (packageName, startTimeMillis, rawEndMs)
            val deduplicatedEvents = pkgEvents
                .mapNotNull { ev ->
                    val pkgName = ev.packageName.trim()
                    if (pkgName.isEmpty() || ev.startTimeMillis < 0L) return@mapNotNull null
                    val rawEnd = if (ev.endTimeMillis > ev.startTimeMillis) {
                        ev.endTimeMillis
                    } else if (ev.totalDurationMillis > 0L) {
                        IntervalMath.safeRawEndMs(ev.startTimeMillis, ev.totalDurationMillis)
                    } else null
                    rawEnd?.let { endMs -> Triple(pkgName, ev.startTimeMillis, endMs) }
                }
                .distinct()

            for ((_, rawStart, rawEnd) in deduplicatedEvents) {
                val clipped = IntervalMath.clipInterval(rawStart, rawEnd, windowStartMs, windowEndMs) ?: continue
                validPkgIntervals.add(clipped)
                if (pkgFirstObserved == null || clipped.startMs < pkgFirstObserved) {
                    pkgFirstObserved = clipped.startMs
                }
                if (pkgLastObserved == null || clipped.endMs > pkgLastObserved) {
                    pkgLastObserved = clipped.endMs
                }
            }

            if (validPkgIntervals.isEmpty()) continue

            // Union overlapping and adjacent intervals so no millisecond within this package is double-counted
            val mergedIntervals = IntervalMath.mergeIntervals(validPkgIntervals)
            val pkgDuration = mergedIntervals.sumOf { it.durationMs }
            if (pkgDuration <= 0L) continue

            if (pkgLastObserved != null && (latestEventEndTime == null || pkgLastObserved > latestEventEndTime)) {
                latestEventEndTime = pkgLastObserved
            }

            appItems.add(
                AppUsageItem(
                    packageName = pkg,
                    appName = null,
                    totalDurationMs = pkgDuration,
                    sessionCount = validPkgIntervals.size,
                    firstObservedAt = pkgFirstObserved,
                    lastObservedAt = pkgLastObserved,
                    epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
                    sourceProvenance = provenance
                )
            )
        }

        if (appItems.isEmpty()) {
            val availability = if (isPartialCoverage) SourceAvailability.PARTIAL_HISTORY else SourceAvailability.EMPTY_NO_DATA
            val totalDuration = if (isPartialCoverage) null else 0L
            val sessionCount = if (isPartialCoverage) null else 0
            val emptyNote = if (isPartialCoverage) {
                "App usage data is incomplete for this window."
            } else {
                "No app usage events observed for this day window."
            }
            return Result.Success(
                DailyAppUsageIntelligence(
                    userId = userId,
                    targetDayStartMs = windowStartMs,
                    targetDayEndMs = windowEndMs,
                    permissionStatus = UsagePermissionStatus.GRANTED,
                    availability = availability,
                    totalObservedDurationMs = totalDuration,
                    totalSessionsCount = sessionCount,
                    appUsages = emptyList(),
                    mostUsedApps = emptyList(),
                    comparisonWithPreviousDay = null,
                    dataFreshnessMs = null,
                    isFresh = true,
                    isRealTime = false,
                    sourceProvenance = provenance,
                    notes = emptyNote
                )
            )
        }

        val totalDayDurationMs = appItems.sumOf { it.totalDurationMs }
        val totalSessionsCount = appItems.sumOf { it.sessionCount }

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

        // 4. Previous day comparison (only when prior baseline data exists)
        val (prevDayStartMs, prevDayEndMs) = if (customWindowStartMs != null && customWindowEndMs != null) {
            val dayWindowDuration = windowEndMs - windowStartMs
            Pair(windowStartMs - dayWindowDuration, windowStartMs)
        } else {
            val localDate = Instant.ofEpochMilli(targetDayMillis).atZone(zoneId).toLocalDate()
            val prevLocalDate = localDate.minusDays(1)
            val prevStart = prevLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            Pair(prevStart, windowStartMs)
        }

        val previousDayResult = collectEventsForWindow(
            userId = userId,
            startTimeMs = prevDayStartMs,
            endTimeMs = prevDayEndMs
        )

        val comparison = if (previousDayResult is WindowCollectionResult.Success) {
            val previousDayEvents = previousDayResult.events
            val prevGrouped = previousDayEvents.groupBy { it.packageName }
            var prevTotalMs = 0L
            var hasPrevValidData = false

            for ((_, prevEvents) in prevGrouped) {
                val deduplicated = prevEvents
                    .mapNotNull { ev ->
                        val pkgName = ev.packageName.trim()
                        if (pkgName.isEmpty() || ev.startTimeMillis < 0L) return@mapNotNull null
                        val rawEnd = if (ev.endTimeMillis > ev.startTimeMillis) {
                            ev.endTimeMillis
                        } else if (ev.totalDurationMillis > 0L) {
                            IntervalMath.safeRawEndMs(ev.startTimeMillis, ev.totalDurationMillis)
                        } else null
                        rawEnd?.let { endMs -> Triple(pkgName, ev.startTimeMillis, endMs) }
                    }
                    .distinct()

                val clippedIntervals = deduplicated.mapNotNull { (_, rawStart, rawEnd) ->
                    IntervalMath.clipInterval(rawStart, rawEnd, prevDayStartMs, prevDayEndMs)
                }

                if (clippedIntervals.isNotEmpty()) {
                    val unionDuration = IntervalMath.unionDurationMs(clippedIntervals)
                    if (unionDuration > 0L) {
                        hasPrevValidData = true
                        prevTotalMs += unionDuration
                    }
                }
            }

            if (hasPrevValidData) {
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
                null
            }
        } else {
            null // Baseline missing or empty: do not fabricate false comparison
        }

        val freshnessMs = latestEventEndTime?.let { (now - it).coerceAtLeast(0L) }
        val isFresh = freshnessMs == null || freshnessMs <= STALE_THRESHOLD_MS
        val isRealTime = provenance == UsageSourceProvenance.LIVE_USAGE_STATS
        val availability = if (isPartialCoverage) {
            SourceAvailability.PARTIAL_HISTORY
        } else {
            SourceAvailability.AVAILABLE
        }
        val note = when (provenance) {
            UsageSourceProvenance.ROOM_PERSISTED_OBSERVATIONS ->
                "Retrieved ${sortedAppItems.size} applications from persisted observations over ${formatDuration(totalDayDurationMs)}."
            else ->
                "Observed ${sortedAppItems.size} applications over ${formatDuration(totalDayDurationMs)}."
        }

        return Result.Success(
            DailyAppUsageIntelligence(
                userId = userId,
                targetDayStartMs = windowStartMs,
                targetDayEndMs = windowEndMs,
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = availability,
                totalObservedDurationMs = totalDayDurationMs,
                totalSessionsCount = totalSessionsCount,
                appUsages = sortedAppItems,
                mostUsedApps = mostUsed,
                comparisonWithPreviousDay = comparison,
                dataFreshnessMs = freshnessMs,
                isFresh = isFresh,
                isRealTime = isRealTime,
                sourceProvenance = provenance,
                notes = note
            )
        )
    }

    private suspend fun collectEventsForWindow(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): WindowCollectionResult {
        // Source A: On-demand UsageStatsManager query via AppUsageObservationProvider
        if (appUsageObservationProvider == null) {
            val roomResult = fetchRoomEvents(userId, startTimeMs, endTimeMs)
            return if (roomResult is Result.Success && roomResult.data.isNotEmpty()) {
                val isPartial = isRoomCheckpointPartial(userId, startTimeMs, endTimeMs)
                WindowCollectionResult.Success(
                    events = roomResult.data,
                    provenance = UsageSourceProvenance.ROOM_PERSISTED_OBSERVATIONS,
                    isPartial = isPartial
                )
            } else {
                WindowCollectionResult.ProviderUnavailable
            }
        }

        if (!appUsageObservationProvider.isPermissionGranted()) {
            return WindowCollectionResult.PermissionDenied
        }

        val providerResult = appUsageObservationProvider.queryAppUsage(startTimeMs, endTimeMs)
        return when (providerResult) {
            is AppUsageProviderResult.PermissionDenied -> {
                WindowCollectionResult.PermissionDenied
            }
            is AppUsageProviderResult.ProviderUnavailable -> {
                val roomResult = fetchRoomEvents(userId, startTimeMs, endTimeMs)
                if (roomResult is Result.Success && roomResult.data.isNotEmpty()) {
                    val isPartial = isRoomCheckpointPartial(userId, startTimeMs, endTimeMs)
                    WindowCollectionResult.Success(
                        events = roomResult.data,
                        provenance = UsageSourceProvenance.ROOM_PERSISTED_OBSERVATIONS,
                        isPartial = isPartial
                    )
                } else {
                    WindowCollectionResult.ProviderUnavailable
                }
            }
            is AppUsageProviderResult.Failure -> {
                // Provider query failed with an exception: check if Room fallback has records
                val roomResult = fetchRoomEvents(userId, startTimeMs, endTimeMs)
                if (roomResult is Result.Success && roomResult.data.isNotEmpty()) {
                    val isPartial = isRoomCheckpointPartial(userId, startTimeMs, endTimeMs)
                    WindowCollectionResult.Success(
                        events = roomResult.data,
                        provenance = UsageSourceProvenance.ROOM_PERSISTED_OBSERVATIONS,
                        isPartial = isPartial
                    )
                } else {
                    // Live query failed and Room has no records: report failure explicitly (never false zero)
                    WindowCollectionResult.Failure(
                        diagnosticCategory = providerResult.diagnosticCategory,
                        cause = providerResult.cause
                    )
                }
            }
            is AppUsageProviderResult.Success -> {
                val filtered = providerResult.events.filter {
                    it.startTimeMillis < endTimeMs && it.endTimeMillis >= startTimeMs
                }
                if (filtered.isNotEmpty()) {
                    val isPartial = providerResult.isTruncated
                    WindowCollectionResult.Success(
                        events = filtered,
                        provenance = UsageSourceProvenance.LIVE_USAGE_STATS,
                        isPartial = isPartial
                    )
                } else {
                    // Live query succeeded with zero events. Check Room fallback:
                    val roomResult = fetchRoomEvents(userId, startTimeMs, endTimeMs)
                    if (roomResult is Result.Success && roomResult.data.isNotEmpty()) {
                        val isPartial = isRoomCheckpointPartial(userId, startTimeMs, endTimeMs)
                        WindowCollectionResult.Success(
                            events = roomResult.data,
                            provenance = UsageSourceProvenance.ROOM_PERSISTED_OBSERVATIONS,
                            isPartial = isPartial
                        )
                    } else if (providerResult.isTruncated) {
                        // Live query was truncated and yielded no filtered events, and Room has no data.
                        // Do not fabricate confirmed 0L / EMPTY_NO_DATA; report partial history with unknown duration.
                        WindowCollectionResult.PartialWithNoEvents(
                            provenance = UsageSourceProvenance.LIVE_USAGE_STATS,
                            notes = "App usage query was truncated by system limit; usage data is incomplete."
                        )
                    } else {
                        WindowCollectionResult.EmptyConfirmed(
                            provenance = UsageSourceProvenance.LIVE_USAGE_STATS
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetchRoomEvents(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<List<AppUsageEvent>, Exception> {
        val repo = observationRepository ?: return Result.Success(emptyList())
        val obsResult = repo.getObservationsForTimeWindow(
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs
        )
        return when (obsResult) {
            is Result.Failure -> Result.Failure(obsResult.error)
            is Result.Success -> {
                val events = mutableListOf<AppUsageEvent>()
                for (obs in obsResult.data) {
                    if (obs.userId != userId) continue
                    val duration = AppUsageIntervalAnalyzer.parseDurationMs(obs.context) ?: continue
                    if (duration <= 0L) continue
                    val rawEnd = IntervalMath.safeRawEndMs(obs.occurredAt, duration) ?: continue
                    events.add(
                        AppUsageEvent(
                            packageName = obs.value,
                            startTimeMillis = obs.occurredAt,
                            endTimeMillis = rawEnd,
                            totalDurationMillis = duration
                        )
                    )
                }
                Result.Success(events)
            }
        }
    }

    private suspend fun isRoomCheckpointPartial(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Boolean {
        val store = checkpointStore ?: return false
        val cp = (store.getCheckpoint(userId) as? Result.Success)?.data ?: return false
        return cp.lastCollectedIntervalEndMs in (startTimeMs + 1) until endTimeMs
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
