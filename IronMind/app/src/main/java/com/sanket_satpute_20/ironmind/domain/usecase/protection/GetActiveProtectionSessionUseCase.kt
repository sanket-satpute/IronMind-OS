package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository

class GetActiveProtectionSessionUseCase(
    private val protectionRepository: ProtectionRepository,
    private val clock: Clock? = null,
    private val stopProtectionSessionUseCase: StopProtectionSessionUseCase? = null
) {
    suspend operator fun invoke(userId: String): Result<ProtectionSession?, Exception> {
        return when (val result = protectionRepository.getActiveProtectionSessionsForUser(userId)) {
            is Result.Success -> {
                val session = result.data.firstOrNull()
                if (session != null && clock != null && session.scheduledEndAt != null) {
                    val now = clock.currentTimeMillis()
                    if (now >= session.scheduledEndAt) {
                        stopProtectionSessionUseCase?.invoke(
                            sessionId = session.id,
                            reason = StopProtectionSessionUseCase.StopReason.EXPIRED
                        )
                        return Result.Success(null)
                    }
                }
                Result.Success(session)
            }
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
