package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.CalendarObservationSettingsDao
import com.sanket_satpute_20.ironmind.data.local.entity.CalendarObservationSettingsEntity
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository

class CalendarObservationSettingsRepositoryImpl(
    private val dao: CalendarObservationSettingsDao,
    private val clock: Clock
) : CalendarObservationSettingsRepository {

    override suspend fun getSettings(userId: String): Result<CalendarObservationSettings, Exception> {
        return try {
            val entity = dao.getSettings(userId)
            val settings = if (entity != null) {
                CalendarObservationSettings(
                    userId = entity.userId,
                    isEnabled = entity.isEnabled,
                    updatedAt = entity.updatedAt
                )
            } else {
                CalendarObservationSettings(
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

    override suspend fun updateSettings(settings: CalendarObservationSettings): Result<Unit, Exception> {
        return try {
            val entity = CalendarObservationSettingsEntity(
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
