package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry

/**
 * Remote repository interface for pushing local data to the remote backend.
 * Implementations must NOT expose Firestore types to callers.
 * Each operation must be idempotent using the OutboxEntry.operationId.
 */
interface SyncRepository {

    /**
     * Uploads a single OutboxEntry to the remote backend.
     * The operation must be idempotent — retrying the same operationId must be safe.
     * Returns SyncConflict if the remote version conflicts with the local payload.
     */
    suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception>
}

sealed class SyncUploadResult {
    object Success : SyncUploadResult()
    object Conflict : SyncUploadResult()
}
