package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType

class StartProtectionSessionUseCase(
    private val protectionRepository: ProtectionRepository,
    private val protectionProvider: AppProtectionProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        ruleId: String? = null,
        commitmentId: String? = null,
        taskId: String? = null,
        scheduledEndAt: Long? = null,
        overrideAllowed: Boolean = true,
        targetPackages: List<String> = emptyList(),
        source: EntitySource = EntitySource.USER
    ): Result<ProtectionSession, Exception> {
        
        if (!protectionProvider.hasRequiredPermissions()) {
            return Result.Failure(Exception("Missing required protection permissions"))
        }

        val now = clock.currentTimeMillis()
        val session = ProtectionSession(
            id = idGenerator.generateId(),
            userId = userId,
            ruleId = ruleId,
            commitmentId = commitmentId,
            taskId = taskId,
            startedAt = now,
            scheduledEndAt = scheduledEndAt,
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = source,
            overrideAllowed = overrideAllowed,
            createdAt = now,
            updatedAt = now
        )

        val applyResult = protectionProvider.applyProtection(targetPackages)
        if (applyResult is Result.Failure) {
            return Result.Failure(Exception("Failed to apply protection: ${applyResult.error.message}"))
        }

        val result = protectionRepository.saveProtectionSession(session)
        return if (result is Result.Success) {
            val event = Event(
                id = idGenerator.generateId(),
                userId = userId,
                type = EventType.PROTECTION_STARTED,
                entityType = "PROTECTION_SESSION",
                entityId = session.id,
                occurredAt = now,
                recordedAt = now,
                source = source,
                metadata = null
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle [Protection] [SESSION_STARTED] sessionId=${session.id}")
            Result.Success(session)
        } else {
            // Rollback if saving session fails
            protectionProvider.removeProtection()
            Result.Failure((result as Result.Failure).error)
        }
    }
}
