package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
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
        targetAt: Long?,
        status: GoalStatus? = null
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
        val newStatus = status ?: existingGoal.status
        val completedAt = if (newStatus == GoalStatus.COMPLETED || newStatus == GoalStatus.ENDED) {
            existingGoal.completedAt ?: now
        } else if (status != null) {
            null
        } else {
            existingGoal.completedAt
        }

        val updatedGoal = existingGoal.copy(
            title = title ?: existingGoal.title,
            description = description ?: existingGoal.description,
            why = why ?: existingGoal.why,
            importance = importance ?: existingGoal.importance,
            targetAt = targetAt ?: existingGoal.targetAt,
            status = newStatus,
            completedAt = completedAt,
            updatedAt = now
        )

        return when (val result = repository.saveGoal(updatedGoal)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                val eventType = when {
                    status == GoalStatus.COMPLETED -> EventType.GOAL_COMPLETED
                    status == GoalStatus.PAUSED -> EventType.GOAL_PAUSED
                    status == GoalStatus.ACTIVE && existingGoal.status != GoalStatus.ACTIVE -> EventType.GOAL_REACTIVATED
                    else -> EventType.GOAL_UPDATED
                }
                // Record a correction event — a user correction is high-value evidence.
                val event = Event(
                    id = idGenerator.generateId(),
                    userId = userId,
                    type = eventType,
                    entityType = "GOAL",
                    entityId = goalId,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER
                )
                eventRepository.saveEvent(event)
                println("IronMindLifecycle [Goal] [CORRECTED] goalId=$goalId, type=$eventType")
                Result.Success(updatedGoal)
            }
        }
    }
}
