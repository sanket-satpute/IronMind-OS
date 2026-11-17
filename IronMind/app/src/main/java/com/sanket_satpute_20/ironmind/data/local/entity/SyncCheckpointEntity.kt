package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Authoritative SQLite sync checkpoint persistence.
 *
 * Ensures downstream compound pagination checkpoints are committed atomically
 * inside the same SQLite transaction that applies remote entity mutations to Room.
 */
@Entity(
    tableName = "sync_checkpoints",
    primaryKeys = ["userId", "entityType", "traversalType"],
    indices = [
        Index(value = ["userId"])
    ]
)
data class SyncCheckpointEntity(
    val userId: String,
    val entityType: String,
    val traversalType: String,
    val lastTimestamp: Long,
    val lastDocumentId: String,
    val cycleCount: Int = 0,
    val lastCycleCompletedAt: Long = 0L,
    val updatedAt: Long
)
