package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository

class GetActivityObservationSettingsUseCase(
    private val repository: ActivityObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String): Result<ActivityObservationSettings, Exception> {
        return repository.getSettings(userId)
    }
}
