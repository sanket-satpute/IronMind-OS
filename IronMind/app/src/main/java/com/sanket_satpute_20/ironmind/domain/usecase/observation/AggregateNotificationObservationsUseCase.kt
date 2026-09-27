package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.NotificationObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

/**
 * Aggregates raw NOTIFICATION_RECEIVED observations over a deterministic time window.
 *
 * NOTE: The query window [startTimeMs, endTimeMs) filters by observation capture time.
 * Package identities and context are validated and stripped, producing strictly factual bounded metrics.
 */
class AggregateNotificationObservationsUseCase(
    private val observationRepository: ObservationRepository
) {
    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<NotificationObservationAggregation, Exception> {
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        return when (val result = observationRepository.getObservationsForTimeWindow(
            userId = userId,
            type = ObservationType.NOTIFICATION_RECEIVED,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs
        )) {
            is Result.Success -> {
                val observations = result.data.sortedBy { it.occurredAt }
                val uniquePackages = mutableSetOf<String>()
                
                var earliestStartMs: Long? = null
                var latestEndMs: Long? = null

                for (obs in observations) {
                    val packageName = obs.value

                    if (earliestStartMs == null || obs.occurredAt < earliestStartMs) {
                        earliestStartMs = obs.occurredAt
                    }
                    if (latestEndMs == null || obs.occurredAt > latestEndMs) {
                        latestEndMs = obs.occurredAt
                    }

                    if (packageName.isBlank()) {
                        println("IronMindLifecycle [NotificationObservationAggregation] [WARNING] Malformed notification package identity ignored")
                        continue
                    }

                    uniquePackages.add(packageName)
                }

                println("IronMindLifecycle [ObservationAggregation] [COMPLETED] type=NOTIFICATION_RECEIVED count=${observations.size}")

                Result.Success(
                    NotificationObservationAggregation(
                        userId = userId,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        observationCount = observations.size,
                        uniquePackageCount = uniquePackages.size,
                        firstOccurrenceTimestamp = earliestStartMs,
                        lastOccurrenceTimestamp = latestEndMs
                    )
                )
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
