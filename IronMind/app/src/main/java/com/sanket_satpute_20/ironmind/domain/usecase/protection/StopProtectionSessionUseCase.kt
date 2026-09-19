package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository

class StopProtectionSessionUseCase(
    private val protectionRepository: ProtectionRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(
        sessionId: String,
        reason: StopReason = StopReason.USER_INITIATED
    ): Result<Unit, Exception> {
        val getResult = protectionRepository.getProtectionSession(sessionId)
        if (getResult !is Result.Success) {
            return Result.Failure(Exception("Session not found"))
        }

        val session = getResult.data
        if (session.status != ProtectionSessionStatus.ACTIVE && session.status != ProtectionSessionStatus.SCHEDULED) {
            return Result.Failure(Exception("Session is not active or scheduled, current status: ${session.status}"))
        }

        val now = clock.currentTimeMillis()
        val finalStatus = when (reason) {
            StopReason.USER_INITIATED -> ProtectionSessionStatus.COMPLETED
            StopReason.CANCELLED -> ProtectionSessionStatus.CANCELLED
            StopReason.OVERRIDDEN -> ProtectionSessionStatus.OVERRIDDEN
            StopReason.EXPIRED -> ProtectionSessionStatus.EXPIRED
        }

        val updatedSession = session.copy(
            status = finalStatus,
            endedAt = now,
            updatedAt = now
        )

        val saveResult = protectionRepository.saveProtectionSession(updatedSession)
        return if (saveResult is Result.Success) {
            println("IronMindLifecycle [Protection] [SESSION_ENDED] sessionId=${session.id} status=${finalStatus.name}")
            Result.Success(Unit)
        } else {
            Result.Failure((saveResult as Result.Failure).error)
        }
    }

    enum class StopReason {
        USER_INITIATED,
        CANCELLED,
        OVERRIDDEN,
        EXPIRED
    }
}
