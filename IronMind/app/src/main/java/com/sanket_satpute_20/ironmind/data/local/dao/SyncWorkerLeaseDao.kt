package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sanket_satpute_20.ironmind.data.local.entity.SyncWorkerLeaseEntity

@Dao
interface SyncWorkerLeaseDao {

    @Query("SELECT * FROM sync_worker_leases WHERE leaseKey = :leaseKey LIMIT 1")
    suspend fun getLease(leaseKey: String): SyncWorkerLeaseEntity?

    @Query("SELECT * FROM sync_worker_leases WHERE leaseKey = :leaseKey LIMIT 1")
    fun getLeaseSync(leaseKey: String): SyncWorkerLeaseEntity?

    @Query("SELECT currentGeneration FROM app_session_state WHERE id = 1 LIMIT 1")
    suspend fun getAuthoritativeGeneration(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialLease(entity: SyncWorkerLeaseEntity): Long

    @Query("""
        UPDATE sync_worker_leases
        SET ownerToken = :callerOwnerToken,
            sessionGeneration = :callerGeneration,
            status = 'ACTIVE',
            acquiredAt = :now,
            expiresAt = :now + :leaseDurationMs,
            updatedAt = :now
        WHERE leaseKey = :leaseKey
          AND (
              -- Takeover Condition A: Expired lease from the current session
              (sessionGeneration = :callerGeneration AND expiresAt <= :now)
              -- Takeover Condition B: Lease from an older session (takes precedence once caller is authoritative)
              OR (sessionGeneration < :callerGeneration)
          )
    """)
    suspend fun updateExpiredOrSupersededLease(
        leaseKey: String,
        callerOwnerToken: String,
        callerGeneration: Long,
        leaseDurationMs: Long,
        now: Long
    ): Int

    /**
     * Atomically acquires an initial lease or takes over an expired/superseded lease.
     * Evaluates authoritative persisted session generation within an atomic SQLite transaction.
     *
     * Returns 1 if acquisition/takeover succeeded, 0 if rejected.
     */
    @Transaction
    suspend fun acquireOrTakeoverLease(
        leaseKey: String,
        callerOwnerToken: String,
        callerGeneration: Long,
        leaseDurationMs: Long,
        now: Long
    ): Long {
        val authGen = getAuthoritativeGeneration() ?: return 0L
        if (authGen != callerGeneration) {
            return 0L
        }

        val updatedRows = updateExpiredOrSupersededLease(
            leaseKey = leaseKey,
            callerOwnerToken = callerOwnerToken,
            callerGeneration = callerGeneration,
            leaseDurationMs = leaseDurationMs,
            now = now
        )
        if (updatedRows > 0) {
            return 1L
        }

        val insertedRowId = insertInitialLease(
            SyncWorkerLeaseEntity(
                leaseKey = leaseKey,
                ownerToken = callerOwnerToken,
                sessionGeneration = callerGeneration,
                generationId = 0,
                cursor = "",
                status = "ACTIVE",
                acquiredAt = now,
                expiresAt = now + leaseDurationMs,
                updatedAt = now,
                lastCompletedAt = null
            )
        )
        return if (insertedRowId > 0L) 1L else 0L
    }

    /**
     * Heartbeat / renewal: extends expiresAt for an active lease.
     * Must match ownerToken, expectedGeneration, authoritative generation, and expiresAt > now.
     * Expired leases return 0 and CANNOT be renewed.
     */
    @Query("""
        UPDATE sync_worker_leases
        SET expiresAt = :now + :leaseDurationMs,
            updatedAt = :now
        WHERE leaseKey = :leaseKey
          AND ownerToken = :ownerToken
          AND sessionGeneration = :expectedGeneration
          AND sessionGeneration = (SELECT currentGeneration FROM app_session_state WHERE id = 1)
          AND status IN ('ACTIVE', 'IN_PROGRESS')
          AND expiresAt > :now
    """)
    suspend fun renewLease(
        leaseKey: String,
        ownerToken: String,
        expectedGeneration: Long,
        leaseDurationMs: Long,
        now: Long
    ): Int

    /**
     * Atomically commits page progress cursor and extends lease validity.
     * Must match ownerToken, expectedGeneration, authoritative generation, and expiresAt > now.
     */
    @Query("""
        UPDATE sync_worker_leases
        SET cursor = :nextCursor,
            expiresAt = :now + :leaseDurationMs,
            status = 'IN_PROGRESS',
            updatedAt = :now
        WHERE leaseKey = :leaseKey
          AND ownerToken = :ownerToken
          AND sessionGeneration = :expectedGeneration
          AND sessionGeneration = (SELECT currentGeneration FROM app_session_state WHERE id = 1)
          AND status IN ('ACTIVE', 'IN_PROGRESS')
          AND expiresAt > :now
    """)
    suspend fun commitPageProgress(
        leaseKey: String,
        ownerToken: String,
        expectedGeneration: Long,
        nextCursor: String,
        leaseDurationMs: Long,
        now: Long
    ): Int

    /**
     * Marks generation completed, resets cursor, increments generationId.
     */
    @Query("""
        UPDATE sync_worker_leases
        SET cursor = '',
            status = 'COMPLETED',
            generationId = generationId + 1,
            lastCompletedAt = :now,
            expiresAt = :now + :leaseDurationMs,
            updatedAt = :now
        WHERE leaseKey = :leaseKey
          AND ownerToken = :ownerToken
          AND sessionGeneration = :expectedGeneration
          AND sessionGeneration = (SELECT currentGeneration FROM app_session_state WHERE id = 1)
          AND status IN ('ACTIVE', 'IN_PROGRESS')
          AND expiresAt > :now
    """)
    suspend fun completeGeneration(
        leaseKey: String,
        ownerToken: String,
        expectedGeneration: Long,
        leaseDurationMs: Long,
        now: Long
    ): Int

    /**
     * Orderly release on termination.
     * Validates ownerToken, expectedGeneration, and authoritative generation.
     */
    @Query("""
        UPDATE sync_worker_leases
        SET expiresAt = 0,
            status = 'IDLE',
            updatedAt = :now
        WHERE leaseKey = :leaseKey
          AND ownerToken = :ownerToken
          AND sessionGeneration = :expectedGeneration
          AND sessionGeneration = (SELECT currentGeneration FROM app_session_state WHERE id = 1)
          AND status IN ('ACTIVE', 'IN_PROGRESS', 'COMPLETED')
    """)
    suspend fun releaseLease(
        leaseKey: String,
        ownerToken: String,
        expectedGeneration: Long,
        now: Long
    ): Int

    /**
     * Revokes all leases belonging to older generations.
     */
    @Query("""
        UPDATE sync_worker_leases
        SET expiresAt = 0,
            status = 'REVOKED',
            updatedAt = :now
        WHERE sessionGeneration < :authoritativeGeneration
    """)
    suspend fun revokeLeasesForOlderGenerations(
        authoritativeGeneration: Long,
        now: Long
    ): Int
}
