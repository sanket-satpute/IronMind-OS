package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository

class GetActiveProtectionSessionUseCase(
    private val protectionRepository: ProtectionRepository
) {
    suspend operator fun invoke(userId: String): Result<ProtectionSession?, Exception> {
        return when (val result = protectionRepository.getActiveProtectionSessionsForUser(userId)) {
            is Result.Success -> Result.Success(result.data.firstOrNull())
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
