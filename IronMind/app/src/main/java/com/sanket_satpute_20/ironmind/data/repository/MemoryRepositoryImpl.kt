package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryRepositoryImpl(
    private val dao: IronMindDao
) : MemoryRepository {

    override suspend fun saveMemory(memory: Memory): Result<Memory, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.insertMemory(MemoryEntity.fromDomain(memory))
            Result.Success(memory)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getMemoryById(id: String): Result<Memory?, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getMemoryById(id)?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getMemoriesForUser(userId: String): Result<List<Memory>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getMemoriesForUser(userId).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveMemoriesForUser(userId: String): Result<List<Memory>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getActiveMemoriesForUser(userId).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
