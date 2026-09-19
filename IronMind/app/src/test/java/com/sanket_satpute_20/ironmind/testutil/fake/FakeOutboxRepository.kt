package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository

class FakeOutboxRepository : OutboxRepository {
    private val entries = mutableMapOf<String, OutboxEntry>()

    override suspend fun insertEntry(entry: OutboxEntry): Result<Unit, Exception> {
        // IGNORE if already exists (idempotent)
        if (!entries.containsKey(entry.operationId)) {
            entries[entry.operationId] = entry
        }
        return Result.Success(Unit)
    }

    override suspend fun getPendingEntries(): Result<List<OutboxEntry>, Exception> {
        val pending = entries.values.filter {
            it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED
        }.sortedBy { it.createdAt }
        return Result.Success(pending)
    }

    override suspend fun updateStatus(
        operationId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Unit, Exception> {
        val existing = entries[operationId] ?: return Result.Failure(Exception("Entry not found: $operationId"))
        entries[operationId] = existing.copy(status = status, retryCount = retryCount, lastAttemptAt = lastAttemptAt)
        return Result.Success(Unit)
    }

    override suspend fun deleteEntry(operationId: String): Result<Unit, Exception> {
        entries.remove(operationId)
        return Result.Success(Unit)
    }

    override suspend fun countPendingEntries(): Result<Int, Exception> {
        val count = entries.values.count {
            it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED
        }
        return Result.Success(count)
    }

    override suspend fun entryExists(operationId: String): Result<Boolean, Exception> {
        return Result.Success(entries.containsKey(operationId))
    }

    // Test helpers
    fun getEntry(operationId: String): OutboxEntry? = entries[operationId]
    fun allEntries(): List<OutboxEntry> = entries.values.toList()
}
