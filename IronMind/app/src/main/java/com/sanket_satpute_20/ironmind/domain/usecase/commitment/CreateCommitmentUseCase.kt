package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType

class CreateCommitmentUseCase(
    private val repository: CommitmentRepository,
    private val reminderScheduler: ReminderScheduler,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        goalId: String?,
        planId: String?,
        taskId: String?,
        parentCommitmentId: String?,
        title: String,
        description: String,
        priority: Int,
        scheduledStartAt: Long? = null,
        scheduledEndAt: Long? = null,
        initialStatus: CommitmentStatus = CommitmentStatus.PLANNED
    ): Result<Commitment, Exception> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        if (initialStatus != CommitmentStatus.PLANNED && initialStatus != CommitmentStatus.COMMITTED) {
            return Result.Failure(IllegalArgumentException("Initial status must be PLANNED or COMMITTED"))
        }

        val now = clock.currentTimeMillis()
        val commitment = Commitment(
            id = idGenerator.generateId(),
            userId = userId,
            goalId = goalId,
            planId = planId,
            taskId = taskId,
            parentCommitmentId = parentCommitmentId,
            title = title,
            description = description,
            committedAt = if (initialStatus == CommitmentStatus.COMMITTED) now else 0L,
            scheduledStartAt = scheduledStartAt,
            scheduledEndAt = scheduledEndAt,
            status = initialStatus,
            priority = priority,
            source = EntitySource.USER,
            createdAt = now,
            updatedAt = now
        )

        return when (val result = repository.saveCommitment(commitment)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                if (scheduledStartAt != null) {
                    reminderScheduler.scheduleReminder(commitment.id, scheduledStartAt, commitment.title)
                }
                
                val createEvent = Event(
                    id = idGenerator.generateId(),
                    userId = userId,
                    type = EventType.COMMITMENT_CREATED,
                    entityType = "COMMITMENT",
                    entityId = commitment.id,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER
                )
                eventRepository.saveEvent(createEvent)
                println("IronMindLifecycle [Commitment] [CREATED] commitmentId=${commitment.id}")

                if (initialStatus == CommitmentStatus.COMMITTED) {
                    val commitEvent = Event(
                        id = idGenerator.generateId(),
                        userId = userId,
                        type = EventType.COMMITMENT_COMMITTED,
                        entityType = "COMMITMENT",
                        entityId = commitment.id,
                        occurredAt = now,
                        recordedAt = now,
                        source = EntitySource.USER,
                        previousState = CommitmentStatus.PLANNED.name,
                        newState = CommitmentStatus.COMMITTED.name
                    )
                    eventRepository.saveEvent(commitEvent)
                    println("IronMindLifecycle [Commitment] [COMMITTED] commitmentId=${commitment.id}")
                }
                
                Result.Success(commitment)
            }
        }
    }
}
