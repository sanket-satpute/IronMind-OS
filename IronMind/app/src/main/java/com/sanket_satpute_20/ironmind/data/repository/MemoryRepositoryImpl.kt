package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MemoryRepositoryImpl(
    private val dao: IronMindDao,
    private val database: IronMindDatabase? = null,
    private val outboxDao: OutboxDao? = null,
    private val idGenerator: IdGenerator = UuidGenerator(),
    private val backgroundExecutor: BackgroundExecutor? = null
) : MemoryRepository {

    override suspend fun saveMemory(memory: Memory): Result<Memory, Exception> = saveMemoryWithEvent(memory, null)

    override suspend fun saveMemoryWithEvent(memory: Memory, event: Event?): Result<Memory, Exception> = withContext(Dispatchers.IO) {
        try {
            val targetOutboxDao = outboxDao ?: database?.outboxDao()

            val outboxEntity = targetOutboxDao?.let {
                require(memory.userId.isNotBlank()) { "Memory outbox entry requires a valid, nonblank userId" }
                OutboxEntity(
                    operationId = idGenerator.generateId(),
                    userId = memory.userId,
                    entityType = "Memory",
                    entityId = memory.id,
                    operationType = OutboxOperationType.UPSERT.name,
                    payload = memory.toOutboxPayload(),
                    createdAt = memory.createdAt,
                    retryCount = 0,
                    lastAttemptAt = null,
                    status = SyncStatus.PENDING.name
                )
            }

            if (database != null) {
                database.withTransaction {
                    dao.insertMemory(MemoryEntity.fromDomain(memory))
                    if (event != null) {
                        dao.insertEvent(event.toEntity())
                    }
                    if (outboxEntity != null) {
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                }
            } else {
                dao.insertMemory(MemoryEntity.fromDomain(memory))
                if (event != null) {
                    dao.insertEvent(event.toEntity())
                }
                if (outboxEntity != null) {
                    targetOutboxDao.insertEntry(outboxEntity)
                }
            }

            try {
                backgroundExecutor?.scheduleSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Memory] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(memory)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getMemoryById(id: String): Result<Memory?, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getMemoryById(id)?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getMemoriesForUser(userId: String): Result<List<Memory>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getMemoriesForUser(userId).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun observeMemoriesForUser(userId: String): Flow<List<Memory>> {
        return dao.observeMemoriesForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteMemory(userId: String, id: String): Result<Unit, Exception> = deleteMemoryWithEvent(userId, id, null)

    override suspend fun deleteMemoryWithEvent(userId: String, id: String, event: Event?): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            require(userId.isNotBlank()) { "Memory outbox entry requires a valid, nonblank userId" }
            val targetOutboxDao = outboxDao ?: database?.outboxDao()
            val now = System.currentTimeMillis()

            val outboxEntity = targetOutboxDao?.let {
                val tombstonePayload = JSONObject().apply {
                    put("id", id)
                    put("userId", userId)
                    put("isDeleted", true)
                    put("deletedAt", now)
                }.toString()
                OutboxEntity(
                    operationId = idGenerator.generateId(),
                    userId = userId,
                    entityType = "Memory",
                    entityId = id,
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
                    val existing = dao.getMemoryById(id)
                        ?: throw IllegalArgumentException("Memory not found: $id")
                    if (existing.userId != userId) {
                        throw IllegalStateException("Memory $id does not belong to user $userId")
                    }
                    dao.deleteMemory(userId, id)
                    if (event != null) {
                        dao.insertEvent(event.toEntity())
                    }
                    if (outboxEntity != null) {
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                }
            } else {
                val existing = dao.getMemoryById(id)
                    ?: return@withContext Result.Failure(IllegalArgumentException("Memory not found: $id"))
                if (existing.userId != userId) {
                    return@withContext Result.Failure(IllegalStateException("Memory $id does not belong to user $userId"))
                }
                dao.deleteMemory(userId, id)
                if (event != null) {
                    dao.insertEvent(event.toEntity())
                }
                if (outboxEntity != null) {
                    targetOutboxDao.insertEntry(outboxEntity)
                }
            }

            try {
                backgroundExecutor?.scheduleSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Memory] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateMemoryState(
        id: String,
        userId: String,
        confirmationState: MemoryConfirmationState,
        status: MemoryStatus,
        updatedAt: Long
    ): Result<Unit, Exception> = updateMemoryStateWithEvent(id, userId, confirmationState, status, updatedAt, null)

    override suspend fun updateMemoryStateWithEvent(
        id: String,
        userId: String,
        confirmationState: MemoryConfirmationState,
        status: MemoryStatus,
        updatedAt: Long,
        event: Event?
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            require(userId.isNotBlank()) { "Memory outbox entry requires a valid, nonblank userId" }
            val targetOutboxDao = outboxDao ?: database?.outboxDao()

            if (database != null) {
                database.withTransaction {
                    val existing = dao.getMemoryById(id)
                        ?: throw IllegalArgumentException("Memory not found: $id")
                    if (existing.userId != userId) {
                        throw IllegalStateException("Memory $id does not belong to user $userId")
                    }
                    if (existing.confirmationState == confirmationState.name && existing.status == status.name) {
                        return@withTransaction
                    }
                    dao.updateMemoryState(id, userId, confirmationState.name, status.name, updatedAt)
                    if (event != null) {
                        dao.insertEvent(event.toEntity())
                    }
                    if (targetOutboxDao != null) {
                        val updatedMemory = existing.copy(
                            confirmationState = confirmationState.name,
                            status = status.name,
                            updatedAt = updatedAt
                        ).toDomain()
                        val outboxEntity = OutboxEntity(
                            operationId = idGenerator.generateId(),
                            userId = userId,
                            entityType = "Memory",
                            entityId = id,
                            operationType = OutboxOperationType.UPSERT.name,
                            payload = updatedMemory.toOutboxPayload(),
                            createdAt = updatedAt,
                            retryCount = 0,
                            lastAttemptAt = null,
                            status = SyncStatus.PENDING.name
                        )
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                }
            } else {
                val existing = dao.getMemoryById(id)
                    ?: return@withContext Result.Failure(IllegalArgumentException("Memory not found: $id"))
                if (existing.userId != userId) {
                    return@withContext Result.Failure(IllegalStateException("Memory $id does not belong to user $userId"))
                }
                if (existing.confirmationState == confirmationState.name && existing.status == status.name) {
                    return@withContext Result.Success(Unit)
                }
                dao.updateMemoryState(id, userId, confirmationState.name, status.name, updatedAt)
                if (event != null) {
                    dao.insertEvent(event.toEntity())
                }
                if (targetOutboxDao != null) {
                    val updatedMemory = existing.copy(
                        confirmationState = confirmationState.name,
                        status = status.name,
                        updatedAt = updatedAt
                    ).toDomain()
                    val outboxEntity = OutboxEntity(
                        operationId = idGenerator.generateId(),
                        userId = userId,
                        entityType = "Memory",
                        entityId = id,
                        operationType = OutboxOperationType.UPSERT.name,
                        payload = updatedMemory.toOutboxPayload(),
                        createdAt = updatedAt,
                        retryCount = 0,
                        lastAttemptAt = null,
                        status = SyncStatus.PENDING.name
                    )
                    targetOutboxDao.insertEntry(outboxEntity)
                }
            }

            try {
                backgroundExecutor?.scheduleSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Memory] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveMemoriesForUser(userId: String): Result<List<Memory>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getActiveMemoriesForUser(userId).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getMemoriesForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Memory>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getMemoriesForDateRange(userId, startTime, endTime).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun searchMemories(userId: String, query: String): Result<List<Memory>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.searchMemories(userId, query).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    internal fun Memory.toOutboxPayload(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        json.put("type", type)
        json.put("content", content)
        json.put("source", source.name)
        json.put("confidence", confidence.toDouble())
        json.put("evidenceCount", evidenceCount)
        json.put("firstObservedAt", firstObservedAt)
        json.put("lastObservedAt", lastObservedAt)
        json.put("confirmationState", confirmationState.name)
        json.put("status", status.name)
        if (expiresAt != null) json.put("expiresAt", expiresAt)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        json.put("schemaVersion", 1)
        return json.toString()
    }
}
