package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

/**
 * Sets confirmationState=USER_CONFIRMED on an existing memory.
 */
class ConfirmMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        memoryId: String
    ): Result<Memory, Exception> {
        val fetchResult = memoryRepository.getMemoryById(memoryId)
        if (fetchResult is Result.Failure) return Result.Failure(fetchResult.error)
        val existing = (fetchResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Memory not found: $memoryId"))

        val now = clock.currentTimeMillis()
        val updated = existing.copy(
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            lastObservedAt = now,
            updatedAt = now
        )
        val saveResult = memoryRepository.saveMemory(updated)
        return if (saveResult is Result.Success) {
            val event = Event(
                id = idGenerator.generateId(),
                userId = userId,
                type = EventType.MEMORY_UPDATED,
                entityType = "MEMORY",
                entityId = memoryId,
                occurredAt = now,
                recordedAt = now,
                source = EntitySource.USER
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle Memory [CONFIRM] memoryId=$memoryId")
            Result.Success(updated)
        } else {
            Result.Failure((saveResult as Result.Failure).error)
        }
    }
}
