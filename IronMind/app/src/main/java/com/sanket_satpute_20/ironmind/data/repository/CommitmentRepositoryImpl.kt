package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CommitmentRepositoryImpl(
    private val dao: IronMindDao,
    private val outboxDao: OutboxDao? = null,
    private val database: IronMindDatabase? = null,
    private val idGenerator: IdGenerator = UuidGenerator(),
    private val backgroundExecutor: BackgroundExecutor? = null
) : CommitmentRepository {

    override suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                val commitmentEntity = commitment.toEntity()
                val targetOutboxDao = outboxDao ?: database?.outboxDao()

                val outboxEntity = targetOutboxDao?.let {
                    require(commitment.userId.isNotBlank()) { "Commitment outbox entry requires a valid, nonblank userId" }
                    OutboxEntity(
                        operationId = idGenerator.generateId(),
                        userId = commitment.userId,
                        entityType = "Commitment",
                        entityId = commitment.id,
                        operationType = OutboxOperationType.UPSERT.name,
                        payload = commitment.toOutboxPayload(),
                        createdAt = commitment.createdAt,
                        retryCount = 0,
                        lastAttemptAt = null,
                        status = SyncStatus.PENDING.name
                    )
                }

                if (database != null) {
                    database.withTransaction {
                        dao.insertCommitment(commitmentEntity)
                        if (outboxEntity != null) {
                            targetOutboxDao.insertEntry(outboxEntity)
                        }
                    }
                } else {
                    dao.insertCommitment(commitmentEntity)
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
                println("IronMindLifecycle [Commitment] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitment(id: String): Result<Commitment?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getCommitment(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForUser(userId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveCommitmentsForUser(
        userId: String,
        statuses: List<CommitmentStatus>
    ): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getActiveCommitmentsForUser(userId, statuses.map { it.name })
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveCommitmentsForUser(
        userId: String,
        statuses: List<CommitmentStatus>,
        limit: Int
    ): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getActiveCommitmentsForUser(userId, statuses.map { it.name }, limit)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForDateRange(userId, startTime, endTime)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForGoal(goalId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForPlan(planId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForTask(taskId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun searchCommitments(userId: String, query: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.searchCommitments(userId, query)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Commitment.toEntity(): CommitmentEntity {
        return CommitmentEntity(
            id = id,
            userId = userId,
            goalId = goalId,
            planId = planId,
            taskId = taskId,
            title = title,
            description = description,
            committedAt = committedAt,
            scheduledStartAt = scheduledStartAt,
            scheduledEndAt = scheduledEndAt,
            status = status.name,
            priority = priority,
            source = source.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            missedAt = missedAt,
            recoveredAt = recoveredAt,
            parentCommitmentId = parentCommitmentId,
            schemaVersion = 1
        )
    }

    private fun CommitmentEntity.toDomain(): Commitment {
        return Commitment(
            id = id,
            userId = userId,
            goalId = goalId,
            planId = planId,
            taskId = taskId,
            parentCommitmentId = parentCommitmentId,
            title = title,
            description = description,
            committedAt = committedAt,
            scheduledStartAt = scheduledStartAt,
            scheduledEndAt = scheduledEndAt,
            status = CommitmentStatus.valueOf(status),
            priority = priority,
            source = EntitySource.valueOf(source),
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            missedAt = missedAt,
            recoveredAt = recoveredAt
        )
    }

    internal fun Commitment.toOutboxPayload(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        if (goalId != null) json.put("goalId", goalId)
        if (planId != null) json.put("planId", planId)
        if (taskId != null) json.put("taskId", taskId)
        if (parentCommitmentId != null) json.put("parentCommitmentId", parentCommitmentId)
        json.put("title", title)
        json.put("description", description)
        json.put("committedAt", committedAt)
        if (scheduledStartAt != null) json.put("scheduledStartAt", scheduledStartAt)
        if (scheduledEndAt != null) json.put("scheduledEndAt", scheduledEndAt)
        json.put("status", status.name)
        json.put("priority", priority)
        json.put("source", source.name)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        if (startedAt != null) json.put("startedAt", startedAt)
        if (completedAt != null) json.put("completedAt", completedAt)
        if (postponedAt != null) json.put("postponedAt", postponedAt)
        if (missedAt != null) json.put("missedAt", missedAt)
        if (recoveredAt != null) json.put("recoveredAt", recoveredAt)
        json.put("schemaVersion", 1)
        return json.toString()
    }
}
