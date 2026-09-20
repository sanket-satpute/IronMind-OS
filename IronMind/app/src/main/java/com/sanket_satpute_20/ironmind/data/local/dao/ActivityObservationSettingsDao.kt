package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.ActivityObservationSettingsEntity

@Dao
interface ActivityObservationSettingsDao {
    @Query("SELECT * FROM activity_observation_settings WHERE userId = :userId")
    suspend fun getSettings(userId: String): ActivityObservationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: ActivityObservationSettingsEntity)
}
