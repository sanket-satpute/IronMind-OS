package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TaskRepositoryImpl(
    private val dao: IronMindDao,
    private val outboxDao: OutboxDao? = null,
    private val database: IronMindDatabase? = null,
    private val idGenerator: IdGenerator = UuidGenerator(),
    private val backgroundExecutor: BackgroundExecutor? = null
) : TaskRepository {

    override suspend fun saveTask(task: Task): Result<Unit, Exception> {
        return persistTaskInternal(task = task, event = null)
    }

    override suspend fun saveTaskWithEvent(
        task: Task,
        previousTask: Task,
        event: Event,
        eventRepository: EventRepository
    ): Result<Unit, Exception> {
        if (database != null && eventRepository is EventRepositoryImpl) {
            return persistTaskInternal(task = task, event = event)
        }
        return super.saveTaskWithEvent(task, previousTask, event, eventRepository)
    }

    private suspend fun persistTaskInternal(
        task: Task,
        event: Event?
    ): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                val taskEntity = task.toEntity()
                val targetOutboxDao = outboxDao ?: database?.outboxDao()

                val outboxEntity = targetOutboxDao?.let {
                    require(task.userId.isNotBlank()) { "Task outbox entry requires a valid, nonblank userId" }
                    OutboxEntity(
                        operationId = idGenerator.generateId(),
                        userId = task.userId,
                        entityType = "Task",
                        entityId = task.id,
                        operationType = OutboxOperationType.UPSERT.name,
                        payload = task.toOutboxPayload(),
                        createdAt = task.createdAt,
                        retryCount = 0,
                        lastAttemptAt = null,
                        status = SyncStatus.PENDING.name
                    )
                }

                if (database != null) {
                    database.withTransaction {
                        dao.insertTask(taskEntity)
                        if (outboxEntity != null) {
                            targetOutboxDao.insertEntry(outboxEntity)
                        }
                        if (event != null) {
                            dao.insertEvent(event.toEntity())
                        }
                    }
                } else {
                    dao.insertTask(taskEntity)
                    if (outboxEntity != null) {
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                    if (event != null) {
                        dao.insertEvent(event.toEntity())
                    }
                }
            }

            try {
                backgroundExecutor?.scheduleSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Task] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getTasksForPlan(planId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getTasksForGoal(goalId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getTasksForUser(userId: String): Result<List<Task>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getTasksForUser(userId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                require(userId.isNotBlank()) { "Task deletion requires a valid, nonblank userId" }
                require(taskId.isNotBlank()) { "Task deletion requires a valid, nonblank taskId" }

                val existingEntity = dao.getTask(taskId)
                    ?: return@withContext Result.Failure(IllegalArgumentException("Task not found: $taskId"))

                if (existingEntity.userId != userId) {
                    return@withContext Result.Failure(IllegalStateException("Task $taskId does not belong to user $userId"))
                }

                val targetOutboxDao = outboxDao ?: database?.outboxDao()
                val now = System.currentTimeMillis()

                val tombstonePayload = JSONObject().apply {
                    put("id", taskId)
                    put("userId", userId)
                    put("isDeleted", true)
                    put("deletedAt", now)
                    put("updatedAt", now)
                }.toString()

                val outboxEntity = targetOutboxDao?.let {
                    OutboxEntity(
                        operationId = idGenerator.generateId(),
                        userId = userId,
                        entityType = "Task",
                        entityId = taskId,
                        operationType = OutboxOperationType.DELETE.name,
                        payload = tombstonePayload,
                        createdAt = now,
                        retryCount = 0,
                        lastAttemptAt = null,
                        status = SyncStatus.PENDING.name
                    )
                }

                if (database != null) {
                    database.withTransaction {
                        dao.deleteTask(userId, taskId)
                        if (outboxEntity != null) {
                            targetOutboxDao.insertEntry(outboxEntity)
                        }
                    }
                } else {
                    dao.deleteTask(userId, taskId)
                    if (outboxEntity != null) {
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                }

                try {
                    backgroundExecutor?.scheduleSync()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("IronMindLifecycle [Task] [SYNC_SCHEDULE_FAILED] error=${e.message}")
                }

                Result.Success(Unit)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

    internal fun Task.toOutboxPayload(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        if (goalId != null) json.put("goalId", goalId)
        if (planId != null) json.put("planId", planId)
        if (parentTaskId != null) json.put("parentTaskId", parentTaskId)
        json.put("title", title)
        json.put("description", description)
        json.put("status", status.name)
        json.put("priority", priority)
        if (estimatedDurationMinutes != null) json.put("estimatedDurationMinutes", estimatedDurationMinutes)
        json.put("scheduledAt", scheduledAt ?: JSONObject.NULL)
        json.put("dueAt", dueAt ?: JSONObject.NULL)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        json.put("completedAt", completedAt ?: JSONObject.NULL)
        json.put("postponedAt", postponedAt ?: JSONObject.NULL)
        json.put("source", source.name)
        return json.toString()
    }
}
