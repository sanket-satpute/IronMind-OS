package com.sanket_satpute_20.ironmind.domain.usecase.plan

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository

class UpdatePlanStatusUseCase(
    private val repository: PlanRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator? = null,
    private val eventRepository: EventRepository? = null
) {
    suspend operator fun invoke(
        userId: String,
        planId: String,
        newStatus: PlanStatus
    ): Result<Plan, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }
        if (planId.isBlank()) {
            return Result.Failure(IllegalArgumentException("planId cannot be blank"))
        }

        val existingResult = repository.getPlan(planId)
        if (existingResult is Result.Failure) {
            return Result.Failure(existingResult.error)
        }
        val existingPlan = (existingResult as Result.Success).data
            ?: return Result.Failure(IllegalArgumentException("Plan not found: $planId"))

        if (existingPlan.userId != userId) {
            return Result.Failure(IllegalStateException("Plan $planId does not belong to user $userId"))
        }

        if (existingPlan.status == newStatus) {
            return Result.Success(existingPlan)
        }

        // Validate state transitions
        val isValid = when (existingPlan.status) {
            PlanStatus.DRAFT -> newStatus == PlanStatus.ACTIVE || newStatus == PlanStatus.ABANDONED
            PlanStatus.ACTIVE -> newStatus == PlanStatus.PAUSED || newStatus == PlanStatus.COMPLETED || newStatus == PlanStatus.ABANDONED
            PlanStatus.PAUSED -> newStatus == PlanStatus.ACTIVE || newStatus == PlanStatus.COMPLETED || newStatus == PlanStatus.ABANDONED
            PlanStatus.COMPLETED -> newStatus == PlanStatus.ACTIVE
            PlanStatus.ABANDONED -> false
        }

        if (!isValid) {
            return Result.Failure(
                IllegalStateException("Invalid plan status transition from ${existingPlan.status} to $newStatus")
            )
        }

        val now = clock.currentTimeMillis()
        val completedAt = when (newStatus) {
            PlanStatus.COMPLETED -> existingPlan.completedAt ?: now
            PlanStatus.ACTIVE, PlanStatus.PAUSED, PlanStatus.DRAFT, PlanStatus.ABANDONED -> null
        }

        val startedAt = if (newStatus == PlanStatus.ACTIVE && existingPlan.startedAt == null) {
            now
        } else {
            existingPlan.startedAt
        }

        val updatedPlan = existingPlan.copy(
            status = newStatus,
            updatedAt = now,
            startedAt = startedAt,
            completedAt = completedAt
        )

        return when (val saveResult = repository.savePlan(updatedPlan)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> {
                if (idGenerator != null && eventRepository != null) {
                    try {
                        val eventType = when (newStatus) {
                            PlanStatus.ACTIVE -> EventType.PLAN_ACTIVATED
                            PlanStatus.PAUSED -> EventType.PLAN_PAUSED
                            PlanStatus.COMPLETED -> EventType.PLAN_COMPLETED
                            PlanStatus.ABANDONED -> EventType.PLAN_UPDATED
                            PlanStatus.DRAFT -> EventType.PLAN_UPDATED
                        }
                        val event = Event(
                            id = idGenerator.generateId(),
                            userId = userId,
                            type = eventType,
                            entityType = "PLAN",
                            entityId = planId,
                            occurredAt = now,
                            recordedAt = now,
                            source = EntitySource.USER
                        )
                        eventRepository.saveEvent(event)
                    } catch (e: Exception) {
                        println("IronMindLifecycle [Plan] [EVENT_SAVE_FAILED] reason=${e.message}")
                    }
                }
                Result.Success(updatedPlan)
            }
        }
    }
}
