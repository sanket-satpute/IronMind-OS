package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator

class StopProtectionSessionUseCase(
    private val protectionRepository: ProtectionRepository,
    private val appProtectionProvider: AppProtectionProvider,
    private val clock: Clock,
    private val eventRepository: EventRepository
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
            appProtectionProvider.removeProtection()
            val event = Event(
                id = java.util.UUID.randomUUID().toString(), // Or inject idGenerator
                userId = session.userId,
                type = EventType.PROTECTION_ENDED,
                entityType = "PROTECTION_SESSION",
                entityId = session.id,
                occurredAt = now,
                recordedAt = now,
                source = EntitySource.USER
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle [Protection] [SESSION_STOPPED] sessionId=${session.id} reason=$reason")
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
