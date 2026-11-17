package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.SyncCheckpointEntity

@Dao
interface SyncCheckpointDao {

    @Query("""
        SELECT * FROM sync_checkpoints
        WHERE userId = :userId AND entityType = :entityType AND traversalType = :traversalType
        LIMIT 1
    """)
    fun getCheckpoint(userId: String, entityType: String, traversalType: String): SyncCheckpointEntity?

    @Query("""
        SELECT * FROM sync_checkpoints
        WHERE userId = :userId AND entityType = :entityType AND traversalType = :traversalType
        LIMIT 1
    """)
    suspend fun getCheckpointAsync(userId: String, entityType: String, traversalType: String): SyncCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCheckpoint(checkpoint: SyncCheckpointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpointAsync(checkpoint: SyncCheckpointEntity)

    @Query("DELETE FROM sync_checkpoints WHERE userId = :userId")
    fun clearCheckpointsForUser(userId: String)

    @Query("DELETE FROM sync_checkpoints WHERE userId = :userId")
    suspend fun clearCheckpointsForUserAsync(userId: String)
}
