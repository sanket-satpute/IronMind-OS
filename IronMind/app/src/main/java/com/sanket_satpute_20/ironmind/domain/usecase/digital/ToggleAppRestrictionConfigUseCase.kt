package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository

class ToggleAppRestrictionConfigUseCase(
    private val protectionRepository: ProtectionRepository,
    private val clock: Clock = object : Clock {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }
) {
    suspend operator fun invoke(
        userId: String,
        configId: String,
        isEnabled: Boolean
    ): Result<Unit, Exception> {
        val trimmedUserId = userId.trim()
        val trimmedConfigId = configId.trim()

        if (trimmedUserId.isBlank() || trimmedConfigId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and configId must not be blank"))
        }

        return when (val ruleResult = protectionRepository.getProtectionRule(trimmedConfigId)) {
            is Result.Success -> {
                val rule = ruleResult.data
                // User isolation check
                if (rule.userId != trimmedUserId) {
                    return Result.Failure(SecurityException("Unauthorized access to restriction rule"))
                }
                val updatedRule = rule.copy(
                    enabled = isEnabled,
                    updatedAt = clock.currentTimeMillis()
                )
                protectionRepository.saveProtectionRule(updatedRule)
            }
            is Result.Failure -> Result.Failure(ruleResult.error)
        }
    }
}
