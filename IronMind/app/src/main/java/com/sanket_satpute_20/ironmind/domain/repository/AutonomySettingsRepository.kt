package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import kotlinx.coroutines.flow.Flow

interface AutonomySettingsRepository {
    suspend fun getSettings(userId: String): Result<AutonomySettings, Exception>
    suspend fun updateLevel(userId: String, capability: AutonomyCapability, level: AutonomyLevel): Result<Unit, Exception>
    suspend fun setGlobalPause(userId: String, isPaused: Boolean): Result<Unit, Exception>
}
