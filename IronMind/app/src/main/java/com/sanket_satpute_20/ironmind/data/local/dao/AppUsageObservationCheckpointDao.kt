package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.AppUsageObservationCheckpointEntity

@Dao
interface AppUsageObservationCheckpointDao {

    @Query("SELECT * FROM app_usage_observation_checkpoints WHERE userId = :userId LIMIT 1")
    fun getCheckpoint(userId: String): AppUsageObservationCheckpointEntity?

    @Query("SELECT * FROM app_usage_observation_checkpoints WHERE userId = :userId LIMIT 1")
    suspend fun getCheckpointAsync(userId: String): AppUsageObservationCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCheckpoint(checkpoint: AppUsageObservationCheckpointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpointAsync(checkpoint: AppUsageObservationCheckpointEntity)

    @Query("DELETE FROM app_usage_observation_checkpoints WHERE userId = :userId")
    fun deleteCheckpointForUser(userId: String)

    @Query("DELETE FROM app_usage_observation_checkpoints WHERE userId = :userId")
    suspend fun deleteCheckpointForUserAsync(userId: String)
}

