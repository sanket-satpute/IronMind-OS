package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository

class SetActivityObservationEnabledUseCase(
    private val repository: ActivityObservationSettingsRepository,
    private val getSettingsUseCase: GetActivityObservationSettingsUseCase
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        val currentSettings = getSettingsUseCase(userId)
        
        if (currentSettings is Result.Success && (currentSettings as Result.Success).data.isEnabled == isEnabled) {
            return Result.Success(Unit)
        }

        return repository.setEnabled(userId, isEnabled)
    }
}
