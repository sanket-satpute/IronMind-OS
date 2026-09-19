package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

/**
 * Reduces confidence on an existing memory. If confidence drops below threshold,
 * status transitions to DECAYING.
 */
class WeakenMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    companion object {
        /** Confidence threshold below which a memory becomes DECAYING. */
        const val DECAYING_THRESHOLD = 0.3f
    }

    suspend operator fun invoke(
        userId: String,
        memoryId: String,
        newConfidence: Float
    ): Result<Memory, Exception> {
        if (newConfidence < 0f || newConfidence > 1f) {
            return Result.Failure(IllegalArgumentException("Confidence must be between 0.0 and 1.0"))
        }

        val fetchResult = memoryRepository.getMemoryById(memoryId)
        if (fetchResult is Result.Failure) return Result.Failure(fetchResult.error)
        val existing = (fetchResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Memory not found: $memoryId"))

        val now = clock.currentTimeMillis()
        val newStatus = when {
            newConfidence <= DECAYING_THRESHOLD -> MemoryStatus.DECAYING
            else -> existing.status
        }
        val updated = existing.copy(
            confidence = newConfidence,
            status = newStatus,
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
                source = EntitySource.SYSTEM
            )
            eventRepository.saveEvent(event)
            println("IronMindLifecycle [Memory] [WEAKENED] memoryId=$memoryId confidence=$newConfidence status=${newStatus.name}")
            Result.Success(updated)
        } else {
            Result.Failure((saveResult as Result.Failure).error)
        }
    }
}
