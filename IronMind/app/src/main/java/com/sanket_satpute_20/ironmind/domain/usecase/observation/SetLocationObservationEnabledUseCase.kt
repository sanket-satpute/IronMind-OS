package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository

class SetLocationObservationEnabledUseCase(
    private val repository: LocationObservationSettingsRepository,
    private val getSettingsUseCase: GetLocationObservationSettingsUseCase
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        return when (val currentResult = getSettingsUseCase(userId)) {
            is Result.Success -> {
                val newSettings = LocationObservationSettings(
                    userId = userId,
                    isEnabled = isEnabled,
                    updatedAt = currentResult.data.updatedAt // Repository will update timestamp
                )
                repository.updateSettings(newSettings)
            }
            is Result.Failure -> Result.Failure(currentResult.error)
        }
    }
}
