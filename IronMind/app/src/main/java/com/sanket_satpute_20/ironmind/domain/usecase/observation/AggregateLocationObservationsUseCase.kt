package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.LocationObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

/**
 * Aggregates raw LOCATION_CONTEXT_CHANGED observations over a deterministic time window.
 *
 * NOTE: The query window [startTimeMs, endTimeMs) filters by observation capture time.
 * Coordinates are validated and stripped, producing strictly factual bounded metrics.
 */
class AggregateLocationObservationsUseCase(
    private val observationRepository: ObservationRepository
) {
    // Data class strictly for internal deduplication and adjacency checks
    private data class Coordinate(
        val lat: Double,
        val lng: Double
    )

    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<LocationObservationAggregation, Exception> {
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        return when (val result = observationRepository.getObservationsForTimeWindow(
            userId = userId,
            type = ObservationType.LOCATION_CONTEXT_CHANGED,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs
        )) {
            is Result.Success -> {
                val observations = result.data.sortedBy { it.occurredAt }
                val uniqueCoordinates = mutableSetOf<String>()
                
                var coordinateChangeCount = 0
                var previousCoordinate: Coordinate? = null
                
                var earliestStartMs: Long? = null
                var latestEndMs: Long? = null

                for (obs in observations) {
                    val contextStr = obs.context

                    if (earliestStartMs == null || obs.occurredAt < earliestStartMs) {
                        earliestStartMs = obs.occurredAt
                    }
                    if (latestEndMs == null || obs.occurredAt > latestEndMs) {
                        latestEndMs = obs.occurredAt
                    }

                    if (contextStr.isBlank()) {
                        println("IronMindLifecycle [ObservationAggregation] [WARNING] Malformed location observation ignored")
                        previousCoordinate = null
                        continue
                    }

                    val coordinate = parseCoordinate(contextStr)
                    if (coordinate == null) {
                        println("IronMindLifecycle [ObservationAggregation] [WARNING] Malformed location observation ignored")
                        previousCoordinate = null
                        continue
                    }

                    uniqueCoordinates.add(contextStr)

                    if (previousCoordinate != null) {
                        if (previousCoordinate.lat != coordinate.lat || previousCoordinate.lng != coordinate.lng) {
                            coordinateChangeCount++
                        }
                    }
                    previousCoordinate = coordinate
                }

                println("IronMindLifecycle [ObservationAggregation] [COMPLETED] type=LOCATION_CONTEXT_CHANGED count=${observations.size}")

                Result.Success(
                    LocationObservationAggregation(
                        userId = userId,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        observationCount = observations.size,
                        uniqueCoordinateCount = uniqueCoordinates.size,
                        coordinateChangeCount = coordinateChangeCount,
                        firstOccurrenceTimestamp = earliestStartMs,
                        lastOccurrenceTimestamp = latestEndMs
                    )
                )
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }

    private fun parseCoordinate(contextStr: String): Coordinate? {
        // Expected format: lat=XX.XX,lng=YY.YY
        val parts = contextStr.split(",")
        if (parts.size != 2) return null

        val latPart = parts.find { it.startsWith("lat=") } ?: return null
        val lngPart = parts.find { it.startsWith("lng=") } ?: return null

        val latStr = latPart.substringAfter("lat=")
        val lngStr = lngPart.substringAfter("lng=")

        val lat = latStr.toDoubleOrNull() ?: return null
        val lng = lngStr.toDoubleOrNull() ?: return null

        if (lat < -90.0 || lat > 90.0) return null
        if (lng < -180.0 || lng > 180.0) return null

        return Coordinate(lat, lng)
    }
}
