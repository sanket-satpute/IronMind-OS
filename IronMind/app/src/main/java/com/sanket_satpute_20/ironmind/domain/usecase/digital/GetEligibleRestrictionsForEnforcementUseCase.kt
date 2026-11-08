package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.provider.InstalledAppDiscoveryProvider

/**
 * Loads and filters app restrictions that are strictly eligible for active enforcement.
 *
 * Guarantees:
 * 1. Only configurations belonging to the active user are loaded.
 * 2. Only configurations with [AppRestrictionConfig.isEnabled] = true are considered.
 * 3. IronMind itself ("com.sanket_satpute_20.ironmind") is NEVER eligible for restriction.
 * 4. Uninstalled or unreachable packages on the device are safely filtered out.
 */
class GetEligibleRestrictionsForEnforcementUseCase(
    private val getAppRestrictionConfigsUseCase: GetAppRestrictionConfigsUseCase,
    private val installedAppDiscoveryProvider: InstalledAppDiscoveryProvider
) {
    suspend operator fun invoke(userId: String): Result<List<AppRestrictionConfig>, Exception> {
        val trimmedUserId = userId.trim()
        if (trimmedUserId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }

        return when (val configsResult = getAppRestrictionConfigsUseCase(trimmedUserId)) {
            is Result.Success -> {
                val eligible = configsResult.data.filter { config ->
                    // 1. Must be enabled
                    config.isEnabled &&
                    // 2. IronMind itself is NEVER restricted
                    config.packageName != IRONMIND_PACKAGE &&
                    // 3. Must be currently installed / reachable on device
                    installedAppDiscoveryProvider.isAppInstalled(config.packageName)
                }
                Result.Success(eligible)
            }
            is Result.Failure -> Result.Failure(configsResult.error)
        }
    }

    companion object {
        const val IRONMIND_PACKAGE = "com.sanket_satpute_20.ironmind"
    }
}
