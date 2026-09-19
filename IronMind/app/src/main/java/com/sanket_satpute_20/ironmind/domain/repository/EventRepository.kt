package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event

interface EventRepository {
    suspend fun saveEvent(event: Event): Result<Event, Exception>
    suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception>
    suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception>
    suspend fun getEvent(id: String): Result<Event?, Exception>
    suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception>
    suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception>
}
