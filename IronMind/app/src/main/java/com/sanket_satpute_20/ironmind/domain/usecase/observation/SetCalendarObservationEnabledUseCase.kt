package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository

class SetCalendarObservationEnabledUseCase(
    private val repository: CalendarObservationSettingsRepository,
    private val getSettingsUseCase: GetCalendarObservationSettingsUseCase
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        return when (val currentSettingsResult = getSettingsUseCase(userId)) {
            is Result.Success -> {
                val newSettings = CalendarObservationSettings(
                    userId = userId,
                    isEnabled = isEnabled,
                    updatedAt = currentSettingsResult.data.updatedAt // Repository will update this timestamp
                )
                repository.updateSettings(newSettings)
            }
            is Result.Failure -> Result.Failure(currentSettingsResult.error)
        }
    }
}
