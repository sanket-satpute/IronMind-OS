package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

class FakeMemoryRepository(
    private val eventRepository: com.sanket_satpute_20.ironmind.domain.repository.EventRepository? = null
) : MemoryRepository {
    private val memories = mutableMapOf<String, Memory>()
    var shouldFailEventPersistence: Boolean = false

    override suspend fun saveMemory(memory: Memory): Result<Memory, Exception> = saveMemoryWithEvent(memory, null)

    override suspend fun saveMemoryWithEvent(memory: Memory, event: com.sanket_satpute_20.ironmind.domain.model.Event?): Result<Memory, Exception> {
        if (shouldFailEventPersistence) {
            return Result.Failure(IllegalStateException("Simulated event persistence failure"))
        }
        memories[memory.id] = memory
        if (event != null) {
            val res = eventRepository?.saveEvent(event)
            if (res is Result.Failure) return Result.Failure(res.error)
        }
        return Result.Success(memory)
    }

    override suspend fun getMemoryById(id: String): Result<Memory?, Exception> {
        return Result.Success(memories[id])
    }

    override suspend fun getMemoriesForUser(userId: String): Result<List<Memory>, Exception> {
        return Result.Success(memories.values.filter { it.userId == userId })
    }

    override suspend fun getActiveMemoriesForUser(userId: String): Result<List<Memory>, Exception> {
        val active = memories.values.filter { memory ->
            memory.userId == userId &&
            memory.status != com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.EXPIRED &&
            memory.status != com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.DELETED &&
            memory.status != com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.INACTIVE
        }
        return Result.Success(active)
    }

    override suspend fun getMemoriesForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Memory>, Exception> {
        return Result.Success(memories.values.filter { it.userId == userId && it.createdAt in startTime..endTime })
    }

    override suspend fun searchMemories(userId: String, query: String): Result<List<Memory>, Exception> {
        return Result.Success(memories.values.filter { it.userId == userId && it.content.contains(query, ignoreCase = true) })
    }

    override fun observeMemoriesForUser(userId: String): kotlinx.coroutines.flow.Flow<List<Memory>> {
        return kotlinx.coroutines.flow.flowOf(memories.values.filter { it.userId == userId && it.status != com.sanket_satpute_20.ironmind.domain.model.MemoryStatus.DELETED })
    }

    override suspend fun deleteMemory(userId: String, id: String): Result<Unit, Exception> = deleteMemoryWithEvent(userId, id, null)

    override suspend fun deleteMemoryWithEvent(
        userId: String,
        id: String,
        event: com.sanket_satpute_20.ironmind.domain.model.Event?
    ): Result<Unit, Exception> {
        val existing = memories[id] ?: return Result.Failure(IllegalArgumentException("Memory not found: $id"))
        if (existing.userId != userId) return Result.Failure(IllegalStateException("Memory $id does not belong to user $userId"))
        if (shouldFailEventPersistence) {
            return Result.Failure(IllegalStateException("Simulated event persistence failure"))
        }
        memories.remove(id)
        if (event != null) {
            val res = eventRepository?.saveEvent(event)
            if (res is Result.Failure) return Result.Failure(res.error)
        }
        return Result.Success(Unit)
    }

    override suspend fun updateMemoryState(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
        status: com.sanket_satpute_20.ironmind.domain.model.MemoryStatus,
        updatedAt: Long
    ): Result<Unit, Exception> = updateMemoryStateWithEvent(id, userId, confirmationState, status, updatedAt, null)

    override suspend fun updateMemoryStateWithEvent(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
        status: com.sanket_satpute_20.ironmind.domain.model.MemoryStatus,
        updatedAt: Long,
        event: com.sanket_satpute_20.ironmind.domain.model.Event?
    ): Result<Unit, Exception> {
        val existing = memories[id] ?: return Result.Failure(IllegalArgumentException("Memory not found: $id"))
        if (existing.userId != userId) return Result.Failure(IllegalStateException("Memory $id does not belong to user $userId"))
        if (existing.confirmationState == confirmationState && existing.status == status) {
            return Result.Success(Unit)
        }
        if (shouldFailEventPersistence) {
            return Result.Failure(IllegalStateException("Simulated event persistence failure"))
        }
        memories[id] = existing.copy(
            confirmationState = confirmationState,
            status = status,
            updatedAt = updatedAt
        )
        if (event != null) {
            val res = eventRepository?.saveEvent(event)
            if (res is Result.Failure) return Result.Failure(res.error)
        }
        return Result.Success(Unit)
    }
}
