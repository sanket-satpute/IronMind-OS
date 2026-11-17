package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.infrastructure.common.UuidGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ReflectionRepositoryImpl(
    private val dao: IronMindDao,
    private val outboxDao: OutboxDao? = null,
    private val database: IronMindDatabase? = null,
    private val idGenerator: IdGenerator = UuidGenerator(),
    private val backgroundExecutor: BackgroundExecutor? = null
) : ReflectionRepository {

    override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = ReflectionEntity(
                id = reflection.id,
                userId = reflection.userId,
                targetEntityId = reflection.targetEntityId,
                targetEntityType = reflection.targetEntityType,
                content = reflection.content,
                sentiment = reflection.sentiment,
                createdAt = reflection.createdAt,
                schemaVersion = reflection.schemaVersion
            )
            val targetOutboxDao = outboxDao ?: database?.outboxDao()

            val outboxEntity = targetOutboxDao?.let {
                require(reflection.userId.isNotBlank()) { "Reflection outbox entry requires a valid, nonblank userId" }
                OutboxEntity(
                    operationId = idGenerator.generateId(),
                    userId = reflection.userId,
                    entityType = "Reflection",
                    entityId = reflection.id,
                    operationType = OutboxOperationType.UPSERT.name,
                    payload = reflection.toOutboxPayload(),
                    createdAt = reflection.createdAt,
                    retryCount = 0,
                    lastAttemptAt = null,
                    status = SyncStatus.PENDING.name
                )
            }

            if (database != null) {
                database.withTransaction {
                    dao.insertReflection(entity)
                    if (outboxEntity != null) {
                        targetOutboxDao.insertEntry(outboxEntity)
                    }
                }
            } else {
                dao.insertReflection(entity)
                if (outboxEntity != null) {
                    targetOutboxDao.insertEntry(outboxEntity)
                }
            }

            try {
                backgroundExecutor?.scheduleSync()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("IronMindLifecycle [Reflection] [SYNC_SCHEDULE_FAILED] error=${e.message}")
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReflection(id: String): Result<Reflection?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getReflection(id)
            if (entity != null) {
                val domainModel = Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
                Result.Success(domainModel)
            } else {
                Result.Success(null)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReflectionForUser(userId: String, id: String): Result<Reflection?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getReflectionForUser(userId, id)
            if (entity != null) {
                val domainModel = Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
                Result.Success(domainModel)
            } else {
                Result.Success(null)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReflectionsForDateRange(
        userId: String,
        startTime: Long,
        endTime: Long
    ): Result<List<Reflection>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getReflectionsForDateRange(userId, startTime, endTime)
            val domainModels = entities.map { entity ->
                Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
            }
            Result.Success(domainModels)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReflectionsForTimeWindow(
        userId: String,
        startTime: Long,
        endTime: Long,
        limit: Int,
        orderAsc: Boolean
    ): Result<List<Reflection>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = if (orderAsc) {
                dao.getReflectionsForDateRangeAsc(userId, startTime, endTime, limit)
            } else {
                dao.getReflectionsForDateRangeDesc(userId, startTime, endTime, limit)
            }
            val domainModels = entities.map { entity ->
                Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
            }
            Result.Success(domainModels)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun searchReflections(
        userId: String,
        query: String
    ): Result<List<Reflection>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.searchReflections(userId, query)
            val domainModels = entities.map { entity ->
                Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
            }
            Result.Success(domainModels)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    internal fun Reflection.toOutboxPayload(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("userId", userId)
        if (targetEntityId != null) json.put("targetEntityId", targetEntityId)
        if (targetEntityType != null) json.put("targetEntityType", targetEntityType)
        json.put("content", content)
        if (sentiment != null) json.put("sentiment", sentiment)
        json.put("createdAt", createdAt)
        json.put("schemaVersion", schemaVersion)
        return json.toString()
    }
}
