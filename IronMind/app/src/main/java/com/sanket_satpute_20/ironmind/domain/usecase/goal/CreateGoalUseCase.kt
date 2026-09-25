package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.EntitySource

class CreateGoalUseCase(
    private val repository: GoalRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        title: String,
        description: String,
        why: String,
        importance: Int,
        targetAt: Long?
    ): Result<Goal, Exception> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }
        if (importance !in 1..10) {
            return Result.Failure(IllegalArgumentException("Importance must be between 1 and 10"))
        }

        val now = clock.currentTimeMillis()
        val goal = Goal(
            id = idGenerator.generateId(),
            userId = userId,
            title = title,
            description = description,
            why = why,
            importance = importance,
            status = GoalStatus.ACTIVE,
            targetAt = targetAt,
            startedAt = now,
            createdAt = now,
            updatedAt = now
        )

        return when (val result = repository.saveGoal(goal)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                val event = Event(
                    id = idGenerator.generateId(),
                    userId = userId,
                    type = EventType.GOAL_CREATED,
                    entityType = "GOAL",
                    entityId = goal.id,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER
                )
                eventRepository.saveEvent(event)
                Result.Success(goal)
            }
        }
    }
}
