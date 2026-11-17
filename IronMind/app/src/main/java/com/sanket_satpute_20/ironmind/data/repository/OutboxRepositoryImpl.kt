package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository

@Suppress("DEPRECATION")
class OutboxRepositoryImpl(
    private val dao: OutboxDao
) : OutboxRepository {

    override suspend fun insertEntry(entry: OutboxEntry): Result<Unit, Exception> {
        return try {
            dao.insertEntry(entry.toEntity())
            println("IronMindLifecycle [Sync] [OUTBOX_ENTRY_ADDED] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId}")
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPendingEntries(): Result<List<OutboxEntry>, Exception> {
        return try {
            val entries = dao.getPendingEntries().map { it.toDomain() }
            Result.Success(entries)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPendingEntriesForUser(userId: String): Result<List<OutboxEntry>, Exception> {
        return try {
            val entries = dao.getPendingEntriesForUser(userId).map { it.toDomain() }
            Result.Success(entries)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getEntryByOperationIdForUser(
        operationId: String,
        userId: String
    ): Result<OutboxEntry?, Exception> {
        return try {
            val entry = dao.getEntryByOperationIdForUser(operationId, userId)?.toDomain()
            Result.Success(entry)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateStatusForUser(
        operationId: String,
        userId: String,
        status: SyncStatus,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.updateStatusForUser(operationId, userId, status.name, retryCount, lastAttemptAt)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deleteEntry(operationId: String): Result<Unit, Exception> {
        return try {
            dao.deleteEntry(operationId)
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deleteEntryForUser(
        operationId: String,
        userId: String
    ): Result<Int, Exception> {
        return try {
            val rows = dao.deleteEntryForUser(operationId, userId)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun countPendingEntries(): Result<Int, Exception> {
        return try {
            Result.Success(dao.countPendingEntries())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun countPendingEntriesForUser(userId: String): Result<Int, Exception> {
        return try {
            Result.Success(dao.countPendingEntriesForUser(userId))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun claimLegacyEntriesForUser(userId: String, batchLimit: Int): Result<Int, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank for legacy claiming"))
        }
        return try {
            val candidates = dao.getUnclaimedCandidates(batchLimit)
            var claimedCount = 0
            for (candidate in candidates) {
                val rootUserId = com.sanket_satpute_20.ironmind.data.local.entity.extractRootPayloadUserId(candidate.payload) ?: continue
                if (rootUserId != userId) {
                    continue
                }
                val rows = dao.claimLegacyEntry(candidate.operationId, userId)
                if (rows > 0) {
                    val verified = dao.getEntryByOperationIdForUser(candidate.operationId, userId)
                    val verifiedRootUserId = verified?.let {
                        com.sanket_satpute_20.ironmind.data.local.entity.extractRootPayloadUserId(it.payload)
                    }
                    if (verified != null && verified.userId == userId && verifiedRootUserId == userId) {
                        claimedCount++
                    } else {
                        dao.rollbackLegacyEntryClaim(candidate.operationId, userId)
                        println("IronMindLifecycle [Sync] [CLAIM_VERIFICATION_FAILED_ROLLED_BACK] operationId=${candidate.operationId}")
                    }
                }
            }
            Result.Success(claimedCount)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun claimEntryForSync(
        operationId: String,
        userId: String,
        now: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.claimEntryForSync(operationId, userId, now)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun claimEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        leaseExpiresAt: Long,
        now: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.claimEntryWithLease(operationId, userId, leaseToken, leaseExpiresAt, now)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun completeEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String
    ): Result<Int, Exception> {
        return try {
            val rows = dao.completeEntryWithLease(operationId, userId, leaseToken)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun failEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.failEntryWithLease(operationId, userId, leaseToken, retryCount, lastAttemptAt)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun conflictEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.conflictEntryWithLease(operationId, userId, leaseToken, retryCount, lastAttemptAt)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun recoverExpiredLeasesForUser(userId: String, now: Long): Result<Int, Exception> {
        return try {
            val rows = dao.recoverExpiredLeasesForUser(userId, now)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateBaseVersionAndPayload(
        operationId: String,
        userId: String,
        newBaseVersion: Long,
        newPayload: String,
        now: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.updateBaseVersionAndPayload(operationId, userId, newBaseVersion, newPayload, now)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun recoverStaleSyncingEntriesForUser(
        userId: String,
        staleThreshold: Long
    ): Result<Int, Exception> {
        return try {
            val rows = dao.recoverStaleSyncingEntriesForUser(userId, staleThreshold)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun rollbackLegacyEntryClaim(
        operationId: String,
        userId: String
    ): Result<Int, Exception> {
        return try {
            val rows = dao.rollbackLegacyEntryClaim(operationId, userId)
            Result.Success(rows)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun entryExists(operationId: String): Result<Boolean, Exception> {
        return try {
            Result.Success(dao.entryExists(operationId))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
