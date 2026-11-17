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

    override suspend fun getPendingEntriesForUser(userId: String): Result<List<OutboxEntry>, Exception> {
        val pending = entries.values.filter {
            it.userId == userId && (it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED)
        }.sortedBy { it.createdAt }
        return Result.Success(pending)
    }

    override suspend fun getEntryByOperationIdForUser(
        operationId: String,
        userId: String
    ): Result<OutboxEntry?, Exception> {
        val entry = entries[operationId]?.takeIf { it.userId == userId }
        return Result.Success(entry)
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

    override suspend fun updateStatusForUser(
        operationId: String,
        userId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception> {
        val existing = entries[operationId]
        if (existing == null || existing.userId != userId) {
            return Result.Success(0)
        }
        entries[operationId] = existing.copy(status = status, retryCount = retryCount, lastAttemptAt = lastAttemptAt)
        return Result.Success(1)
    }

    override suspend fun deleteEntry(operationId: String): Result<Unit, Exception> {
        entries.remove(operationId)
        return Result.Success(Unit)
    }

    override suspend fun deleteEntryForUser(
        operationId: String,
        userId: String
    ): Result<Int, Exception> {
        val existing = entries[operationId]
        if (existing == null || existing.userId != userId) {
            return Result.Success(0)
        }
        entries.remove(operationId)
        return Result.Success(1)
    }

    override suspend fun countPendingEntries(): Result<Int, Exception> {
        val count = entries.values.count {
            it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED
        }
        return Result.Success(count)
    }

    override suspend fun countPendingEntriesForUser(userId: String): Result<Int, Exception> {
        val count = entries.values.count {
            it.userId == userId && (it.status == SyncStatus.PENDING || it.status == SyncStatus.FAILED)
        }
        return Result.Success(count)
    }

    override suspend fun claimLegacyEntriesForUser(userId: String, batchLimit: Int): Result<Int, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }
        var claimed = 0
        val candidates = entries.values.filter { it.userId == null }.sortedBy { it.createdAt }.take(batchLimit)
        for (candidate in candidates) {
            val rootUserId = com.sanket_satpute_20.ironmind.data.local.entity.extractRootPayloadUserId(candidate.payload) ?: continue
            if (rootUserId == userId) {
                entries[candidate.operationId] = candidate.copy(userId = userId)
                claimed++
            }
        }
        return Result.Success(claimed)
    }

    override suspend fun claimEntryForSync(operationId: String, userId: String, now: Long): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId && (entry.status == SyncStatus.PENDING || entry.status == SyncStatus.FAILED)) {
            entries[operationId] = entry.copy(status = SyncStatus.SYNCING, lastAttemptAt = now)
            return Result.Success(1)
        }
        return Result.Success(0)
    }

    override suspend fun claimEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        leaseExpiresAt: Long,
        now: Long
    ): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId) {
            val isEligible = (entry.status == SyncStatus.PENDING || entry.status == SyncStatus.FAILED) ||
                    (entry.status == SyncStatus.SYNCING && entry.leaseExpiresAt != null && entry.leaseExpiresAt <= now)
            if (isEligible) {
                entries[operationId] = entry.copy(
                    status = SyncStatus.SYNCING,
                    leaseToken = leaseToken,
                    leaseExpiresAt = leaseExpiresAt,
                    lastAttemptAt = now
                )
                return Result.Success(1)
            }
        }
        return Result.Success(0)
    }

    override suspend fun completeEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String
    ): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId && entry.leaseToken == leaseToken) {
            entries.remove(operationId)
            return Result.Success(1)
        }
        return Result.Success(0)
    }

    override suspend fun failEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId && entry.leaseToken == leaseToken) {
            entries[operationId] = entry.copy(
                status = SyncStatus.FAILED,
                retryCount = retryCount,
                lastAttemptAt = lastAttemptAt,
                leaseToken = null,
                leaseExpiresAt = null
            )
            return Result.Success(1)
        }
        return Result.Success(0)
    }

    override suspend fun conflictEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId && entry.leaseToken == leaseToken) {
            entries[operationId] = entry.copy(
                status = SyncStatus.CONFLICT,
                retryCount = retryCount,
                lastAttemptAt = lastAttemptAt,
                leaseToken = null,
                leaseExpiresAt = null
            )
            return Result.Success(1)
        }
        return Result.Success(0)
    }

    override suspend fun recoverExpiredLeasesForUser(userId: String, now: Long): Result<Int, Exception> {
        var count = 0
        for ((opId, entry) in entries.entries.toList()) {
            if (entry.userId == userId && entry.status == SyncStatus.SYNCING && entry.leaseExpiresAt != null && entry.leaseExpiresAt <= now) {
                entries[opId] = entry.copy(
                    status = SyncStatus.PENDING,
                    leaseToken = null,
                    leaseExpiresAt = null
                )
                count++
            }
        }
        return Result.Success(count)
    }

    override suspend fun updateBaseVersionAndPayload(
        operationId: String,
        userId: String,
        newBaseVersion: Long,
        newPayload: String,
        now: Long
    ): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId) {
            entries[operationId] = entry.copy(
                baseVersion = newBaseVersion,
                payload = newPayload,
                status = SyncStatus.PENDING,
                leaseToken = null,
                leaseExpiresAt = null,
                retryCount = 0,
                lastAttemptAt = now
            )
            return Result.Success(1)
        }
        return Result.Success(0)
    }

    override suspend fun recoverStaleSyncingEntriesForUser(userId: String, staleThreshold: Long): Result<Int, Exception> {
        var count = 0
        for ((opId, entry) in entries.entries.toList()) {
            if (entry.userId == userId && entry.status == SyncStatus.SYNCING && entry.lastAttemptAt != null && entry.lastAttemptAt <= staleThreshold) {
                entries[opId] = entry.copy(status = SyncStatus.PENDING)
                count++
            }
        }
        return Result.Success(count)
    }

    override suspend fun rollbackLegacyEntryClaim(operationId: String, userId: String): Result<Int, Exception> {
        val entry = entries[operationId]
        if (entry != null && entry.userId == userId) {
            entries[operationId] = entry.copy(userId = null)
            return Result.Success(1)
        }
        return Result.Success(0)
    }

    override suspend fun entryExists(operationId: String): Result<Boolean, Exception> {
        return Result.Success(entries.containsKey(operationId))
    }

    // Test helpers
    fun getEntry(operationId: String): OutboxEntry? = entries[operationId]
    fun allEntries(): List<OutboxEntry> = entries.values.toList()
}
