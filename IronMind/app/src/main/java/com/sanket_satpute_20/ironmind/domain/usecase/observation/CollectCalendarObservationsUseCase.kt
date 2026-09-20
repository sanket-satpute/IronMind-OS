package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import org.json.JSONArray
import org.json.JSONObject

class CollectCalendarObservationsUseCase(
    private val settingsRepository: CalendarObservationSettingsRepository,
    private val observationRepository: ObservationRepository,
    private val calendarObservationProvider: CalendarObservationProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    // Lookahead window: check the next 24 hours of events
    private val lookaheadWindowMillis = 24 * 60 * 60 * 1000L // 24 hours

    suspend operator fun invoke(userId: String): Result<Int, Exception> {
        return try {
            // Guard 1: user consent
            val settingsResult = settingsRepository.getSettings(userId)
            if (settingsResult is Result.Failure) return Result.Failure(settingsResult.error)
            val settings = (settingsResult as Result.Success).data

            if (!settings.isEnabled) {
                println("IronMindLifecycle [CalendarObservation] [SKIPPED] reason=disabled userId=$userId")
                return Result.Success(0)
            }

            // Guard 2: OS permission
            if (!calendarObservationProvider.isPermissionGranted()) {
                println("IronMindLifecycle [CalendarObservation] [SKIPPED] reason=permission_not_granted userId=$userId")
                return Result.Success(0)
            }

            val now = clock.currentTimeMillis()
            val toTime = now + lookaheadWindowMillis
            val events = calendarObservationProvider.getUpcomingEvents(now, toTime)

            // Convert to a structured JSON string
            val jsonArray = JSONArray()
            events.forEach { event ->
                val jsonObject = JSONObject().apply {
                    put("title", event.title)
                    put("startTimeMillis", event.startTimeMillis)
                    put("endTimeMillis", event.endTimeMillis)
                }
                jsonArray.put(jsonObject)
            }

            // Record a single observation encapsulating the upcoming schedule
            val observation = Observation(
                id = idGenerator.generateId(),
                userId = userId,
                type = ObservationType.CALENDAR_CONTEXT_CHANGED,
                source = ObservationSource.CALENDAR,
                occurredAt = now,
                recordedAt = now,
                subjectId = null,
                value = "UPCOMING_EVENTS",
                context = jsonArray.toString(),
                confidence = null,
                provenance = ObservationProvenance(
                    source = ObservationSource.CALENDAR,
                    sourceReference = "CalendarContract",
                    capturedAt = now
                )
            )

            val insertResult = observationRepository.insertObservation(observation)
            
            if (insertResult is Result.Success) {
                println("IronMindLifecycle [CalendarObservation] [COLLECTED] userId=$userId count=1 events=${events.size}")
                Result.Success(1)
            } else {
                Result.Failure((insertResult as Result.Failure).error)
            }
        } catch (e: Exception) {
            println("IronMindLifecycle [CalendarObservation] [COLLECT_FAILED] userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }
}
