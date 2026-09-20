package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.NotificationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.NotificationObservationSettingsRepository

class GetNotificationObservationSettingsUseCase(
    private val notificationObservationSettingsRepository: NotificationObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String): Result<NotificationObservationSettings, Exception> {
        return notificationObservationSettingsRepository.getSettings(userId)
    }
}
