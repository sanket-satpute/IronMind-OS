package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity persisting whether the user has enabled location context observation.
 * Keyed per user. Location observation is strictly coarse and snapshot-based;
 * no continuous tracking is permitted.
 */
@Entity(tableName = "location_observation_settings")
data class LocationObservationSettingsEntity(
    @PrimaryKey val userId: String,
    val isEnabled: Boolean,
    val updatedAt: Long
)
