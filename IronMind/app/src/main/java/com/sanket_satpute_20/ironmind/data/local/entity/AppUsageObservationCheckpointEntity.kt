package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint

/**
 * Authoritative SQLite table `app_usage_observation_checkpoints`.
 *
 * Persists the latest successfully processed usage event timestamp and active foreground session
 * per user, enabling bounded queries and recovery after process restart or deep sleep delays.
 */
@Entity(tableName = "app_usage_observation_checkpoints")
data class AppUsageObservationCheckpointEntity(
    @PrimaryKey val userId: String,
    val lastCollectedIntervalEndMs: Long,
    val activePackageName: String?,
    val activeSessionStartTimeMs: Long?,
    val updatedAt: Long,
    val schemaVersion: Int = 1
)

fun AppUsageObservationCheckpointEntity.toDomain(): AppUsageCheckpoint = AppUsageCheckpoint(
    userId = userId,
    lastCollectedIntervalEndMs = lastCollectedIntervalEndMs,
    activePackageName = activePackageName,
    activeSessionStartTimeMs = activeSessionStartTimeMs,
    updatedAt = updatedAt,
    schemaVersion = schemaVersion
)

fun AppUsageCheckpoint.toEntity(): AppUsageObservationCheckpointEntity = AppUsageObservationCheckpointEntity(
    userId = userId,
    lastCollectedIntervalEndMs = lastCollectedIntervalEndMs,
    activePackageName = activePackageName,
    activeSessionStartTimeMs = activeSessionStartTimeMs,
    updatedAt = updatedAt,
    schemaVersion = schemaVersion
)

