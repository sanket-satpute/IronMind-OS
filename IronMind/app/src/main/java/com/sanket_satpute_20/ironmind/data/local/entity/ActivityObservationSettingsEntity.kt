package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_observation_settings")
data class ActivityObservationSettingsEntity(
    @PrimaryKey
    val userId: String,
    val isEnabled: Boolean,
    val lastUpdatedAt: Long
)
