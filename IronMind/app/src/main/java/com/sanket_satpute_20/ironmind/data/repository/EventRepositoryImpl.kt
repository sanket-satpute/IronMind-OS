package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
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
    private val logger: IronLogger? = null
) : EventRepository {

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
}
