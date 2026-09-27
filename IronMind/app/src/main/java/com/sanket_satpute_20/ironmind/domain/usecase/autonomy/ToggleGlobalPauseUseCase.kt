package com.sanket_satpute_20.ironmind.domain.usecase.autonomy

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository

class ToggleGlobalPauseUseCase(
    private val repository: AutonomySettingsRepository,
    private val coordinator: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator
) {
    suspend operator fun invoke(userId: String, isPaused: Boolean): Result<Unit, Exception> {
        val result = repository.setGlobalPause(userId, isPaused)
        if (result is Result.Success) {
            println("IronMindLifecycle [ObservationSchedulingLifecycle] [GLOBAL_PAUSE_CHANGED]")
            coordinator.handle(com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged)
        }
        return result
    }
}
