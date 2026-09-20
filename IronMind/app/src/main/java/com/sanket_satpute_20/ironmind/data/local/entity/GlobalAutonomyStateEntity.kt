package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing the global autonomy pause state for a user.
 */
@Entity(tableName = "global_autonomy_state")
data class GlobalAutonomyStateEntity(
    @PrimaryKey val userId: String,
    val isGlobalPauseActive: Boolean,
    val updatedAt: Long
)
