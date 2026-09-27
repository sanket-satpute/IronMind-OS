package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.ActivityObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

/**
 * Aggregates raw ACTIVITY_CONTEXT_CHANGED observations over a deterministic time window.
 *
 * Rules:
 * - Time window is explicitly bounded: occurredAt >= startTimeMs AND occurredAt < endTimeMs.
 * - Counts distinct activity states from observation value.
 * - Extracts first and last occurrence timestamps in the window.
 * - Explicitly avoids calculating duration since raw observations are stateless snapshots.
 * - Strictly scopes aggregation to the provided userId.
 */
class AggregateActivityObservationsUseCase(
    private val observationRepository: ObservationRepository
) {
    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<ActivityObservationAggregation, Exception> {
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        return when (val result = observationRepository.getObservationsForTimeWindow(
            userId = userId,
            type = ObservationType.ACTIVITY_CONTEXT_CHANGED,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs
        )) {
            is Result.Success -> {
                val observations = result.data
                
                val stateCounts = mutableMapOf<String, Int>()
                var firstOccurrence: Long? = null
                var lastOccurrence: Long? = null

                for (obs in observations) {
                    val state = obs.value
                    if (state.isNotBlank()) {
                        stateCounts[state] = stateCounts.getOrDefault(state, 0) + 1
                    } else {
                        println("IronMindLifecycle [ObservationAggregation] [WARNING] Malformed activity state ignored/unknown")
                    }

                    if (firstOccurrence == null || obs.occurredAt < firstOccurrence) {
                        firstOccurrence = obs.occurredAt
                    }
                    if (lastOccurrence == null || obs.occurredAt > lastOccurrence) {
                        lastOccurrence = obs.occurredAt
                    }
                }

                println("IronMindLifecycle [ObservationAggregation] [COMPLETED] type=ACTIVITY_CONTEXT_CHANGED count=${observations.size}")

                Result.Success(
                    ActivityObservationAggregation(
                        userId = userId,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        observationCount = observations.size,
                        distinctActivityStateCount = stateCounts.size,
                        perStateObservationCounts = stateCounts,
                        firstOccurrenceTimestamp = firstOccurrence,
                        lastOccurrenceTimestamp = lastOccurrence
                    )
                )
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
