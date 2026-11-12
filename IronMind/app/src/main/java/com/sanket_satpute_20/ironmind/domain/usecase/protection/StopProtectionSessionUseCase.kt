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
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator? = null
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
        if (session.status == ProtectionSessionStatus.EXPIRED && reason == StopReason.EXPIRED) {
            // Idempotent: already expired. Ensure protection is removed and return success.
            appProtectionProvider.removeProtection()
            return Result.Success(Unit)
        }
        if (session.status == ProtectionSessionStatus.OVERRIDDEN) {
            return Result.Failure(Exception("Session is already OVERRIDDEN"))
        }
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

        val removeResult = appProtectionProvider.removeProtection()
        if (removeResult is Result.Failure) {
            return Result.Failure(Exception("Failed to remove protection: ${removeResult.error.message}"))
        }

        val effectiveEndedAt = if (reason == StopReason.EXPIRED && session.scheduledEndAt != null) {
            session.scheduledEndAt
        } else {
            now
        }

        val updatedSession = session.copy(
            status = finalStatus,
            endedAt = effectiveEndedAt,
            updatedAt = now
        )

        val saveResult = protectionRepository.saveProtectionSession(updatedSession)
        return if (saveResult is Result.Success) {
            val eventType = when (reason) {
                StopReason.OVERRIDDEN -> EventType.PROTECTION_OVERRIDDEN
                StopReason.USER_INITIATED,
                StopReason.CANCELLED,
                StopReason.EXPIRED -> EventType.PROTECTION_ENDED
            }
            val event = Event(
                id = idGenerator?.generateId() ?: java.util.UUID.randomUUID().toString(),
                userId = session.userId,
                type = eventType,
                entityType = "PROTECTION_SESSION",
                entityId = session.id,
                occurredAt = now,
                recordedAt = now,
                source = EntitySource.USER,
                previousState = session.status.name,
                newState = finalStatus.name,
                metadata = "reason=${reason.name};endedAt=$effectiveEndedAt"
            )
            val eventResult = try {
                eventRepository.saveEvent(event)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Failure(e)
            }
            if (eventResult is Result.Failure) {
                protectionRepository.saveProtectionSession(session)
                return Result.Failure(eventResult.error)
            }
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
