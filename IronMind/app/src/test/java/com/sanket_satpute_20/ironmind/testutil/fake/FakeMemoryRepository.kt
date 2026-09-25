package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

class FakeMemoryRepository : MemoryRepository {
    private val memories = mutableMapOf<String, Memory>()

    override suspend fun saveMemory(memory: Memory): Result<Memory, Exception> {
        memories[memory.id] = memory
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
}
