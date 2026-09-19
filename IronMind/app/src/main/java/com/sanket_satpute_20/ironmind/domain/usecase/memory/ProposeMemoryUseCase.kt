package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

/**
 * Proposes a new Memory with status=ACTIVE and confirmationState=UNCONFIRMED.
 * Memory is not truth until confirmed.
 */
class ProposeMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        type: String,
        content: String,
        source: EntitySource,
        confidence: Float,
        evidenceCount: Int = 1
    ): Result<Memory, Exception> {
        if (content.isBlank()) {
            return Result.Failure(IllegalArgumentException("Memory content cannot be blank"))
        }
        if (confidence < 0f || confidence > 1f) {
            return Result.Failure(IllegalArgumentException("Confidence must be between 0.0 and 1.0"))
        }

        val now = clock.currentTimeMillis()
        val memory = Memory(
            id = idGenerator.generateId(),
            userId = userId,
            type = type,
            content = content,
            source = source,
            confidence = confidence,
            evidenceCount = evidenceCount,
            firstObservedAt = now,
            lastObservedAt = now,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            status = MemoryStatus.ACTIVE,
            expiresAt = null,
            createdAt = now,
            updatedAt = now
        )

        val result = memoryRepository.saveMemory(memory)
        return if (result is Result.Success) {
            val event = Event(
                id = idGenerator.generateId(),
                userId = userId,
                type = EventType.MEMORY_CREATED,
                entityType = "MEMORY",
                entityId = memory.id,
                occurredAt = now,
                recordedAt = now,
                source = source
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle [Memory] [PROPOSED] memoryId=${memory.id} source=${source.name}")
            Result.Success(memory)
        } else {
            Result.Failure((result as Result.Failure).error)
        }
    }
}
