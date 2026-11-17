package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository

class ConfirmPatternUseCase(
    private val patternRepository: PatternRepository,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        patternId: String
    ): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        if (patternId.isBlank()) {
            return Result.Failure(IllegalArgumentException("patternId must not be blank"))
        }

        val patternResult = patternRepository.getPattern(patternId)
        if (patternResult is Result.Failure) return Result.Failure(patternResult.error)
        val existing = (patternResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Pattern not found: $patternId"))

        if (existing.userId != userId) {
            return Result.Failure(IllegalStateException("Pattern $patternId does not belong to user $userId"))
        }

        // Idempotency check: if already confirmed, succeed without duplicate event
        if (existing.confirmationState == MemoryConfirmationState.USER_CONFIRMED && existing.status == PatternStatus.ACTIVE) {
            return Result.Success(Unit)
        }

        // Only eligible UNCONFIRMED patterns can be confirmed
        if (existing.confirmationState != MemoryConfirmationState.UNCONFIRMED) {
            return Result.Failure(IllegalStateException("Cannot confirm pattern in confirmationState ${existing.confirmationState}"))
        }
        if (existing.status != PatternStatus.ACTIVE) {
            return Result.Failure(IllegalStateException("Cannot confirm pattern with status ${existing.status}"))
        }

        val now = clock.currentTimeMillis()
        val event = Event(
            id = idGenerator.generateId(),
            userId = userId,
            type = EventType.PATTERN_CONFIRMED,
            entityType = "PATTERN",
            entityId = patternId,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            previousState = existing.confirmationState.name,
            newState = MemoryConfirmationState.USER_CONFIRMED.name
        )

        val updateResult = patternRepository.updatePatternStateWithEvent(
            id = patternId,
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            status = PatternStatus.ACTIVE,
            updatedAt = now,
            event = event
        )
        if (updateResult is Result.Failure) return updateResult

        println("IronMindLifecycle Pattern [CONFIRM] patternId=$patternId userId=$userId")
        return Result.Success(Unit)
    }
}
