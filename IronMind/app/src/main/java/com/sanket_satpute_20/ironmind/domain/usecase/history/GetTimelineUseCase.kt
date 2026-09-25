package com.sanket_satpute_20.ironmind.domain.usecase.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class GetTimelineUseCase(
    private val eventRepository: EventRepository,
    private val commitmentRepository: CommitmentRepository,
    private val reflectionRepository: ReflectionRepository,
    private val goalRepository: GoalRepository,
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository,
    private val logger: IronLogger? = null
) {
    suspend operator fun invoke(
        userId: String,
        goalId: String? = null,
        commitmentId: String? = null,
        reflectionId: String? = null,
        memoryId: String? = null,
        eventId: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): Result<List<TimelineItem>, Exception> {
        logger?.logLifecycle("History", "LOAD_START")

        val eventsResult = if (startTime != null && endTime != null) {
            eventRepository.getEventsForDateRange(userId, startTime, endTime)
        } else if (eventId != null) {
            when (val res = eventRepository.getEvent(eventId)) {
                is Result.Success -> Result.Success(res.data?.let { listOf(it) } ?: emptyList())
                is Result.Failure -> Result.Failure(res.error)
            }
        } else if (goalId != null) {
            eventRepository.getEventsForEntity(goalId)
        } else if (commitmentId != null) {
            eventRepository.getEventsForEntity(commitmentId)
        } else if (reflectionId != null) {
            eventRepository.getEventsForEntity(reflectionId)
        } else if (memoryId != null) {
            eventRepository.getEventsForEntity(memoryId)
        } else {
            eventRepository.getEventsForUser(userId)
        }

        if (eventsResult is Result.Failure) {
            logger?.logLifecycle("History", "LOAD_FAILURE", mapOf("error" to (eventsResult.error.message ?: "Unknown error")))
            return Result.Failure(eventsResult.error)
        }

        val events = (eventsResult as Result.Success).data
        val timelineItems = mutableListOf<TimelineItem>()

        for (event in events) {
            when (event.type) {
                // Commitment Events
                EventType.COMMITMENT_CREATED,
                EventType.COMMITMENT_COMMITTED,
                EventType.COMMITMENT_STARTED,
                EventType.COMMITMENT_COMPLETED,
                EventType.COMMITMENT_POSTPONED,
                EventType.COMMITMENT_RESCHEDULED,
                EventType.COMMITMENT_MISSED,
                EventType.COMMITMENT_RECOVERED,
                EventType.COMMITMENT_ABANDONED,
                EventType.COMMITMENT_UPDATED -> {
                    if (event.entityId != null) {
                        val commitmentResult = commitmentRepository.getCommitment(event.entityId)
                        if (commitmentResult is Result.Success && commitmentResult.data != null) {
                            timelineItems.add(
                                TimelineItem.CommitmentEvent(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    commitmentTitle = commitmentResult.data.title,
                                    actionDescription = getHumanReadableAction(event.type)
                                )
                            )
                        }
                    }
                }

                // Reflection Events
                EventType.REFLECTION_CREATED,
                EventType.REFLECTION_STARTED,
                EventType.REFLECTION_COMPLETED -> {
                    if (event.entityId != null) {
                        val reflectionResult = reflectionRepository.getReflection(event.entityId)
                        if (reflectionResult is Result.Success && reflectionResult.data != null) {
                            timelineItems.add(
                                TimelineItem.ReflectionRecorded(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    sentiment = reflectionResult.data.sentiment ?: "UNKNOWN"
                                )
                            )
                        }
                    }
                }

                // Goal Events
                EventType.GOAL_CREATED,
                EventType.GOAL_UPDATED,
                EventType.GOAL_COMPLETED,
                EventType.GOAL_PAUSED,
                EventType.GOAL_ABANDONED,
                EventType.GOAL_REACTIVATED -> {
                    if (event.entityId != null) {
                        val goalResult = goalRepository.getGoal(event.entityId)
                        if (goalResult is Result.Success && goalResult.data != null) {
                            timelineItems.add(
                                TimelineItem.GoalEvent(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    goalTitle = goalResult.data.title,
                                    actionDescription = getHumanReadableAction(event.type)
                                )
                            )
                        }
                    }
                }

                // Plan Events
                EventType.PLAN_CREATED,
                EventType.PLAN_UPDATED,
                EventType.PLAN_ACTIVATED,
                EventType.PLAN_COMPLETED,
                EventType.PLAN_PAUSED,
                EventType.PLAN_REPLACED -> {
                    if (event.entityId != null) {
                        val planResult = planRepository.getPlan(event.entityId)
                        if (planResult is Result.Success && planResult.data != null) {
                            timelineItems.add(
                                TimelineItem.PlanEvent(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    planTitle = planResult.data.title,
                                    actionDescription = getHumanReadableAction(event.type)
                                )
                            )
                        }
                    }
                }

                // Task Events
                EventType.TASK_CREATED,
                EventType.TASK_STARTED,
                EventType.TASK_COMPLETED,
                EventType.TASK_POSTPONED,
                EventType.TASK_CANCELLED,
                EventType.TASK_ABANDONED -> {
                    if (event.entityId != null) {
                        val taskResult = taskRepository.getTask(event.entityId)
                        if (taskResult is Result.Success && taskResult.data != null) {
                            timelineItems.add(
                                TimelineItem.TaskEvent(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    taskTitle = taskResult.data.title,
                                    actionDescription = getHumanReadableAction(event.type)
                                )
                            )
                        }
                    }
                }

                // Outcome Events
                EventType.OUTCOME_RECORDED -> {
                    timelineItems.add(
                        TimelineItem.OutcomeRecorded(
                            id = event.id,
                            timestamp = event.occurredAt,
                            actionDescription = getHumanReadableAction(event.type)
                        )
                    )
                }

                // Major Events (e.g., Protection)
                EventType.PROTECTION_STARTED,
                EventType.PROTECTION_ENDED,
                EventType.PROTECTION_ENABLED,
                EventType.PROTECTION_DISABLED,
                EventType.PROTECTION_OVERRIDDEN,
                EventType.PROTECTION_EXPIRED -> {
                    timelineItems.add(
                        TimelineItem.MajorEvent(
                            id = event.id,
                            timestamp = event.occurredAt,
                            description = getHumanReadableAction(event.type)
                        )
                    )
                }

                else -> {
                    // Ignore noisy/internal events for now
                }
            }
        }

        // Reverse because DAO returns ASC (older first) by default, and we want DESC (newer first) for timeline UI,
        // which naturally flips the ASC `id` order into a deterministic DESC order.
        val orderedItems = timelineItems.reversed()

        logger?.logLifecycle("History", "LOAD_SUCCESS", mapOf("count" to orderedItems.size.toString()))
        return Result.Success(orderedItems)
    }

    private fun getHumanReadableAction(type: EventType): String {
        return type.name
            .lowercase()
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
