package com.sanket_satpute_20.ironmind.domain.usecase.autonomy

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository

class ToggleGlobalPauseUseCase(
    private val repository: AutonomySettingsRepository
) {
    suspend operator fun invoke(userId: String, isPaused: Boolean): Result<Unit, Exception> {
        return repository.setGlobalPause(userId, isPaused)
    }
}
