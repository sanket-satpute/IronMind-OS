package com.sanket_satpute_20.ironmind.domain.usecase.autonomy

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository

class GetAutonomySettingsUseCase(
    private val repository: AutonomySettingsRepository
) {
    suspend operator fun invoke(userId: String): Result<AutonomySettings, Exception> {
        return repository.getSettings(userId)
    }
}
