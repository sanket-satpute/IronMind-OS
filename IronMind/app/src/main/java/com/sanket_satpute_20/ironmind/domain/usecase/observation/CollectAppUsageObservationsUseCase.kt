package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

/**
 * Collects app usage observations from the OS and persists them.
 *
 * Guards:
 * 1. isEnabled must be true in [AppUsageObservationSettings]
 * 2. Android PACKAGE_USAGE_STATS permission must be granted
 *
 * Only runs if both conditions are met. Silently returns empty if either is missing.
 * This ensures the user's consent is always respected before any data is collected.
 */
class CollectAppUsageObservationsUseCase(
    private val settingsRepository: AppUsageObservationSettingsRepository,
    private val observationRepository: ObservationRepository,
    private val appUsageObservationProvider: AppUsageObservationProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) : ObservationCollector {
    override val collectorName: String = "AppUsage"

    // Lookback window: collect the last hour of app usage events each run
    private val lookbackWindowMillis = 60 * 60 * 1000L // 1 hour

    override suspend operator fun invoke(userId: String): Result<Int, Exception> {
        return try {
            // Guard 1: user consent
            val settingsResult = settingsRepository.getSettings(userId)
            if (settingsResult is Result.Failure) return Result.Failure(settingsResult.error)
            val settings = (settingsResult as Result.Success).data

            if (!settings.isEnabled) {
                println("IronMindLifecycle [AppUsageObservation] [SKIPPED] reason=disabled userId=$userId")
                return Result.Success(0)
            }

            // Guard 2: OS permission
            if (!appUsageObservationProvider.isPermissionGranted()) {
                println("IronMindLifecycle [AppUsageObservation] [SKIPPED] reason=permission_not_granted userId=$userId")
                return Result.Success(0)
            }

            val fromTime = clock.currentTimeMillis() - lookbackWindowMillis
            val events = appUsageObservationProvider.getAppUsageSince(fromTime)

            var savedCount = 0
            val now = clock.currentTimeMillis()

            for (event in events) {
                // Use deterministic ID to prevent duplicates across runs for the same event
                val deterministicId = java.util.UUID.nameUUIDFromBytes(
                    "appusage_${userId}_${event.packageName}_${event.startTimeMillis}".toByteArray()
                ).toString()

                val observation = Observation(
                    id = deterministicId,
                    userId = userId,
                    type = ObservationType.APP_USAGE_SESSION,
                    source = ObservationSource.APP_USAGE,
                    occurredAt = event.startTimeMillis,
                    recordedAt = now,
                    subjectId = null,
                    value = event.packageName,
                    context = "duration_ms=${event.totalDurationMillis}",
                    confidence = null,
                    provenance = ObservationProvenance(
                        source = ObservationSource.APP_USAGE,
                        sourceReference = "UsageStatsManager",
                        capturedAt = now
                    )
                )
                val insertResult = observationRepository.insertObservation(observation)
                if (insertResult is Result.Success) savedCount++
            }

            println("IronMindLifecycle [AppUsageObservation] [COLLECTED] userId=$userId count=$savedCount")
            Result.Success(savedCount)
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageObservation] [COLLECT_FAILED] userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }
}
