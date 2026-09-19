package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory

interface MemoryRepository {
    suspend fun saveMemory(memory: Memory): Result<Memory, Exception>
    suspend fun getMemoryById(id: String): Result<Memory?, Exception>
    suspend fun getMemoriesForUser(userId: String): Result<List<Memory>, Exception>
    suspend fun getActiveMemoriesForUser(userId: String): Result<List<Memory>, Exception>
}
