package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncConflict

/**
 * Strategy chosen by an authorized user to resolve a durable sync conflict.
 */
enum class ConflictResolutionStrategy {
    /**
     * User chooses the local mutation. The conflict is marked resolved, and the local
     * outbox entry is reset to PENDING to be re-uploaded.
     */
    USE_LOCAL,

    /**
     * User chooses the remote version. The conflict is marked resolved, and the local
     * outbox entry is discarded.
     */
    USE_REMOTE,

    /**
     * User explicitly discards both/aborts. The conflict is marked DISCARDED and local outbox entry deleted.
     */
    DISCARD
}

/**
 * Domain repository interface for managing and resolving synchronization conflicts.
 */
interface SyncConflictRepository {

    suspend fun getUnresolvedConflictsForUser(userId: String): Result<List<SyncConflict>, Exception>

    suspend fun getConflictByIdForUser(conflictId: String, userId: String): Result<SyncConflict?, Exception>

    suspend fun resolveConflictForUser(
        conflictId: String,
        userId: String,
        strategy: ConflictResolutionStrategy,
        resolvedBy: String = userId
    ): Result<Unit, Exception>
}
