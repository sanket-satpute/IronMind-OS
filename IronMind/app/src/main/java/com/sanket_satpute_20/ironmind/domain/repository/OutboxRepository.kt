package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus

interface OutboxRepository {

    suspend fun insertEntry(entry: OutboxEntry): Result<Unit, Exception>

    @Deprecated("Legacy unscoped query. Use getPendingEntriesForUser in production code.")
    suspend fun getPendingEntries(): Result<List<OutboxEntry>, Exception>

    suspend fun getPendingEntriesForUser(userId: String): Result<List<OutboxEntry>, Exception>

    suspend fun getEntryByOperationIdForUser(
        operationId: String,
        userId: String
    ): Result<OutboxEntry?, Exception>

    @Deprecated("Legacy unscoped status update. Use updateStatusForUser in production code.")
    suspend fun updateStatus(
        operationId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Unit, Exception>

    suspend fun updateStatusForUser(
        operationId: String,
        userId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception>

    @Deprecated("Legacy unscoped delete. Use deleteEntryForUser in production code.")
    suspend fun deleteEntry(operationId: String): Result<Unit, Exception>

    suspend fun deleteEntryForUser(
        operationId: String,
        userId: String
    ): Result<Int, Exception>

    @Deprecated("Legacy unscoped count. Use countPendingEntriesForUser in production code.")
    suspend fun countPendingEntries(): Result<Int, Exception>

    suspend fun countPendingEntriesForUser(userId: String): Result<Int, Exception>

    suspend fun claimLegacyEntriesForUser(userId: String, batchLimit: Int = 50): Result<Int, Exception>

    suspend fun claimEntryForSync(operationId: String, userId: String, now: Long): Result<Int, Exception>

    suspend fun claimEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        leaseExpiresAt: Long,
        now: Long
    ): Result<Int, Exception>

    suspend fun completeEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String
    ): Result<Int, Exception>

    suspend fun failEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception>

    suspend fun conflictEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception>

    suspend fun recoverExpiredLeasesForUser(userId: String, now: Long): Result<Int, Exception>

    suspend fun updateBaseVersionAndPayload(
        operationId: String,
        userId: String,
        newBaseVersion: Long,
        newPayload: String,
        now: Long
    ): Result<Int, Exception>

    suspend fun recoverStaleSyncingEntriesForUser(userId: String, staleThreshold: Long): Result<Int, Exception>

    suspend fun rollbackLegacyEntryClaim(operationId: String, userId: String): Result<Int, Exception>

    suspend fun entryExists(operationId: String): Result<Boolean, Exception>
}
