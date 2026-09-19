package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

class EditGoalUseCase(
    private val repository: GoalRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        goalId: String,
        title: String?,
        description: String?,
        why: String?,
        importance: Int?,
        targetAt: Long?
    ): Result<Goal, Exception> {
        val existingGoalResult = repository.getGoal(goalId)
        if (existingGoalResult is Result.Failure) {
            return Result.Failure(existingGoalResult.error)
        }

        val existingGoal = (existingGoalResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Goal not found"))

        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }
        if (importance != null && importance !in 1..10) {
            return Result.Failure(IllegalArgumentException("Importance must be between 1 and 10"))
        }

        val now = clock.currentTimeMillis()
        val updatedGoal = existingGoal.copy(
            title = title ?: existingGoal.title,
            description = description ?: existingGoal.description,
            why = why ?: existingGoal.why,
            importance = importance ?: existingGoal.importance,
            targetAt = targetAt ?: existingGoal.targetAt,
            updatedAt = now
        )

        return when (val result = repository.saveGoal(updatedGoal)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                // Record a correction event — a user correction is high-value evidence.
                val event = Event(
                    id = idGenerator.generateId(),
                    userId = userId,
                    type = EventType.GOAL_UPDATED,
                    entityType = "GOAL",
                    entityId = goalId,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER
                )
                eventRepository.saveEvent(event)
                println("IronMindLifecycle [Goal] [CORRECTED] goalId=$goalId")
                Result.Success(updatedGoal)
            }
        }
    }
}
