package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class UpdateTaskStatusUseCase(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val eventRepository: EventRepository,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(taskId: String, newStatus: TaskStatus): Result<Task, Exception> {
        val existingResult = repository.getTask(taskId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val task = (existingResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Task not found"))

        if (task.status == newStatus) {
            return Result.Success(task)
        }

        if (!isValidTransition(task.status, newStatus)) {
            return Result.Failure(
                IllegalStateException("Invalid state transition from ${task.status} to $newStatus")
            )
        }

        val now = clock.currentTimeMillis()
        
        var completedAt = task.completedAt
        var postponedAt = task.postponedAt
        
        when (newStatus) {
            TaskStatus.COMPLETED, TaskStatus.FAILED -> {
                completedAt = completedAt ?: now
            }
            TaskStatus.POSTPONED -> {
                postponedAt = postponedAt ?: now
                completedAt = null
            }
            else -> {
                completedAt = null
            }
        }

        val updatedTask = task.copy(
            status = newStatus,
            completedAt = completedAt,
            postponedAt = postponedAt,
            updatedAt = now
        )

        val eventType = when (newStatus) {
            TaskStatus.IN_PROGRESS -> EventType.TASK_STARTED
            TaskStatus.COMPLETED -> EventType.TASK_COMPLETED
            TaskStatus.POSTPONED -> EventType.TASK_POSTPONED
            TaskStatus.CANCELLED -> EventType.TASK_CANCELLED
            TaskStatus.PENDING, TaskStatus.FAILED -> null
        }

        val event = eventType?.let { type ->
            Event(
                id = idGenerator.generateId(),
                userId = task.userId,
                type = type,
                entityType = "TASK",
                entityId = task.id,
                occurredAt = now,
                recordedAt = now,
                source = EntitySource.USER,
                previousState = task.status.name,
                newState = updatedTask.status.name,
                metadata = buildString {
                    append("transitionedAt=").append(now)
                    append(";previousDueAt=").append(task.dueAt ?: "none")
                    append(";newDueAt=").append(updatedTask.dueAt ?: "none")
                    append(";previousScheduledAt=").append(task.scheduledAt ?: "none")
                    append(";newScheduledAt=").append(updatedTask.scheduledAt ?: "none")
                    append(";postponedAt=").append(updatedTask.postponedAt ?: "none")
                    append(";completedAt=").append(updatedTask.completedAt ?: "none")
                }
            )
        }

        return persistTaskWithEvent(
            repository = repository,
            eventRepository = eventRepository,
            previousTask = task,
            updatedTask = updatedTask,
            event = event
        )
    }

    private fun isValidTransition(current: TaskStatus, newStatus: TaskStatus): Boolean {
        if (current == newStatus) return true
        return when (current) {
            TaskStatus.COMPLETED, TaskStatus.CANCELLED -> false
            TaskStatus.PENDING -> newStatus in setOf(
                TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED, TaskStatus.POSTPONED, TaskStatus.CANCELLED, TaskStatus.FAILED
            )
            TaskStatus.IN_PROGRESS -> newStatus in setOf(
                TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.POSTPONED, TaskStatus.CANCELLED, TaskStatus.PENDING
            )
            TaskStatus.POSTPONED -> newStatus in setOf(
                TaskStatus.PENDING, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED, TaskStatus.CANCELLED
            )
            TaskStatus.FAILED -> newStatus in setOf(
                TaskStatus.IN_PROGRESS, TaskStatus.PENDING, TaskStatus.CANCELLED
            )
        }
    }
}

internal suspend fun persistTaskWithEvent(
    repository: TaskRepository,
    eventRepository: EventRepository,
    previousTask: Task,
    updatedTask: Task,
    event: Event?
): Result<Task, Exception> {
    val result = if (event != null) {
        repository.saveTaskWithEvent(
            task = updatedTask,
            previousTask = previousTask,
            event = event,
            eventRepository = eventRepository
        )
    } else {
        repository.saveTask(updatedTask)
    }
    return when (result) {
        is Result.Failure -> Result.Failure(result.error)
        is Result.Success -> Result.Success(updatedTask)
    }
}
