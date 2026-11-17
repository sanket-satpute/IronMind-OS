package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.SyncCheckpointDao
import com.sanket_satpute_20.ironmind.data.local.entity.SyncCheckpointEntity
import com.sanket_satpute_20.ironmind.domain.repository.SyncCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor

/**
 * Authoritative SQLite implementation of SyncCheckpointStore.
 * Checkpoints persist directly into the Room `sync_checkpoints` table.
 */
class RoomSyncCheckpointStore(
    private val syncCheckpointDao: SyncCheckpointDao,
    private val defaultTraversalType: String = "COMPOUND_PAGINATION"
) : SyncCheckpointStore {

    override fun getLastCursor(userId: String, entityType: String): SyncCursor {
        val cp = syncCheckpointDao.getCheckpoint(userId, entityType, defaultTraversalType)
            ?: return SyncCursor(0L, "")
        return SyncCursor(timestamp = cp.lastTimestamp, documentId = cp.lastDocumentId)
    }

    override fun setLastCursor(userId: String, entityType: String, cursor: SyncCursor) {
        val now = System.currentTimeMillis()
        val current = syncCheckpointDao.getCheckpoint(userId, entityType, defaultTraversalType)
        val shouldUpdate = current == null ||
            cursor.timestamp > current.lastTimestamp ||
            (cursor.timestamp == current.lastTimestamp && cursor.documentId > current.lastDocumentId)

        if (shouldUpdate) {
            syncCheckpointDao.upsertCheckpoint(
                SyncCheckpointEntity(
                    userId = userId,
                    entityType = entityType,
                    traversalType = defaultTraversalType,
                    lastTimestamp = cursor.timestamp,
                    lastDocumentId = cursor.documentId,
                    cycleCount = current?.cycleCount ?: 0,
                    lastCycleCompletedAt = current?.lastCycleCompletedAt ?: 0L,
                    updatedAt = now
                )
            )
        }
    }

    override fun getLastCheckpoint(userId: String, entityType: String): Long {
        return getLastCursor(userId, entityType).timestamp
    }

    override fun setLastCheckpoint(userId: String, entityType: String, checkpoint: Long) {
        setLastCursor(userId, entityType, SyncCursor(checkpoint, ""))
    }

    override fun clearCheckpointsForUser(userId: String) {
        syncCheckpointDao.clearCheckpointsForUser(userId)
    }
}
