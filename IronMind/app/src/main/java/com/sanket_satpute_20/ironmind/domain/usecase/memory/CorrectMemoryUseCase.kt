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
 * Corrects the content of an existing memory based on explicit user feedback.
 * Original content is replaced, and because it comes from the user, it is treated as
 * highly confident and USER_CONFIRMED.
 */
class CorrectMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        memoryId: String,
        newContent: String,
        newConfidence: Float? = null
    ): Result<Memory, Exception> {
        if (newContent.isBlank()) {
            return Result.Failure(IllegalArgumentException("Corrected content cannot be blank"))
        }
        if (newConfidence != null && (newConfidence < 0f || newConfidence > 1f)) {
            return Result.Failure(IllegalArgumentException("Confidence must be between 0.0 and 1.0"))
        }

        val fetchResult = memoryRepository.getMemoryById(memoryId)
        if (fetchResult is Result.Failure) return Result.Failure(fetchResult.error)
        val existing = (fetchResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Memory not found: $memoryId"))

        val now = clock.currentTimeMillis()
        val updated = existing.copy(
            content = newContent,
            confidence = newConfidence ?: 1.0f, // Explicit user correction implies high confidence
            source = EntitySource.USER,
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
                source = EntitySource.USER,
                previousState = existing.content,
                newState = updated.content
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle Memory [CORRECT] memoryId=$memoryId")
            Result.Success(updated)
        } else {
            Result.Failure((saveResult as Result.Failure).error)
        }
    }
}
