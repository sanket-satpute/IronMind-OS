package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository

class GetLocationObservationSettingsUseCase(
    private val repository: LocationObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String): Result<LocationObservationSettings, Exception> {
        return repository.getSettings(userId)
    }
}
