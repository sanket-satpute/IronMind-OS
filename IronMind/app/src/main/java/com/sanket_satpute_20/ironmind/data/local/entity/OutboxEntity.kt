package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus

@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["leaseToken"]),
        Index(value = ["leaseGeneration"]),
        Index(value = ["chainedFrom"])
    ]
)
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastAttemptAt: Long?,
    val status: String,
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

fun OutboxEntity.toDomain(): OutboxEntry = OutboxEntry(
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    operationType = OutboxOperationType.valueOf(operationType),
    payload = payload,
    createdAt = createdAt,
    retryCount = retryCount,
    lastAttemptAt = lastAttemptAt,
    status = SyncStatus.valueOf(status),
    userId = userId,
    baseVersion = baseVersion,
    leaseToken = leaseToken,
    leaseExpiresAt = leaseExpiresAt,
    baseServerVersion = baseServerVersion,
    uploadedLocalRevision = uploadedLocalRevision,
    chainedFrom = chainedFrom,
    legacyBaseHash = legacyBaseHash,
    leaseGeneration = leaseGeneration
)

fun extractRootPayloadUserId(payload: String): String? {
    return try {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null
        }
        if (!trimmed.contains("\"userId\"")) {
            return null
        }
        val json = org.json.JSONObject(trimmed)
        if (!json.has("userId") || json.isNull("userId")) {
            return null
        }
        val pattern = Regex("""\"userId\"\s*:\s*\"([^\"\\]*)\"""")
        val match = pattern.find(trimmed) ?: return null
        val extractedValue = match.groupValues[1].trim()
        if (extractedValue.isEmpty()) {
            return null
        }
        val opt = json.opt("userId")
        if (opt !is String) {
            return null
        }
        val uid = opt.trim()
        if (uid != extractedValue || uid.isEmpty()) {
            null
        } else {
            uid
        }
    } catch (_: Exception) {
        null
    }
}

fun OutboxEntry.toEntity(): OutboxEntity {
    val effectiveUserId = userId ?: extractRootPayloadUserId(payload)

    return OutboxEntity(
        operationId = operationId,
        entityType = entityType,
        entityId = entityId,
        operationType = operationType.name,
        payload = payload,
        createdAt = createdAt,
        retryCount = retryCount,
        lastAttemptAt = lastAttemptAt,
        status = status.name,
        userId = effectiveUserId,
        baseVersion = baseVersion,
        leaseToken = leaseToken,
        leaseExpiresAt = leaseExpiresAt
    )
}
