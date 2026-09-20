package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_observation_settings")
data class NotificationObservationSettingsEntity(
    @PrimaryKey
    val userId: String,
    val isEnabled: Boolean,
    val updatedAt: Long
)
