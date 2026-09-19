package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus

interface OutboxRepository {

    suspend fun insertEntry(entry: OutboxEntry): Result<Unit, Exception>

    suspend fun getPendingEntries(): Result<List<OutboxEntry>, Exception>

    suspend fun updateStatus(
        operationId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Unit, Exception>

    suspend fun deleteEntry(operationId: String): Result<Unit, Exception>

    suspend fun countPendingEntries(): Result<Int, Exception>

    suspend fun entryExists(operationId: String): Result<Boolean, Exception>
}
