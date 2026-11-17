package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Durable local tombstone tracking table.
 * Records deleted entities to prevent resurrecting deleted records during downstream pull
 * and to track pending tombstone sync upstream.
 */
@Entity(
    tableName = "local_tombstones",
    primaryKeys = ["userId", "entityType", "entityId"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["deletedAt"])
    ]
)
data class LocalTombstoneEntity(
    val userId: String,
    val entityType: String,
    val entityId: String,
    val status: String, // "PENDING", "SYNCED", "PURGED"
    val deletionOperationId: String? = null,
    val baseServerVersion: Long? = null,
    val canonicalServerVersion: Long? = null,
    val localRevision: Long = 1L,
    val deletedAt: Long,
    val updatedAt: Long = deletedAt
)
