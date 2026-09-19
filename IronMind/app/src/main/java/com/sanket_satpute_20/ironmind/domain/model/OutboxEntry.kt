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
    val status: SyncStatus
)

enum class OutboxOperationType {
    UPSERT,
    DELETE
}
