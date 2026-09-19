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

class EditCommitmentUseCase(
    private val repository: CommitmentRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        commitmentId: String,
        title: String?,
        description: String?,
        priority: Int?,
        scheduledStartAt: Long?,
        scheduledEndAt: Long?
    ): Result<Commitment, Exception> {
        val existingResult = repository.getCommitment(commitmentId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val commitment = (existingResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Commitment not found"))

        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        val updatedScheduledStartAt = scheduledStartAt ?: commitment.scheduledStartAt
        val now = clock.currentTimeMillis()

        val updatedCommitment = commitment.copy(
            title = title ?: commitment.title,
            description = description ?: commitment.description,
            priority = priority ?: commitment.priority,
            scheduledStartAt = updatedScheduledStartAt,
            scheduledEndAt = scheduledEndAt ?: commitment.scheduledEndAt,
            updatedAt = now
        )

        return when (val saveResult = repository.saveCommitment(updatedCommitment)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> {
                if (updatedScheduledStartAt != commitment.scheduledStartAt || (title != null && title != commitment.title)) {
                    if (updatedScheduledStartAt != null) {
                        reminderScheduler.scheduleReminder(updatedCommitment.id, updatedScheduledStartAt, updatedCommitment.title)
                    }
                }
                // Record a correction event — a user correction is high-value evidence.
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
                println("IronMindLifecycle [Commitment] [CORRECTED] commitmentId=$commitmentId")
                Result.Success(updatedCommitment)
            }
        }
    }
}
