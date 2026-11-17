package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

class RejectMemoryUseCase(
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

        // Idempotency: if already rejected, succeed without duplicate event
        if (existing.confirmationState == MemoryConfirmationState.USER_REJECTED && existing.status == MemoryStatus.INACTIVE) {
            return Result.Success(Unit)
        }

        val now = clock.currentTimeMillis()
        val event = Event(
            id = idGenerator.generateId(),
            userId = userId,
            type = EventType.MEMORY_REJECTED,
            entityType = "MEMORY",
            entityId = memoryId,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            previousState = existing.confirmationState.name,
            newState = MemoryConfirmationState.USER_REJECTED.name
        )

        val updateResult = memoryRepository.updateMemoryStateWithEvent(
            id = memoryId,
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_REJECTED,
            status = MemoryStatus.INACTIVE,
            updatedAt = now,
            event = event
        )
        if (updateResult is Result.Failure) return updateResult

        println("IronMindLifecycle Memory [REJECT] memoryId=$memoryId userId=$userId")
        return Result.Success(Unit)
    }
}
