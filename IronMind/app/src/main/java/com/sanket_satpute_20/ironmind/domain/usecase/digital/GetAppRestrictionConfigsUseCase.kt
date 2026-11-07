package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfigCodec
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import java.util.Locale

class GetAppRestrictionConfigsUseCase(
    private val protectionRepository: ProtectionRepository
) {
    suspend operator fun invoke(userId: String): Result<List<AppRestrictionConfig>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }

        return when (val result = protectionRepository.getProtectionRulesForUser(userId)) {
            is Result.Success -> {
                val configs = result.data
                    .mapNotNull { AppRestrictionConfigCodec.fromProtectionRule(it) }
                    .sortedWith(
                        compareBy<AppRestrictionConfig> { it.appDisplayName.lowercase(Locale.ROOT) }
                            .thenBy { it.packageName }
                    )
                Result.Success(configs)
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
