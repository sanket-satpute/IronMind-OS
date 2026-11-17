package com.sanket_satpute_20.ironmind.domain.repository

/**
 * Persists and retrieves sync checkpoints per user and entity type.
 * Ensures downstream pull operations query only updates since the last successful sync.
 */
interface SyncCheckpointStore {
    fun getLastCheckpoint(userId: String, entityType: String): Long =
        getLastCursor(userId, entityType).timestamp

    fun setLastCheckpoint(userId: String, entityType: String, checkpoint: Long) =
        setLastCursor(userId, entityType, SyncCursor(checkpoint, ""))

    fun getLastCursor(userId: String, entityType: String): SyncCursor =
        SyncCursor(getLastCheckpoint(userId, entityType), "")

    fun setLastCursor(userId: String, entityType: String, cursor: SyncCursor) {
        setLastCheckpoint(userId, entityType, cursor.timestamp)
    }

    fun clearCheckpointsForUser(userId: String)
}
