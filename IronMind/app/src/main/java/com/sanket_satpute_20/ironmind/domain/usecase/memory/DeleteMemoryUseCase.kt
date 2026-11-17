package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

class DeleteMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        memoryId: String
    ): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        if (memoryId.isBlank()) {
            return Result.Failure(IllegalArgumentException("memoryId must not be blank"))
        }

        val fetchResult = memoryRepository.getMemoryById(memoryId)
        if (fetchResult is Result.Failure) return Result.Failure(fetchResult.error)
        val existing = (fetchResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Memory not found: $memoryId"))

        if (existing.userId != userId) {
            return Result.Failure(IllegalStateException("Memory $memoryId does not belong to user $userId"))
        }

        val now = clock.currentTimeMillis()
        val event = Event(
            id = idGenerator.generateId(),
            userId = userId,
            type = EventType.MEMORY_DELETED,
            entityType = "MEMORY",
            entityId = memoryId,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            previousState = existing.content
        )

        val deleteResult = memoryRepository.deleteMemoryWithEvent(userId, memoryId, event)
        if (deleteResult is Result.Failure) return deleteResult

        println("IronMindLifecycle Memory [DELETE] memoryId=$memoryId userId=$userId")
        return Result.Success(Unit)
    }
}
