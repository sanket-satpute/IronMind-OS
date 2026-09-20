package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.AppUsageObservationSettingsEntity

@Dao
interface AppUsageObservationSettingsDao {
    @Query("SELECT * FROM app_usage_observation_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettingsForUser(userId: String): AppUsageObservationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(entity: AppUsageObservationSettingsEntity)
}
