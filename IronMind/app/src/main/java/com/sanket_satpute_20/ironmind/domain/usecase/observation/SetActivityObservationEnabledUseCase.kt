package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository

class SetActivityObservationEnabledUseCase(
    private val repository: ActivityObservationSettingsRepository,
    private val getSettingsUseCase: GetActivityObservationSettingsUseCase,
    private val coordinator: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        val currentSettings = getSettingsUseCase(userId)
        
        if (currentSettings is Result.Success && currentSettings.data.isEnabled == isEnabled) {
            return Result.Success(Unit)
        }

        val result = repository.setEnabled(userId, isEnabled)
        if (result is Result.Success) {
            println("IronMindLifecycle [ObservationSchedulingLifecycle] [CONSENT_CHANGED]")
            coordinator.handle(com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
        }
        return result
    }
}
