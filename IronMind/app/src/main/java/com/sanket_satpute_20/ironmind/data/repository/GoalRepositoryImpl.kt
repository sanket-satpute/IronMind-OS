package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GoalRepositoryImpl(
    private val dao: IronMindDao,
    private val outboxDao: OutboxDao? = null,
    private val database: IronMindDatabase? = null,
    private val idGenerator: IdGenerator = UuidGenerator(),
    private val backgroundExecutor: BackgroundExecutor? = null
) : GoalRepository {

    override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                val goalEntity = goal.toEntity()
                val targetOutboxDao = outboxDao ?: database?.outboxDao()

                val outboxEntity = targetOutboxDao?.let {
                    require(goal.userId.isNotBlank()) { "Goal outbox entry requires a valid, nonblank userId" }
                    OutboxEntity(
                        operationId = idGenerator.generateId(),
                        userId = goal.userId,
                        entityType = "Goal",
                        entityId = goal.id,
                        operationType = OutboxOperationType.UPSERT.name,
                        payload = goal.toOutboxPayload(),
                        createdAt = goal.createdAt,
                        retryCount = 0,
                        lastAttemptAt = null,
                        status = SyncStatus.PENDING.name
                    )
                }

                if (database != null) {
                    database.withTransaction {
                        dao.insertGoal(goalEntity)
                        if (outboxEntity != null) {
                            targetOutboxDao.insertEntry(outboxEntity)
                        }
                    }
                } else {
                    dao.insertGoal(goalEntity)
                    if (outboxEntity != null) {
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                }
            }

            try {
                backgroundExecutor?.scheduleSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Goal] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getGoal(id: String): Result<Goal?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getGoal(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getGoalsForUser(userId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveGoalsForUser(userId: String, limit: Int): Result<List<Goal>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getActiveGoalsForUser(userId, limit)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.searchGoals(userId, query)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Goal.toEntity(): GoalEntity {
        return GoalEntity(
            id = id,
            userId = userId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            why = why,
            importance = importance,
            status = status.name,
            targetAt = targetAt,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            schemaVersion = 1
        )
    }

    private fun GoalEntity.toDomain(): Goal {
        return Goal(
            id = id,
            userId = userId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            why = why,
            importance = importance,
            status = GoalStatus.valueOf(status),
            targetAt = targetAt,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    internal fun Goal.toOutboxPayload(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        if (ambitionId != null) json.put("ambitionId", ambitionId)
        json.put("title", title)
        json.put("description", description)
        json.put("why", why)
        json.put("importance", importance)
        json.put("status", status.name)
        if (targetAt != null) json.put("targetAt", targetAt)
        if (startedAt != null) json.put("startedAt", startedAt)
        if (completedAt != null) json.put("completedAt", completedAt)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        return json.toString()
    }
}
