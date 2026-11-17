package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.SyncConflict

/**
 * Durable persistence for synchronization conflicts.
 *
 * Establishes explicit user ownership and records both local and remote payloads
 * along with conflict diagnosis reasons and resolution lifecycle status.
 */
@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["resolutionStatus"]),
        Index(value = ["createdAt"]),
        Index(value = ["operationId"], unique = true)
    ]
)
data class SyncConflictEntity(
    @PrimaryKey val conflictId: String,
    val operationId: String,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val localPayload: String,
    val remotePayload: String? = null,
    val conflictReason: String,
    val createdAt: Long,
    val updatedAt: Long,
    val resolutionStatus: String,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null
)

fun SyncConflictEntity.toDomain(): SyncConflict = SyncConflict(
    conflictId = conflictId,
    operationId = operationId,
    userId = userId,
    entityType = entityType,
    entityId = entityId,
    localPayload = localPayload,
    remotePayload = remotePayload,
    conflictReason = conflictReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolutionStatus = resolutionStatus,
    resolvedAt = resolvedAt,
    resolvedBy = resolvedBy
)

fun SyncConflict.toEntity(): SyncConflictEntity = SyncConflictEntity(
    conflictId = conflictId,
    operationId = operationId,
    userId = userId,
    entityType = entityType,
    entityId = entityId,
    localPayload = localPayload,
    remotePayload = remotePayload,
    conflictReason = conflictReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolutionStatus = resolutionStatus,
    resolvedAt = resolvedAt,
    resolvedBy = resolvedBy
)
