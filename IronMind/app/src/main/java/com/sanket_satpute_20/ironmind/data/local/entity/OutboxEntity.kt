package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val status: String
)

fun OutboxEntity.toDomain(): OutboxEntry = OutboxEntry(
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    operationType = OutboxOperationType.valueOf(operationType),
    payload = payload,
    createdAt = createdAt,
    retryCount = retryCount,
    lastAttemptAt = lastAttemptAt,
    status = SyncStatus.valueOf(status)
)

fun OutboxEntry.toEntity(): OutboxEntity = OutboxEntity(
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    operationType = operationType.name,
    payload = payload,
    createdAt = createdAt,
    retryCount = retryCount,
    lastAttemptAt = lastAttemptAt,
    status = status.name
)
