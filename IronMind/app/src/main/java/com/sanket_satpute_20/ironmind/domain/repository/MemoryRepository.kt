package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory

interface MemoryRepository {
    suspend fun saveMemory(memory: Memory): Result<Memory, Exception>
    suspend fun saveMemoryWithEvent(memory: Memory, event: com.sanket_satpute_20.ironmind.domain.model.Event? = null): Result<Memory, Exception> = saveMemory(memory)
    suspend fun getMemoryById(id: String): Result<Memory?, Exception>
    suspend fun getMemoriesForUser(userId: String): Result<List<Memory>, Exception>
    fun observeMemoriesForUser(userId: String): kotlinx.coroutines.flow.Flow<List<Memory>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun getActiveMemoriesForUser(userId: String): Result<List<Memory>, Exception>
    suspend fun getMemoriesForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Memory>, Exception>
    suspend fun searchMemories(userId: String, query: String): Result<List<Memory>, Exception>
    suspend fun deleteMemory(userId: String, id: String): Result<Unit, Exception> = Result.Success(Unit)
    suspend fun deleteMemoryWithEvent(userId: String, id: String, event: com.sanket_satpute_20.ironmind.domain.model.Event? = null): Result<Unit, Exception> = deleteMemory(userId, id)
    suspend fun updateMemoryState(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
        status: com.sanket_satpute_20.ironmind.domain.model.MemoryStatus,
        updatedAt: Long
    ): Result<Unit, Exception> = Result.Success(Unit)
    suspend fun updateMemoryStateWithEvent(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
        status: com.sanket_satpute_20.ironmind.domain.model.MemoryStatus,
        updatedAt: Long,
        event: com.sanket_satpute_20.ironmind.domain.model.Event? = null
    ): Result<Unit, Exception> = updateMemoryState(id, userId, confirmationState, status, updatedAt)
}
