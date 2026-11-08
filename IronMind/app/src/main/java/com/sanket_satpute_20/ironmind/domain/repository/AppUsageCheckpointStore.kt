package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation

/**
 * Authoritative domain model representing the persistent app-usage observation checkpoint.
 *
 * Strict user-scoped tracking ensures cross-account isolation.
 * Contains both cursor timestamp progress and foreground-session reconstruction state.
 */
data class AppUsageCheckpoint(
    val userId: String,
    val lastCollectedIntervalEndMs: Long,
    val activePackageName: String?,
    val activeSessionStartTimeMs: Long?,
    val updatedAt: Long,
    val schemaVersion: Int = 1
)

/**
 * Abstraction for persisting and retrieving app usage observation checkpoints.
 *
 * Supports atomic coordinated persistence of observations alongside cursor advancement
 * within a single database transaction.
 */
interface AppUsageCheckpointStore {
    suspend fun getCheckpoint(userId: String): Result<AppUsageCheckpoint?, Exception>

    suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception>

    /**
     * Atomically persists the given [observations] and updates the [checkpoint].
     * If an error occurs, neither the observations nor the checkpoint are committed.
     */
    suspend fun saveObservationsWithCheckpoint(
        observations: List<Observation>,
        checkpoint: AppUsageCheckpoint
    ): Result<Unit, Exception>

    suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception>
}

