package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.NotificationObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.common.Clock

class HandleIncomingNotificationUseCase(
    private val notificationObservationSettingsRepository: NotificationObservationSettingsRepository,
    private val observationRepository: ObservationRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        packageName: String,
        isClearable: Boolean,
        timestamp: Long
    ) {
        // Filter out ongoing / non-clearable notifications (like media controls, system services)
        if (!isClearable) return
        
        // Don't track self notifications as interruptions
        if (packageName == "com.sanket_satpute_20.ironmind") return

        val settingsResult = notificationObservationSettingsRepository.getSettings(userId)
        if (settingsResult is Result.Success && settingsResult.data.isEnabled) {
            val observation = Observation(
                id = idGenerator.generateId(),
                userId = userId,
                type = ObservationType.NOTIFICATION_RECEIVED,
                source = ObservationSource.NOTIFICATION,
                occurredAt = timestamp,
                recordedAt = clock.currentTimeMillis(),
                subjectId = packageName,
                value = packageName,
                context = "{\"packageName\":\"$packageName\"}",
                confidence = 1.0f,
                provenance = com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance(
                    source = ObservationSource.NOTIFICATION,
                    sourceReference = packageName,
                    capturedAt = clock.currentTimeMillis()
                )
            )
            observationRepository.insertObservation(observation)
        }
    }
}
