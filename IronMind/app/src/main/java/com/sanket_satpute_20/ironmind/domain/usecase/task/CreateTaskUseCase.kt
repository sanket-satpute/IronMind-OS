package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class CreateTaskUseCase(
    private val repository: TaskRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        goalId: String?,
        planId: String?,
        title: String,
        description: String,
        priority: Int,
        estimatedDurationMinutes: Int? = null,
        scheduledAt: Long? = null,
        dueAt: Long? = null
    ): Result<Task, Exception> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        val now = clock.currentTimeMillis()
        val task = Task(
            id = idGenerator.generateId(),
            userId = userId,
            goalId = goalId,
            planId = planId,
            title = title,
            description = description,
            status = TaskStatus.PENDING,
            priority = priority,
            estimatedDurationMinutes = estimatedDurationMinutes,
            scheduledAt = scheduledAt,
            dueAt = dueAt,
            createdAt = now,
            updatedAt = now,
            source = EntitySource.USER
        )

        return when (val result = repository.saveTask(task)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> Result.Success(task)
        }
    }
}
