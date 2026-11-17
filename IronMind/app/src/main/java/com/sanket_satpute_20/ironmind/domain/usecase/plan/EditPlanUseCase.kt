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

class EditPlanUseCase(
    private val repository: PlanRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator? = null,
    private val eventRepository: EventRepository? = null
) {
    suspend operator fun invoke(
        userId: String,
        planId: String,
        title: String? = null,
        description: String? = null,
        status: PlanStatus? = null
    ): Result<Plan, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }
        if (planId.isBlank()) {
            return Result.Failure(IllegalArgumentException("planId cannot be blank"))
        }
        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
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

        val targetStatus = status ?: existingPlan.status

        // Validate state transitions if status is changing
        if (targetStatus != existingPlan.status) {
            val isValid = when (existingPlan.status) {
                PlanStatus.DRAFT -> targetStatus == PlanStatus.ACTIVE || targetStatus == PlanStatus.ABANDONED
                PlanStatus.ACTIVE -> targetStatus == PlanStatus.PAUSED || targetStatus == PlanStatus.COMPLETED || targetStatus == PlanStatus.ABANDONED
                PlanStatus.PAUSED -> targetStatus == PlanStatus.ACTIVE || targetStatus == PlanStatus.COMPLETED || targetStatus == PlanStatus.ABANDONED
                PlanStatus.COMPLETED -> targetStatus == PlanStatus.ACTIVE
                PlanStatus.ABANDONED -> false
            }
            if (!isValid) {
                return Result.Failure(
                    IllegalStateException("Invalid plan status transition from ${existingPlan.status} to $targetStatus")
                )
            }
        }

        val now = clock.currentTimeMillis()
        val completedAt = when (targetStatus) {
            PlanStatus.COMPLETED -> existingPlan.completedAt ?: now
            else -> if (status != null && existingPlan.completedAt != null) null else existingPlan.completedAt
        }

        val startedAt = if (targetStatus == PlanStatus.ACTIVE && existingPlan.startedAt == null) {
            now
        } else {
            existingPlan.startedAt
        }

        val updatedPlan = existingPlan.copy(
            title = title?.trim() ?: existingPlan.title,
            description = description?.trim() ?: existingPlan.description,
            status = targetStatus,
            updatedAt = now,
            startedAt = startedAt,
            completedAt = completedAt
        )

        return when (val saveResult = repository.savePlan(updatedPlan)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> {
                if (idGenerator != null && eventRepository != null) {
                    try {
                        val eventType = when {
                            status != null && status == PlanStatus.COMPLETED -> EventType.PLAN_COMPLETED
                            status != null && status == PlanStatus.PAUSED -> EventType.PLAN_PAUSED
                            status != null && status == PlanStatus.ACTIVE && existingPlan.status != PlanStatus.ACTIVE -> EventType.PLAN_ACTIVATED
                            else -> EventType.PLAN_UPDATED
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
