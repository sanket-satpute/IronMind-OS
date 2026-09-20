package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.NotificationObservationSettingsEntity

@Dao
interface NotificationObservationSettingsDao {
    @Query("SELECT * FROM notification_observation_settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettings(userId: String): NotificationObservationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: NotificationObservationSettingsEntity)
}
