package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity persisting whether the user has enabled app usage observation.
 * Keyed per user. Separate from autonomy pause to keep concerns separate.
 */
@Entity(tableName = "app_usage_observation_settings")
data class AppUsageObservationSettingsEntity(
    @PrimaryKey val userId: String,
    val isEnabled: Boolean,
    val updatedAt: Long
)
