package com.sanket_satpute_20.ironmind.domain.usecase.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository

class GetTimelineUseCase(
    private val eventRepository: EventRepository,
    private val commitmentRepository: CommitmentRepository,
    private val reflectionRepository: ReflectionRepository,
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(userId: String): Result<List<TimelineItem>, Exception> {
        val eventsResult = eventRepository.getEventsForUser(userId)
        if (eventsResult is Result.Failure) {
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
                EventType.COMMITMENT_ABANDONED -> {
                    if (event.entityId != null) {
                        val commitmentResult = commitmentRepository.getCommitment(event.entityId)
                        if (commitmentResult is Result.Success && commitmentResult.data != null) {
                            timelineItems.add(
                                TimelineItem.CommitmentEvent(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    commitmentTitle = commitmentResult.data.title,
                                    previousStatus = event.previousState,
                                    newStatus = event.newState ?: event.type.name.removePrefix("COMMITMENT_")
                                )
                            )
                        }
                    }
                }
                
                // Reflection Events
                EventType.REFLECTION_CREATED -> {
                    if (event.entityId != null) {
                        val reflectionResult = reflectionRepository.getReflection(event.entityId)
                        if (reflectionResult is Result.Success && reflectionResult.data != null) {
                            timelineItems.add(
                                TimelineItem.ReflectionRecorded(
                                    id = event.id,
                                    timestamp = event.occurredAt,
                                    content = reflectionResult.data.content,
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
                                    newStatus = event.newState ?: event.type.name.removePrefix("GOAL_")
                                )
                            )
                        }
                    }
                }
                
                // Major Events (e.g., Protection)
                EventType.PROTECTION_STARTED,
                EventType.PROTECTION_ENDED,
                EventType.PROTECTION_OVERRIDDEN -> {
                    timelineItems.add(
                        TimelineItem.MajorEvent(
                            id = event.id,
                            timestamp = event.occurredAt,
                            description = "Protection Session ${event.type.name.removePrefix("PROTECTION_")}"
                        )
                    )
                }
                
                else -> {
                    // Ignore noisy/internal events for now
                }
            }
        }
        
        return Result.Success(timelineItems.sortedByDescending { it.timestamp })
    }
}
