package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfigCodec
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository

class SaveAppRestrictionConfigUseCase(
    private val protectionRepository: ProtectionRepository,
    private val clock: Clock = object : Clock {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }
) {
    suspend operator fun invoke(
        userId: String,
        packageName: String,
        appDisplayName: String,
        isEnabled: Boolean = true,
        scheduleReferenceId: String? = null
    ): Result<AppRestrictionConfig, Exception> {
        val trimmedUserId = userId.trim()
        val trimmedPackage = packageName.trim()
        val trimmedName = appDisplayName.trim().ifBlank { trimmedPackage }

        if (trimmedUserId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }
        if (trimmedPackage.isBlank()) {
            return Result.Failure(IllegalArgumentException("packageName cannot be blank"))
        }

        // Safety invariant: Never allow restricting IronMind itself
        if (trimmedPackage == "com.sanket_satpute_20.ironmind") {
            return Result.Failure(IllegalArgumentException("IronMind itself cannot be restricted"))
        }

        val now = clock.currentTimeMillis()
        val stableId = "restr_${trimmedUserId}_${trimmedPackage.replace('.', '_')}"

        val config = AppRestrictionConfig(
            id = stableId,
            userId = trimmedUserId,
            packageName = trimmedPackage,
            appDisplayName = trimmedName,
            isEnabled = isEnabled,
            createdAt = now,
            updatedAt = now,
            scheduleReferenceId = scheduleReferenceId
        )

        val rule = AppRestrictionConfigCodec.toProtectionRule(config)
        return when (val result = protectionRepository.saveProtectionRule(rule)) {
            is Result.Success -> Result.Success(config)
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
