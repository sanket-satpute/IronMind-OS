package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.AutonomySettingsEntity

@Dao
interface AutonomySettingsDao {
    @Query("SELECT * FROM autonomy_settings WHERE userId = :userId")
    fun getSettingsForUser(userId: String): List<AutonomySettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSetting(setting: AutonomySettingsEntity)

    // Sprint V4.13: Data Deletion & Lifecycle Foundation
    @Query("DELETE FROM autonomy_settings WHERE userId = :userId")
    fun deleteAutonomySettingsForUser(userId: String)
}
