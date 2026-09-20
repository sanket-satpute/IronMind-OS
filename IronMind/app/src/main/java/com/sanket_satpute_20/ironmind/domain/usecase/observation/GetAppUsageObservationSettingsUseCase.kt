package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository

class GetAppUsageObservationSettingsUseCase(
    private val repository: AppUsageObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String): Result<AppUsageObservationSettings, Exception> =
        repository.getSettings(userId)
}
