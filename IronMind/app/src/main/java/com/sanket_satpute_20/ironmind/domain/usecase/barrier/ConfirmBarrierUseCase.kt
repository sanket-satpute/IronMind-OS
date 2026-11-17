package com.sanket_satpute_20.ironmind.domain.usecase.barrier

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository

class ConfirmBarrierUseCase(
    private val barrierRepository: BarrierRepository,
    private val eventRepository: EventRepository? = null,
    private val idGenerator: IdGenerator? = null,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        barrierId: String
    ): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        if (barrierId.isBlank()) {
            return Result.Failure(IllegalArgumentException("barrierId must not be blank"))
        }

        val barrierResult = barrierRepository.getBarrier(barrierId)
        if (barrierResult is Result.Failure) return Result.Failure(barrierResult.error)
        val existing = (barrierResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Barrier not found: $barrierId"))

        if (existing.userId != userId) {
            return Result.Failure(IllegalStateException("Barrier $barrierId does not belong to user $userId"))
        }

        // Idempotency check: if already confirmed, succeed without duplicate event
        if (existing.confirmationState == BarrierConfirmationState.CONFIRMED && existing.status == BarrierStatus.ACTIVE) {
            return Result.Success(Unit)
        }

        // Only eligible UNCONFIRMED barriers can be confirmed
        if (existing.confirmationState in setOf(BarrierConfirmationState.REJECTED, BarrierConfirmationState.DISMISSED)) {
            return Result.Failure(IllegalStateException("Cannot confirm barrier in confirmationState ${existing.confirmationState}"))
        }

        val now = clock.currentTimeMillis()
        val event = Event(
            id = idGenerator?.generateId() ?: java.util.UUID.randomUUID().toString(),
            userId = userId,
            type = EventType.BARRIER_CONFIRMED,
            entityType = "BARRIER",
            entityId = barrierId,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            previousState = existing.confirmationState.name,
            newState = BarrierConfirmationState.CONFIRMED.name
        )

        val updateResult = barrierRepository.updateBarrierStateWithEvent(
            id = barrierId,
            userId = userId,
            confirmationState = BarrierConfirmationState.CONFIRMED,
            status = BarrierStatus.ACTIVE,
            now = now,
            event = event
        )
        if (updateResult is Result.Failure) return updateResult

        println("IronMindLifecycle Barrier [CONFIRM] barrierId=$barrierId userId=$userId")
        return Result.Success(Unit)
    }
}
