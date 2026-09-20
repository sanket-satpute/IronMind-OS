package com.sanket_satpute_20.ironmind.domain.usecase.autonomy

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository

class UpdateAutonomyLevelUseCase(
    private val repository: AutonomySettingsRepository,
    private val logger: IronLogger
) {
    suspend operator fun invoke(
        userId: String,
        capability: AutonomyCapability,
        level: AutonomyLevel
    ): Result<Unit, Exception> {
        val result = repository.updateLevel(userId, capability, level)
        if (result is Result.Success) {
            logger.logLifecycle(
                component = "Autonomy",
                event = "CHANGED",
                parameters = mapOf("capability" to capability.name, "level" to level.name)
            )
        }
        return result
    }
}
