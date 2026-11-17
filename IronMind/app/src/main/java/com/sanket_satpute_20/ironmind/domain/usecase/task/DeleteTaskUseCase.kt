package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class DeleteTaskUseCase(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(userId: String, taskId: String): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("User ID cannot be blank"))
        }
        if (taskId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Task ID cannot be blank"))
        }
        return repository.deleteTask(userId, taskId)
    }
}
