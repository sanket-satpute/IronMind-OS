package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository

class CancelCommitmentScheduleUseCase(
    private val repository: CommitmentRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        commitmentId: String
    ): Result<Commitment, Exception> {
        println("IronMindLifecycle [Scheduling] [CANCEL_REQUEST]")
        val now = clock.currentTimeMillis()
        
        val existingResult = repository.getCommitment(commitmentId)
        if (existingResult is Result.Failure) {
            println("IronMindLifecycle [Scheduling] [CANCEL_FAILURE]")
            return Result.Failure(existingResult.error)
        }

        val commitment = (existingResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Commitment not found"))

        if (commitment.scheduledStartAt == null) {
            // Already no schedule, just return
            return Result.Success(commitment)
        }

        val updatedCommitment = commitment.copy(
            scheduledStartAt = null,
            updatedAt = now
        )

        return when (val saveResult = repository.saveCommitment(updatedCommitment)) {
            is Result.Failure -> {
                println("IronMindLifecycle [Scheduling] [CANCEL_FAILURE]")
                Result.Failure(saveResult.error)
            }
            is Result.Success -> {
                reminderScheduler.cancelReminder(commitmentId)
                
                val event = Event(
                    id = idGenerator.generateId(),
                    userId = userId,
                    type = EventType.COMMITMENT_UPDATED,
                    entityType = "COMMITMENT",
                    entityId = commitmentId,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER
                )
                eventRepository.saveEvent(event)
                println("IronMindLifecycle [Scheduling] [CANCEL_SUCCESS]")
                Result.Success(updatedCommitment)
            }
        }
    }
}
