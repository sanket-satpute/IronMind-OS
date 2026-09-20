package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.LocationObservationSettingsEntity

@Dao
interface LocationObservationSettingsDao {
    @Query("SELECT * FROM location_observation_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettings(userId: String): LocationObservationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: LocationObservationSettingsEntity)
}
