package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel

/**
 * Room entity representing the autonomy setting for a specific capability for a user.
 * We use a composite primary key of userId and capability to ensure exactly one
 * setting per capability per user.
 */
@Entity(tableName = "autonomy_settings", primaryKeys = ["userId", "capability"])
data class AutonomySettingsEntity(
    val userId: String,
    val capability: String,
    val level: String,
    val updatedAt: Long
)
