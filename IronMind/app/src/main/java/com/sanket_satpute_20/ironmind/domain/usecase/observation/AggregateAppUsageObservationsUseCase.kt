package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.AppUsageObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

/**
 * Aggregates raw APP_USAGE_SESSION observations over a deterministic time window.
 *
 * Rules:
 * - Time window is explicitly bounded: occurredAt >= startTimeMs AND occurredAt < endTimeMs.
 * - Extracts `duration_ms` from observation context.
 * - If `duration_ms` is malformed, missing, or negative, its duration contribution is 0,
 *   but it still counts towards observationCount and uniquePackageCount to preserve accuracy
 *   of observation occurrence.
 * - Strictly scopes aggregation to the provided userId.
 */
class AggregateAppUsageObservationsUseCase(
    private val observationRepository: ObservationRepository
) {
    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<AppUsageObservationAggregation, Exception> {
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        return when (val result = observationRepository.getObservationsForTimeWindow(
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs
        )) {
            is Result.Success -> {
                val observations = result.data
                
                var totalDurationMs = 0L
                val uniquePackages = mutableSetOf<String>()

                for (obs in observations) {
                    uniquePackages.add(obs.value)

                    val durationStr = obs.context.removePrefix("duration_ms=")
                    val duration = durationStr.toLongOrNull()
                    
                    if (duration != null && duration > 0) {
                        totalDurationMs += duration
                    } else {
                        println("IronMindLifecycle [ObservationAggregation] [WARNING] Malformed or negative duration ignored")
                    }
                }

                println("IronMindLifecycle [ObservationAggregation] [COMPLETED] type=APP_USAGE_SESSION count=${observations.size}")

                Result.Success(
                    AppUsageObservationAggregation(
                        userId = userId,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        observationCount = observations.size,
                        totalDurationMs = totalDurationMs,
                        uniquePackageCount = uniquePackages.size
                    )
                )
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
