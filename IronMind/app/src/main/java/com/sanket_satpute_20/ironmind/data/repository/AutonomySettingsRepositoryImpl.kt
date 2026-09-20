package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.AutonomySettingsDao
import com.sanket_satpute_20.ironmind.data.local.dao.GlobalAutonomyStateDao
import com.sanket_satpute_20.ironmind.data.local.entity.AutonomySettingsEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GlobalAutonomyStateEntity
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutonomySettingsRepositoryImpl(
    private val dao: AutonomySettingsDao,
    private val globalStateDao: GlobalAutonomyStateDao,
    private val clock: Clock
) : AutonomySettingsRepository {

    override suspend fun getSettings(userId: String): Result<AutonomySettings, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getSettingsForUser(userId)
            val globalState = globalStateDao.getStateForUser(userId)
            
            val map = entities.associate { 
                AutonomyCapability.valueOf(it.capability) to AutonomyLevel.valueOf(it.level) 
            }
            Result.Success(
                AutonomySettings(
                    userId = userId,
                    levels = map,
                    isGlobalPauseActive = globalState?.isGlobalPauseActive ?: false
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateLevel(
        userId: String,
        capability: AutonomyCapability,
        level: AutonomyLevel
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = AutonomySettingsEntity(
                userId = userId,
                capability = capability.name,
                level = level.name,
                updatedAt = clock.currentTimeMillis()
            )
            dao.upsertSetting(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun setGlobalPause(userId: String, isPaused: Boolean): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = GlobalAutonomyStateEntity(
                userId = userId,
                isGlobalPauseActive = isPaused,
                updatedAt = clock.currentTimeMillis()
            )
            globalStateDao.upsertState(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
