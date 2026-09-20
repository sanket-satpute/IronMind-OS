package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.LocationObservationSettingsDao
import com.sanket_satpute_20.ironmind.data.local.entity.LocationObservationSettingsEntity
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository

class LocationObservationSettingsRepositoryImpl(
    private val dao: LocationObservationSettingsDao,
    private val clock: Clock
) : LocationObservationSettingsRepository {

    override suspend fun getSettings(userId: String): Result<LocationObservationSettings, Exception> {
        return try {
            val entity = dao.getSettings(userId)
            val settings = if (entity != null) {
                LocationObservationSettings(
                    userId = entity.userId,
                    isEnabled = entity.isEnabled,
                    updatedAt = entity.updatedAt
                )
            } else {
                LocationObservationSettings(
                    userId = userId,
                    isEnabled = false,
                    updatedAt = clock.currentTimeMillis()
                )
            }
            Result.Success(settings)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateSettings(settings: LocationObservationSettings): Result<Unit, Exception> {
        return try {
            val entity = LocationObservationSettingsEntity(
                userId = settings.userId,
                isEnabled = settings.isEnabled,
                updatedAt = clock.currentTimeMillis()
            )
            dao.insertOrUpdate(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
