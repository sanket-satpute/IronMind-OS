package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository

class FakeEventRepository : EventRepository {
    
    val events = mutableMapOf<String, Event>()
    var shouldFail = false

    override suspend fun saveEvent(event: Event): Result<Event, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        events[event.id] = event
        return Result.Success(event)
    }

    override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(events.values.filter { it.entityId == entityId }.sortedBy { it.occurredAt })
    }

    override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(events.values.filter { it.userId == userId }.sortedBy { it.occurredAt })
    }
}
