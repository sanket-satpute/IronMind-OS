package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity persisting whether the user has enabled calendar context observation.
 */
@Entity(tableName = "calendar_observation_settings")
data class CalendarObservationSettingsEntity(
    @PrimaryKey val userId: String,
    val isEnabled: Boolean,
    val updatedAt: Long
)
