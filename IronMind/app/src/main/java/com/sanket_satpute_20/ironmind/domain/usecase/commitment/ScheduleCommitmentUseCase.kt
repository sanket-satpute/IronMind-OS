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

class ScheduleCommitmentUseCase(
    private val repository: CommitmentRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        commitmentId: String,
        scheduledStartAt: Long
    ): Result<Commitment, Exception> {
        val now = clock.currentTimeMillis()
        
        if (scheduledStartAt <= now) {
            println("IronMindLifecycle [Scheduling] [SCHEDULE_FAILURE] reason=past_time")
            return Result.Failure(IllegalArgumentException("Cannot schedule a commitment in the past"))
        }

        println("IronMindLifecycle [Scheduling] [SCHEDULE_REQUEST]")

        val existingResult = repository.getCommitment(commitmentId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val commitment = (existingResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Commitment not found"))

        val updatedCommitment = commitment.copy(
            scheduledStartAt = scheduledStartAt,
            updatedAt = now
        )

        return when (val saveResult = repository.saveCommitment(updatedCommitment)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> {
                // If there's an existing schedule, cancel it first. Not strictly necessary with exact alarm update,
                // but AndroidReminderScheduler overrides the pending intent anyway.
                reminderScheduler.scheduleReminder(updatedCommitment.id, scheduledStartAt, updatedCommitment.title)
                
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
                println("IronMindLifecycle [Scheduling] [SCHEDULE_SUCCESS] scheduleId=${updatedCommitment.id}")
                Result.Success(updatedCommitment)
            }
        }
    }
}
