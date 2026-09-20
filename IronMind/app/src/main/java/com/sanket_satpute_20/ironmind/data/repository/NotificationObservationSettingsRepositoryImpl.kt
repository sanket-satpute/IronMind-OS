package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.NotificationObservationSettingsDao
import com.sanket_satpute_20.ironmind.data.local.entity.NotificationObservationSettingsEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.NotificationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.NotificationObservationSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sanket_satpute_20.ironmind.domain.common.Clock

class NotificationObservationSettingsRepositoryImpl(
    private val dao: NotificationObservationSettingsDao,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : NotificationObservationSettingsRepository {

    override suspend fun getSettings(userId: String): Result<NotificationObservationSettings, Exception> = withContext(dispatcher) {
        try {
            val entity = dao.getSettings(userId)
            if (entity != null) {
                Result.Success(
                    NotificationObservationSettings(
                        userId = entity.userId,
                        isEnabled = entity.isEnabled
                    )
                )
            } else {
                // Default is disabled
                Result.Success(
                    NotificationObservationSettings(
                        userId = userId,
                        isEnabled = false
                    )
                )
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> = withContext(dispatcher) {
        try {
            val entity = NotificationObservationSettingsEntity(
                userId = userId,
                isEnabled = isEnabled,
                updatedAt = clock.currentTimeMillis()
            )
            dao.upsertSettings(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
