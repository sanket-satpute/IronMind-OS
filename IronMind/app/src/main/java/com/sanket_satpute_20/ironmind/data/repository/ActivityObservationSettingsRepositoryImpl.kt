package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.ActivityObservationSettingsDao
import com.sanket_satpute_20.ironmind.data.local.entity.ActivityObservationSettingsEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.common.Clock

class ActivityObservationSettingsRepositoryImpl(
    private val dao: ActivityObservationSettingsDao,
    private val clock: Clock
) : ActivityObservationSettingsRepository {

    override suspend fun getSettings(userId: String): Result<ActivityObservationSettings, Exception> {
        return try {
            val entity = dao.getSettings(userId)
            if (entity != null) {
                Result.Success(
                    ActivityObservationSettings(
                        userId = entity.userId,
                        isEnabled = entity.isEnabled,
                        lastUpdatedAt = entity.lastUpdatedAt
                    )
                )
            } else {
                // Default settings if not explicitly configured
                Result.Success(
                    ActivityObservationSettings(
                        userId = userId,
                        isEnabled = false,
                        lastUpdatedAt = clock.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
        return try {
            dao.insertOrUpdateSettings(
                ActivityObservationSettingsEntity(
                    userId = userId,
                    isEnabled = isEnabled,
                    lastUpdatedAt = clock.currentTimeMillis()
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
