package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEntry(entry: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun getPendingEntries(): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    fun getAllEntries(): List<OutboxEntity>

    @Deprecated("Legacy unscoped status update. Use updateStatusForUser in production code.")
    @Query("UPDATE outbox SET status = :status, retryCount = :retryCount, lastAttemptAt = :lastAttemptAt WHERE operationId = :operationId")
    fun updateStatus(operationId: String, status: String, retryCount: Int, lastAttemptAt: Long)

    @Deprecated("Legacy unscoped delete. Use deleteEntryForUser in production code.")
    @Query("DELETE FROM outbox WHERE operationId = :operationId")
    fun deleteEntry(operationId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE status IN ('PENDING', 'FAILED')")
    fun countPendingEntries(): Int

    @Query("SELECT COUNT(*) FROM outbox")
    fun getOutboxCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) > 0 FROM outbox WHERE operationId = :operationId")
    fun entryExists(operationId: String): Boolean

    // =========================================================================
    // User-Scoped Outbox Queries (Phase 8.3.3.1.3 Strict Ownership Hardening)
    // =========================================================================

    @Query("SELECT * FROM outbox WHERE userId = :userId AND status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun getPendingEntriesForUser(userId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE userId = :userId AND status = 'FAILED' ORDER BY createdAt ASC")
    fun getFailedEntriesForUser(userId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE userId = :userId AND status = 'CONFLICT' ORDER BY createdAt ASC")
    fun getConflictEntriesForUser(userId: String): List<OutboxEntity>

    @Deprecated("Legacy unscoped lookup. Use getEntryByOperationIdForUser in production code.")
    @Query("SELECT * FROM outbox WHERE operationId = :operationId LIMIT 1")
    fun getEntryByOperationId(operationId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE operationId = :operationId AND userId = :userId LIMIT 1")
    fun getEntryByOperationIdForUser(operationId: String, userId: String): OutboxEntity?

    @Query("UPDATE outbox SET status = :status, retryCount = :retryCount, lastAttemptAt = :lastAttemptAt WHERE operationId = :operationId AND userId = :userId")
    fun updateStatusForUser(
        operationId: String,
        userId: String,
        status: String,
        retryCount: Int,
        lastAttemptAt: Long
    ): Int

    @Query("DELETE FROM outbox WHERE operationId = :operationId AND userId = :userId")
    fun deleteEntryForUser(operationId: String, userId: String): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE userId = :userId")
    fun getOutboxCountForUser(userId: String): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE userId = :userId AND status IN ('PENDING', 'FAILED')")
    fun countPendingEntriesForUser(userId: String): Int

    @Query("DELETE FROM outbox WHERE userId = :userId")
    fun deleteEntriesForUser(userId: String)

    @Query("SELECT * FROM outbox WHERE userId IS NULL ORDER BY createdAt ASC")
    fun getOrphanedEntries(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE userId IS NULL ORDER BY createdAt ASC LIMIT :limit")
    fun getUnclaimedCandidates(limit: Int): List<OutboxEntity>

    @Query("UPDATE outbox SET userId = :userId WHERE operationId = :operationId AND userId IS NULL")
    fun claimLegacyEntry(operationId: String, userId: String): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE userId IS NULL")
    fun countOrphanedEntries(): Int

    // =========================================================================
    // Sync Reliability & In-Flight State Management (Phase 8.3.3.2)
    // =========================================================================

    @Query("UPDATE outbox SET status = 'SYNCING', lastAttemptAt = :now WHERE operationId = :operationId AND userId = :userId AND status IN ('PENDING', 'FAILED')")
    fun claimEntryForSync(operationId: String, userId: String, now: Long): Int

    @Query("UPDATE outbox SET status = 'PENDING' WHERE userId = :userId AND status = 'SYNCING' AND lastAttemptAt IS NOT NULL AND lastAttemptAt <= :staleThreshold")
    fun recoverStaleSyncingEntriesForUser(userId: String, staleThreshold: Long): Int

    @Query("UPDATE outbox SET userId = NULL WHERE operationId = :operationId AND userId = :userId")
    fun rollbackLegacyEntryClaim(operationId: String, userId: String): Int

    // =========================================================================
    // Lease Fencing & Distributed Versioning (Phase 8.3.3.2.2 & 8.3.3.6A)
    // =========================================================================

    @Query("""
        UPDATE outbox
        SET status = 'SYNCING',
            leaseToken = :leaseToken,
            leaseExpiresAt = :leaseExpiresAt,
            leaseGeneration = :leaseGeneration,
            lastAttemptAt = :now
        WHERE operationId = :operationId
          AND userId = :userId
          AND (
              status IN ('PENDING', 'FAILED')
              OR (status = 'SYNCING' AND (
                  (leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now)
                  OR (leaseExpiresAt IS NULL AND (lastAttemptAt IS NULL OR lastAttemptAt <= :now))
                  OR (:leaseGeneration IS NOT NULL AND leaseGeneration IS NOT NULL AND leaseGeneration < :leaseGeneration)
              ))
          )
    """)
    fun claimEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        leaseExpiresAt: Long,
        now: Long,
        leaseGeneration: Long? = null
    ): Int

    @Query("""
        DELETE FROM outbox
        WHERE operationId = :operationId
          AND userId = :userId
          AND leaseToken = :leaseToken
          AND (:leaseGeneration IS NULL OR leaseGeneration = :leaseGeneration OR leaseGeneration IS NULL)
    """)
    fun completeEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        leaseGeneration: Long? = null
    ): Int

    @Query("""
        UPDATE outbox
        SET status = 'FAILED',
            retryCount = :retryCount,
            lastAttemptAt = :lastAttemptAt,
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            leaseGeneration = NULL
        WHERE operationId = :operationId
          AND userId = :userId
          AND leaseToken = :leaseToken
          AND (:leaseGeneration IS NULL OR leaseGeneration = :leaseGeneration OR leaseGeneration IS NULL)
    """)
    fun failEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long,
        leaseGeneration: Long? = null
    ): Int

    @Query("""
        UPDATE outbox
        SET status = 'CONFLICT',
            retryCount = :retryCount,
            lastAttemptAt = :lastAttemptAt,
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            leaseGeneration = NULL
        WHERE operationId = :operationId
          AND userId = :userId
          AND leaseToken = :leaseToken
          AND (:leaseGeneration IS NULL OR leaseGeneration = :leaseGeneration OR leaseGeneration IS NULL)
    """)
    fun conflictEntryWithLease(
        operationId: String,
        userId: String,
        leaseToken: String,
        retryCount: Int,
        lastAttemptAt: Long,
        leaseGeneration: Long? = null
    ): Int

    @Query("""
        UPDATE outbox
        SET status = 'DEAD_LETTER',
            retryCount = :retryCount,
            lastAttemptAt = :now,
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            leaseGeneration = NULL
        WHERE operationId = :operationId
          AND userId = :userId
    """)
    fun deadLetterEntry(
        operationId: String,
        userId: String,
        retryCount: Int,
        now: Long
    ): Int

    @Query("""
        UPDATE outbox
        SET status = 'PENDING',
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            leaseGeneration = NULL
        WHERE userId = :userId
          AND status = 'SYNCING'
          AND (
              (leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now)
              OR (leaseExpiresAt IS NULL AND (lastAttemptAt IS NULL OR lastAttemptAt <= :now))
              OR (:authoritativeGeneration IS NOT NULL AND leaseGeneration IS NOT NULL AND leaseGeneration < :authoritativeGeneration)
          )
    """)
    fun recoverExpiredLeasesForUser(
        userId: String,
        now: Long,
        authoritativeGeneration: Long? = null
    ): Int

    @Query("""
        UPDATE outbox
        SET status = 'SYNCED',
            lastAttemptAt = :now
        WHERE operationId = :operationId
          AND userId = :userId
          AND leaseToken = :leaseToken
          AND (:expectedGeneration IS NULL OR leaseGeneration = :expectedGeneration OR leaseGeneration IS NULL)
          AND status = 'SYNCING'
          AND leaseExpiresAt > :now
    """)
    fun markEntrySyncedFenced(
        operationId: String,
        userId: String,
        leaseToken: String,
        expectedGeneration: Long?,
        now: Long
    ): Int

    @Query("""
        UPDATE outbox
        SET chainedFrom = NULL,
            baseServerVersion = :canonicalVersion,
            lastAttemptAt = :now
        WHERE userId = :userId
          AND chainedFrom = :predecessorOpId
          AND status = 'PENDING'
          AND EXISTS (
              SELECT 1 FROM outbox p
              WHERE p.operationId = :predecessorOpId
                AND p.userId = :userId
                AND p.status = 'SYNCED'
                AND p.leaseToken = :leaseToken
                AND (:expectedGeneration IS NULL OR p.leaseGeneration = :expectedGeneration OR p.leaseGeneration IS NULL)
          )
    """)
    fun unlockChainedOperationsFenced(
        predecessorOpId: String,
        userId: String,
        canonicalVersion: Long,
        leaseToken: String,
        expectedGeneration: Long?,
        now: Long
    ): Int

    @Query("""
        DELETE FROM outbox
        WHERE operationId = :predecessorOpId
          AND userId = :userId
          AND status = 'SYNCED'
          AND leaseToken = :leaseToken
          AND (:expectedGeneration IS NULL OR leaseGeneration = :expectedGeneration OR leaseGeneration IS NULL)
    """)
    fun purgePredecessorFenced(
        predecessorOpId: String,
        userId: String,
        leaseToken: String,
        expectedGeneration: Long?
    ): Int

    @Query("UPDATE outbox SET baseVersion = :newBaseVersion, payload = :newPayload, status = 'PENDING', leaseToken = NULL, leaseExpiresAt = NULL, retryCount = 0, lastAttemptAt = :now WHERE operationId = :operationId AND userId = :userId")
    fun updateBaseVersionAndPayload(
        operationId: String,
        userId: String,
        newBaseVersion: Long,
        newPayload: String,
        now: Long
    ): Int

    // =========================================================================
    // Downstream Sync & Divergence Detection (Phase 8.3.3.5)
    // =========================================================================

    @Query("SELECT * FROM outbox WHERE userId = :userId AND entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'SYNCING', 'CONFLICT') ORDER BY createdAt DESC LIMIT 1")
    fun getActiveEntryForEntity(userId: String, entityType: String, entityId: String): OutboxEntity?

    @Query("UPDATE outbox SET status = 'CONFLICT', lastAttemptAt = :now, leaseToken = NULL, leaseExpiresAt = NULL WHERE operationId = :operationId AND userId = :userId")
    fun quarantineEntryToConflict(operationId: String, userId: String, now: Long): Int
}
