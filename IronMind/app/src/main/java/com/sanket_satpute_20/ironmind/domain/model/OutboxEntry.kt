package com.sanket_satpute_20.ironmind.domain.model

data class OutboxEntry(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: OutboxOperationType,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val status: SyncStatus,
    val userId: String? = null,
    val baseVersion: Long? = null,
    val leaseToken: String? = null,
    val leaseExpiresAt: Long? = null,
    val baseServerVersion: Long? = null,
    val uploadedLocalRevision: Long = 1L,
    val chainedFrom: String? = null,
    val legacyBaseHash: String? = null,
    val leaseGeneration: Long? = null
)

enum class OutboxOperationType {
    UPSERT,
    DELETE
}
