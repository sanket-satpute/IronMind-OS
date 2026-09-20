package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.NotificationObservationSettingsRepository

class SetNotificationObservationEnabledUseCase(
    private val notificationObservationSettingsRepository: NotificationObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        return notificationObservationSettingsRepository.setEnabled(userId, isEnabled)
    }
}
