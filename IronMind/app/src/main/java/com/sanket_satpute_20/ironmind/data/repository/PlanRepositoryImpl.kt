package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PlanRepositoryImpl(
    private val dao: IronMindDao,
    private val outboxDao: OutboxDao? = null,
    private val database: IronMindDatabase? = null,
    private val idGenerator: IdGenerator = UuidGenerator(),
    private val backgroundExecutor: BackgroundExecutor? = null
) : PlanRepository {

    override suspend fun savePlan(plan: Plan): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                val planEntity = plan.toEntity()
                val targetOutboxDao = outboxDao ?: database?.outboxDao()

                val outboxEntity = targetOutboxDao?.let {
                    require(plan.userId.isNotBlank()) { "Plan outbox entry requires a valid, nonblank userId" }
                    OutboxEntity(
                        operationId = idGenerator.generateId(),
                        userId = plan.userId,
                        entityType = "Plan",
                        entityId = plan.id,
                        operationType = OutboxOperationType.UPSERT.name,
                        payload = plan.toOutboxPayload(),
                        createdAt = plan.createdAt,
                        retryCount = 0,
                        lastAttemptAt = null,
                        status = SyncStatus.PENDING.name
                    )
                }

                if (database != null) {
                    database.withTransaction {
                        dao.insertPlan(planEntity)
                        if (outboxEntity != null) {
                            targetOutboxDao.insertEntry(outboxEntity)
                        }
                    }
                } else {
                    dao.insertPlan(planEntity)
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
                println("IronMindLifecycle [Plan] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPlan(id: String): Result<Plan?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getPlan(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPlansForGoal(goalId: String): Result<List<Plan>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getPlansForGoal(goalId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPlansForUser(userId: String): Result<List<Plan>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getPlansForUser(userId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Plan.toEntity(): PlanEntity {
        return PlanEntity(
            id = id,
            userId = userId,
            goalId = goalId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            status = status.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            source = source.name,
            schemaVersion = 1
        )
    }

    private fun PlanEntity.toDomain(): Plan {
        return Plan(
            id = id,
            userId = userId,
            goalId = goalId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            status = PlanStatus.valueOf(status),
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            source = EntitySource.valueOf(source)
        )
    }

    internal fun Plan.toOutboxPayload(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        if (goalId != null) json.put("goalId", goalId)
        if (ambitionId != null) json.put("ambitionId", ambitionId)
        json.put("title", title)
        json.put("description", description)
        json.put("status", status.name)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        if (startedAt != null) json.put("startedAt", startedAt)
        if (completedAt != null) json.put("completedAt", completedAt)
        json.put("source", source.name)
        return json.toString()
    }
}
