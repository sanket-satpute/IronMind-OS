package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository

class SetLocationObservationEnabledUseCase(
    private val repository: LocationObservationSettingsRepository,
    private val getSettingsUseCase: GetLocationObservationSettingsUseCase,
    private val coordinator: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        return when (val currentResult = getSettingsUseCase(userId)) {
            is Result.Success -> {
                if (currentResult.data.isEnabled == isEnabled) {
                    return Result.Success(Unit)
                }

                val newSettings = LocationObservationSettings(
                    userId = userId,
                    isEnabled = isEnabled,
                    updatedAt = currentResult.data.updatedAt // Repository will update timestamp
                )
                val result = repository.updateSettings(newSettings)
                if (result is Result.Success) {
                    println("IronMindLifecycle [ObservationSchedulingLifecycle] [CONSENT_CHANGED]")
                    coordinator.handle(com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
                }
                result
            }
            is Result.Failure -> Result.Failure(currentResult.error)
        }
    }
}
