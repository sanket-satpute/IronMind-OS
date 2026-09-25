package com.sanket_satpute_20.ironmind.domain.usecase.plan

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType

class CreatePlanUseCase(
    private val repository: PlanRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        goalId: String?,
        title: String,
        description: String
    ): Result<Plan, Exception> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        val now = clock.currentTimeMillis()
        val plan = Plan(
            id = idGenerator.generateId(),
            userId = userId,
            goalId = goalId,
            title = title,
            description = description,
            status = PlanStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            startedAt = now, // Since we default to ACTIVE
            source = EntitySource.USER
        )

        return when (val result = repository.savePlan(plan)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                val event = Event(
                    id = idGenerator.generateId(),
                    userId = userId,
                    type = EventType.PLAN_CREATED,
                    entityType = "PLAN",
                    entityId = plan.id,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER
                )
                eventRepository.saveEvent(event)
                Result.Success(plan)
            }
        }
    }
}
