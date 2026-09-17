package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskRepositoryImpl(
    private val dao: IronMindDao
) : TaskRepository {

    override suspend fun saveTask(task: Task): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insertTask(task.toEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getTask(id: String): Result<Task?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getTask(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getTasksForPlan(planId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getTasksForGoal(goalId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            userId = userId,
            goalId = goalId,
            planId = planId,
            parentTaskId = parentTaskId,
            title = title,
            description = description,
            status = status.name,
            priority = priority,
            estimatedDurationMinutes = estimatedDurationMinutes,
            scheduledAt = scheduledAt,
            dueAt = dueAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            source = source.name,
            schemaVersion = 1
        )
    }

    private fun TaskEntity.toDomain(): Task {
        return Task(
            id = id,
            userId = userId,
            goalId = goalId,
            planId = planId,
            parentTaskId = parentTaskId,
            title = title,
            description = description,
            status = TaskStatus.valueOf(status),
            priority = priority,
            estimatedDurationMinutes = estimatedDurationMinutes,
            scheduledAt = scheduledAt,
            dueAt = dueAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            source = EntitySource.valueOf(source)
        )
    }
}
