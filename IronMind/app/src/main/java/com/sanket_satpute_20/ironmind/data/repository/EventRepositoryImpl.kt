package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomainModel
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EventRepositoryImpl(
    private val dao: IronMindDao,
    private val logger: IronLogger? = null,
    private val database: IronMindDatabase? = null,
    private val learnerEventStatusDao: LearnerEventStatusDao? = null
) : EventRepository {

    override fun isAtomicLearnerStatusSupported(): Boolean = database != null

    override suspend fun saveEventWithLearnerStatus(
        event: Event
    ): Result<Event, Exception> = withContext(Dispatchers.IO) {
        val status = com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity(
            eventId = event.id,
            userId = event.userId,
            status = "PENDING",
            claimCount = 0,
            failureCount = 0,
            maxAttempts = 5,
            leaseToken = null,
            leaseExpiresAt = null,
            lastAttemptAt = null,
            nextAttemptAt = event.occurredAt,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = event.occurredAt,
            updatedAt = event.occurredAt
        )
        if (database != null) {
            try {
                database.withTransaction {
                    val user = dao.getUserProfile(event.userId)
                    if (user == null) {
                        throw IllegalStateException("USER_PROFILE_NOT_FOUND: User ${event.userId} does not exist or has been erased")
                    }
                    dao.insertEvent(event.toEntity())
                    val sDao = learnerEventStatusDao ?: database.learnerEventStatusDao()
                    sDao.insert(status)
                }
                Result.Success(event)
            } catch (e: Exception) {
                logger?.logLifecycle(
                    "Event",
                    "PERSIST_WITH_STATUS_FAILURE",
                    mapOf(
                        "eventType" to event.type.name,
                        "eventId" to event.id,
                        "error" to (e.message ?: "UNKNOWN")
                    )
                )
                Result.Failure(e)
            }
        } else {
            val user = dao.getUserProfile(event.userId)
            if (user == null) {
                return@withContext Result.Failure(IllegalStateException("USER_PROFILE_NOT_FOUND: User ${event.userId} does not exist or has been erased"))
            }
            val saveResult = saveEvent(event)
            if (saveResult is Result.Success && learnerEventStatusDao != null) {
                try {
                    learnerEventStatusDao.insert(status)
                } catch (e: Exception) {
                    return@withContext Result.Failure(e)
                }
            }
            saveResult
        }
    }

    override suspend fun saveEvent(event: Event): Result<Event, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.insertEvent(event.toEntity())
            Result.Success(event)
        } catch (e: Exception) {
            logger?.logLifecycle(
                "Event",
                "PERSIST_FAILURE",
                mapOf(
                    "eventType" to event.type.name,
                    "source" to event.source.name
                )
            )
            Result.Failure(e)
        }
    }

    override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> = withContext(Dispatchers.IO) {
        try {
            val events = dao.getEventsForEntity(entityId).map { it.toDomainModel() }
            Result.Success(events)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> = withContext(Dispatchers.IO) {
        try {
            val events = dao.getEventsForUser(userId).map { it.toDomainModel() }
            Result.Success(events)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEvent(id: String): Result<Event?, Exception> = withContext(Dispatchers.IO) {
        try {
            val event = dao.getEvent(id)?.toDomainModel()
            Result.Success(event)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEventForUser(userId: String, id: String): Result<Event?, Exception> = withContext(Dispatchers.IO) {
        try {
            val event = dao.getEventForUser(userId, id)?.toDomainModel()
            Result.Success(event)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception> = withContext(Dispatchers.IO) {
        try {
            val events = dao.getEventsForDateRange(userId, startTime, endTime).map { it.toDomainModel() }
            Result.Success(events)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEventsForTimeWindow(
        userId: String,
        startTime: Long,
        endTime: Long,
        types: List<com.sanket_satpute_20.ironmind.domain.model.EventType>?,
        limit: Int,
        orderAsc: Boolean
    ): Result<List<Event>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = if (types.isNullOrEmpty()) {
                if (orderAsc) {
                    dao.getEventsForDateRangeAsc(userId, startTime, endTime, limit)
                } else {
                    dao.getEventsForDateRangeDesc(userId, startTime, endTime, limit)
                }
            } else {
                val typeStrings = types.map { it.name }
                if (orderAsc) {
                    dao.getEventsForDateRangeWithTypesAsc(userId, typeStrings, startTime, endTime, limit)
                } else {
                    dao.getEventsForDateRangeWithTypesDesc(userId, typeStrings, startTime, endTime, limit)
                }
            }
            Result.Success(entities.map { it.toDomainModel() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception> = withContext(Dispatchers.IO) {
        try {
            val events = dao.searchEvents(userId, query).map { it.toDomainModel() }
            Result.Success(events)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEventsByCausationId(userId: String, causationId: String): Result<List<Event>, Exception> = withContext(Dispatchers.IO) {
        try {
            val events = dao.getEventsByCausationId(userId, causationId).map { it.toDomainModel() }
            Result.Success(events)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
