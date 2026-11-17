package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified Room entity storing worker leases, fencing tokens, session generations,
 * pagination cursors, and generation lifecycle states.
 *
 * Ensures all distributed coordination and progress commits share 100% true
 * ACID transactional atomicity within SQLite.
 */
@Entity(
    tableName = "sync_worker_leases",
    indices = [
        Index(value = ["sessionGeneration"]),
        Index(value = ["expiresAt"])
    ]
)
data class SyncWorkerLeaseEntity(
    @PrimaryKey val leaseKey: String,
    val ownerToken: String,
    val sessionGeneration: Long,
    val generationId: Long,
    val cursor: String,
    val status: String, // "ACTIVE", "IN_PROGRESS", "COMPLETED", "IDLE", "REVOKED"
    val acquiredAt: Long,
    val expiresAt: Long,
    val updatedAt: Long,
    val lastCompletedAt: Long?
)
