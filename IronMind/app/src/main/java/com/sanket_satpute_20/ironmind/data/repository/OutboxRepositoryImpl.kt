package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository

class OutboxRepositoryImpl(
    private val dao: OutboxDao
) : OutboxRepository {

    override suspend fun insertEntry(entry: OutboxEntry): Result<Unit, Exception> {
        return try {
            dao.insertEntry(entry.toEntity())
            println("IronMindLifecycle [Sync] [OUTBOX_ENTRY_ADDED] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPendingEntries(): Result<List<OutboxEntry>, Exception> {
        return try {
            val entries = dao.getPendingEntries().map { it.toDomain() }
            Result.Success(entries)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateStatus(
        operationId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Unit, Exception> {
        return try {
            dao.updateStatus(operationId, status.name, retryCount, lastAttemptAt)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deleteEntry(operationId: String): Result<Unit, Exception> {
        return try {
            dao.deleteEntry(operationId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun countPendingEntries(): Result<Int, Exception> {
        return try {
            Result.Success(dao.countPendingEntries())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun entryExists(operationId: String): Result<Boolean, Exception> {
        return try {
            Result.Success(dao.entryExists(operationId))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
