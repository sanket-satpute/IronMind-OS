package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class UpdateTaskStatusUseCase(
    private val repository: TaskRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(taskId: String, newStatus: TaskStatus): Result<Task, Exception> {
        val existingResult = repository.getTask(taskId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val task = (existingResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Task not found"))

        if (task.status == newStatus) {
            return Result.Success(task)
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

        return when (val saveResult = repository.saveTask(updatedTask)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> Result.Success(updatedTask)
        }
    }
}
