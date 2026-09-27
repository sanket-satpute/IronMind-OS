package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository

class SetCalendarObservationEnabledUseCase(
    private val repository: CalendarObservationSettingsRepository,
    private val getSettingsUseCase: GetCalendarObservationSettingsUseCase,
    private val coordinator: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator
) {
    suspend operator fun invoke(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        return when (val currentSettingsResult = getSettingsUseCase(userId)) {
            is Result.Success -> {
                if (currentSettingsResult.data.isEnabled == isEnabled) {
                    return Result.Success(Unit)
                }

                val newSettings = CalendarObservationSettings(
                    userId = userId,
                    isEnabled = isEnabled,
                    updatedAt = currentSettingsResult.data.updatedAt // Repository will update this timestamp
                )
                val result = repository.updateSettings(newSettings)
                if (result is Result.Success) {
                    println("IronMindLifecycle [ObservationSchedulingLifecycle] [CONSENT_CHANGED]")
                    coordinator.handle(com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingLifecycleEvent.ObservationConsentChanged)
                }
                result
            }
            is Result.Failure -> Result.Failure(currentSettingsResult.error)
        }
    }
}
