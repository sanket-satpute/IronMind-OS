package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.CalendarObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import org.json.JSONArray
import org.json.JSONException

/**
 * Aggregates raw CALENDAR_CONTEXT_CHANGED observations over a deterministic time window.
 *
 * NOTE: The query window [startTimeMs, endTimeMs) filters by observation capture time,
 * not by scheduled event time.
 */
class AggregateCalendarObservationsUseCase(
    private val observationRepository: ObservationRepository
) {
    // Data class strictly for internal deduplication
    private data class DeduplicationKey(
        val title: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long
    )

    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<CalendarObservationAggregation, Exception> {
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        return when (val result = observationRepository.getObservationsForTimeWindow(
            userId = userId,
            type = ObservationType.CALENDAR_CONTEXT_CHANGED,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs
        )) {
            is Result.Success -> {
                val observations = result.data
                val uniqueEvents = mutableSetOf<DeduplicationKey>()

                for (obs in observations) {
                    val contextStr = obs.context
                    if (contextStr.isBlank()) {
                        println("IronMindLifecycle [ObservationAggregation] [WARNING] Calendar observation context is empty")
                        continue
                    }

                    try {
                        val jsonArray = JSONArray(contextStr)
                        for (i in 0 until jsonArray.length()) {
                            val eventObj = jsonArray.optJSONObject(i) ?: continue

                            val title = eventObj.optString("title", "")
                            if (title.isBlank()) continue // Invalid if title is missing or blank

                            if (!eventObj.has("startTimeMillis") || !eventObj.has("endTimeMillis")) {
                                continue // Invalid if missing timestamps
                            }

                            val startMs = eventObj.optLong("startTimeMillis", -1L)
                            val endMs = eventObj.optLong("endTimeMillis", -1L)

                            if (startMs < 0 || endMs < 0) continue // Invalid if parsing failed
                            if (endMs < startMs) continue // Invalid if end is before start (0 duration is fine)

                            uniqueEvents.add(DeduplicationKey(title, startMs, endMs))
                        }
                    } catch (e: JSONException) {
                        println("IronMindLifecycle [ObservationAggregation] [WARNING] Malformed calendar observation JSON payload")
                    }
                }

                var sumOfDurationMs = 0L
                var earliestStartMs: Long? = null
                var latestEndMs: Long? = null

                for (event in uniqueEvents) {
                    sumOfDurationMs += (event.endTimeMillis - event.startTimeMillis)
                    if (earliestStartMs == null || event.startTimeMillis < earliestStartMs) {
                        earliestStartMs = event.startTimeMillis
                    }
                    if (latestEndMs == null || event.endTimeMillis > latestEndMs) {
                        latestEndMs = event.endTimeMillis
                    }
                }

                println("IronMindLifecycle [ObservationAggregation] [COMPLETED] type=CALENDAR_CONTEXT_CHANGED count=${observations.size}")

                Result.Success(
                    CalendarObservationAggregation(
                        userId = userId,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        observationCount = observations.size,
                        uniqueEventCount = uniqueEvents.size,
                        sumOfScheduledDurationMs = sumOfDurationMs,
                        earliestScheduledEventStartMs = earliestStartMs,
                        latestScheduledEventEndMs = latestEndMs
                    )
                )
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
