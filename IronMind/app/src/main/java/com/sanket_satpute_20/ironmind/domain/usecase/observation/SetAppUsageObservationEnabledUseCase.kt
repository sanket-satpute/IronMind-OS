package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository

class SetAppUsageObservationEnabledUseCase(
    private val repository: AppUsageObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        println("IronMindLifecycle [AppUsageObservation] [SETTING_CHANGED] userId=$userId isEnabled=$isEnabled")
        return repository.setEnabled(userId, isEnabled)
    }
}
