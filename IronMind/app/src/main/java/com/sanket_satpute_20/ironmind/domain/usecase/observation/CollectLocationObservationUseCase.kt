package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

/**
 * Collects a single coarse location snapshot and records it as an observation.
 *
 * Critical design constraints (from roadmap V4.4):
 * - Context must not become surveillance.
 * - Only a single coarse snapshot is captured per invocation — no continuous tracking.
 * - No address resolution or place name storage.
 * - Guarded by user consent and OS permission.
 */
class CollectLocationObservationUseCase(
    private val settingsRepository: LocationObservationSettingsRepository,
    private val observationRepository: ObservationRepository,
    private val locationObservationProvider: LocationObservationProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) : ObservationCollector {
    override val collectorName: String = "Location"

    override suspend operator fun invoke(userId: String): Result<Int, Exception> {
        return try {
            // Guard 1: user consent
            val settingsResult = settingsRepository.getSettings(userId)
            if (settingsResult is Result.Failure) return Result.Failure(settingsResult.error)
            val settings = (settingsResult as Result.Success).data

            if (!settings.isEnabled) {
                println("IronMindLifecycle [LocationObservation] [SKIPPED] reason=disabled userId=$userId")
                return Result.Success(0)
            }

            // Guard 2: OS permission
            if (!locationObservationProvider.isPermissionGranted()) {
                println("IronMindLifecycle [LocationObservation] [SKIPPED] reason=permission_not_granted userId=$userId")
                return Result.Success(0)
            }

            val snapshot = locationObservationProvider.getLastKnownCoarseLocation()
            if (snapshot == null) {
                println("IronMindLifecycle [LocationObservation] [SKIPPED] reason=location_unavailable userId=$userId")
                return Result.Success(0)
            }

            val now = clock.currentTimeMillis()
            val observation = Observation(
                id = idGenerator.generateId(),
                userId = userId,
                type = ObservationType.LOCATION_CONTEXT_CHANGED,
                source = ObservationSource.LOCATION,
                occurredAt = now,
                recordedAt = now,
                subjectId = null,
                value = "COARSE_LOCATION",
                // Store coarse lat/lng rounded to 2 decimal places (~1 km precision) to reduce precision
                context = "lat=${String.format("%.2f", snapshot.latitude)},lng=${String.format("%.2f", snapshot.longitude)}",
                confidence = null,
                provenance = ObservationProvenance(
                    source = ObservationSource.LOCATION,
                    sourceReference = "LocationManager.getLastKnownLocation",
                    capturedAt = now
                )
            )

            val insertResult = observationRepository.insertObservation(observation)
            return if (insertResult is Result.Success) {
                println("IronMindLifecycle [LocationObservation] [COLLECTED] userId=$userId")
                Result.Success(1)
            } else {
                Result.Failure((insertResult as Result.Failure).error)
            }
        } catch (e: Exception) {
            println("IronMindLifecycle [LocationObservation] [COLLECT_FAILED] userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }
}
