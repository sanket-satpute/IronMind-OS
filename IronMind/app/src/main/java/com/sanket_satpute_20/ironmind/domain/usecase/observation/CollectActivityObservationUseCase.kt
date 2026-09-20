package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance

class CollectActivityObservationUseCase(
    private val settingsRepository: ActivityObservationSettingsRepository,
    private val observationRepository: ObservationRepository,
    private val activityObservationProvider: ActivityObservationProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(userId: String): Result<Int, Exception> {
        return try {
            val settingsResult = settingsRepository.getSettings(userId)
            if (settingsResult is Result.Success && !(settingsResult as Result.Success).data.isEnabled) {
                println("IronMindLifecycle [ActivityObservation] [SKIPPED] reason=disabled")
                return Result.Success(0)
            }

            if (!activityObservationProvider.isPermissionGranted()) {
                println("IronMindLifecycle [ActivityObservation] [SKIPPED] reason=permission_not_granted")
                return Result.Success(0)
            }

            val activitySnapshot = activityObservationProvider.getCurrentActivity()
            if (activitySnapshot == null) {
                println("IronMindLifecycle [ActivityObservation] [SKIPPED] reason=activity_unavailable")
                return Result.Success(0)
            }

            val now = clock.currentTimeMillis()
            val observation = Observation(
                id = idGenerator.generateId(),
                userId = userId,
                type = ObservationType.ACTIVITY_CONTEXT_CHANGED,
                source = ObservationSource.ACTIVITY,
                occurredAt = now,
                recordedAt = now,
                subjectId = null,
                value = activitySnapshot.state.name,
                context = "Confidence: ${activitySnapshot.confidence}%",
                confidence = (activitySnapshot.confidence / 100.0).toFloat(),
                provenance = ObservationProvenance(
                    source = ObservationSource.ACTIVITY,
                    sourceReference = "AndroidActivityRecognition",
                    capturedAt = now
                )
            )

            val insertResult = observationRepository.insertObservation(observation)
            if (insertResult is Result.Success) {
                println("IronMindLifecycle [ActivityObservation] [COLLECTED] userId=$userId activity=${activitySnapshot.state}")
                return Result.Success(1)
            } else {
                return Result.Failure((insertResult as Result.Failure).error)
            }
        } catch (e: Exception) {
            println("IronMindLifecycle [ActivityObservation] [COLLECT_FAILED]")
            Result.Failure(e)
        }
    }
}
