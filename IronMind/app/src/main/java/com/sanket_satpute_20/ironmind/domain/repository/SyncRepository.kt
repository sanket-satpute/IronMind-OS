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

    /**
     * Pulls remote documents for the authenticated user and given entityType.
     * Only documents with updatedAt > sinceTimestamp are returned, ordered by updatedAt ascending.
     */
    suspend fun pull(
        userId: String,
        entityType: String,
        sinceTimestamp: Long,
        limit: Int
    ): Result<SyncPullResult, Exception> =
        pull(userId, entityType, SyncCursor(sinceTimestamp, ""), limit)

    /**
     * Pulls remote documents using a compound cursor (updatedAt, documentId).
     * Guarantees deterministic pagination across page boundaries and handles duplicate timestamps.
     */
    suspend fun pull(
        userId: String,
        entityType: String,
        cursor: SyncCursor,
        limit: Int
    ): Result<SyncPullResult, Exception> =
        Result.Success(SyncPullResult.Success(items = emptyList(), hasMore = false))

    /**
     * Traverses remote documents strictly by documentId ascending without depending on updatedAt index.
     * Discovers and transactionally backfills legacy documents missing updatedAt per Phase 8.3.3.6A.6.4/6.5.
     */
    suspend fun discoverLegacyDocuments(
        userId: String,
        entityType: String,
        startAfterDocId: String,
        limit: Int
    ): Result<SyncPullResult, Exception> =
        Result.Success(SyncPullResult.Success(items = emptyList(), hasMore = false))
}

data class SyncCursor(
    val timestamp: Long = 0L,
    val documentId: String = ""
)

data class RemoteEntityPayload(
    val entityType: String,
    val entityId: String,
    val version: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
    val payloadJson: String
)

sealed class SyncPullResult {
    data class Success(val items: List<RemoteEntityPayload>, val hasMore: Boolean) : SyncPullResult()
    data class Failure(val error: Exception) : SyncPullResult()
}

sealed class SyncUploadResult {
    object Success : SyncUploadResult()
    open class Conflict(
        val remotePayload: String? = null,
        val conflictReason: String = "REMOTE_VERSION_MISMATCH"
    ) : SyncUploadResult() {
        companion object : Conflict(null, "REMOTE_VERSION_MISMATCH")

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Conflict) return false
            return remotePayload == other.remotePayload && conflictReason == other.conflictReason
        }

        override fun hashCode(): Int {
            var result = remotePayload?.hashCode() ?: 0
            result = 31 * result + conflictReason.hashCode()
            return result
        }

        override fun toString(): String {
            return "Conflict(remotePayload=$remotePayload, conflictReason=$conflictReason)"
        }
    }
}
