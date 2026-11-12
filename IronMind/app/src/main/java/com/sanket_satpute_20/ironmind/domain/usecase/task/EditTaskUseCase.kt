package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class EditTaskUseCase(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(
        taskId: String,
        title: String? = null,
        description: String? = null,
        priority: Int? = null,
        estimatedDurationMinutes: Int? = null,
        scheduledAt: Long? = null,
        dueAt: Long? = null,
        clearDueAt: Boolean = false
    ): Result<Task, Exception> {
        val existingResult = repository.getTask(taskId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val task = (existingResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Task not found"))

        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        val updatedTitle = title ?: task.title
        val updatedDescription = description ?: task.description
        val updatedPriority = priority ?: task.priority
        val updatedDuration = estimatedDurationMinutes ?: task.estimatedDurationMinutes
        val updatedScheduledAt = scheduledAt ?: task.scheduledAt
        val updatedDueAt = if (clearDueAt) null else (dueAt ?: task.dueAt)

        val hasAnyChange = updatedTitle != task.title ||
            updatedDescription != task.description ||
            updatedPriority != task.priority ||
            updatedDuration != task.estimatedDurationMinutes ||
            updatedScheduledAt != task.scheduledAt ||
            updatedDueAt != task.dueAt

        if (!hasAnyChange) {
            return Result.Success(task)
        }

        val now = clock.currentTimeMillis()
        val updatedTask = task.copy(
            title = updatedTitle,
            description = updatedDescription,
            priority = updatedPriority,
            estimatedDurationMinutes = updatedDuration,
            scheduledAt = updatedScheduledAt,
            dueAt = updatedDueAt,
            updatedAt = now
        )

        val dueMovedForward = task.dueAt != null && updatedDueAt != null && updatedDueAt > task.dueAt
        val scheduledMovedForward = task.scheduledAt != null && updatedScheduledAt != null && updatedScheduledAt > task.scheduledAt

        val rescheduleEvent = if (dueMovedForward || scheduledMovedForward) {
            Event(
                id = idGenerator.generateId(),
                userId = task.userId,
                type = EventType.TASK_POSTPONED,
                entityType = "TASK",
                entityId = task.id,
                occurredAt = now,
                recordedAt = now,
                source = EntitySource.USER,
                previousState = task.status.name,
                newState = updatedTask.status.name,
                metadata = buildString {
                    append("reason=forward_reschedule")
                    append(";rescheduledAt=").append(now)
                    append(";previousDueAt=").append(task.dueAt ?: "none")
                    append(";newDueAt=").append(updatedDueAt ?: "none")
                    append(";previousScheduledAt=").append(task.scheduledAt ?: "none")
                    append(";newScheduledAt=").append(updatedScheduledAt ?: "none")
                }
            )
        } else {
            null
        }

        return persistTaskWithEvent(
            repository = repository,
            eventRepository = eventRepository,
            previousTask = task,
            updatedTask = updatedTask,
            event = rescheduleEvent
        )
    }
}
