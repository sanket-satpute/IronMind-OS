package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class EditTaskUseCase(
    private val repository: TaskRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(
        taskId: String,
        title: String?,
        description: String?,
        priority: Int?,
        estimatedDurationMinutes: Int?,
        scheduledAt: Long?,
        dueAt: Long?
    ): Result<Task, Exception> {
        val existingResult = repository.getTask(taskId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val task = (existingResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Task not found"))

        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        val updatedTask = task.copy(
            title = title ?: task.title,
            description = description ?: task.description,
            priority = priority ?: task.priority,
            estimatedDurationMinutes = estimatedDurationMinutes ?: task.estimatedDurationMinutes,
            scheduledAt = scheduledAt ?: task.scheduledAt,
            dueAt = dueAt ?: task.dueAt,
            updatedAt = clock.currentTimeMillis()
        )

        return when (val saveResult = repository.saveTask(updatedTask)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> Result.Success(updatedTask)
        }
    }
}
