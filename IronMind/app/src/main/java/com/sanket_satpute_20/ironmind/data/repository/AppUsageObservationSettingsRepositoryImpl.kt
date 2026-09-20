package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationSettingsDao
import com.sanket_satpute_20.ironmind.data.local.entity.AppUsageObservationSettingsEntity
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppUsageObservationSettingsRepositoryImpl(
    private val dao: AppUsageObservationSettingsDao,
    private val clock: Clock
) : AppUsageObservationSettingsRepository {

    override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> =
        withContext(Dispatchers.IO) {
            try {
                val entity = dao.getSettingsForUser(userId)
                Result.Success(
                    AppUsageObservationSettings(
                        userId = userId,
                        isEnabled = entity?.isEnabled ?: false
                    )
                )
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }

    override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> =
        withContext(Dispatchers.IO) {
            try {
                dao.upsertSettings(
                    AppUsageObservationSettingsEntity(
                        userId = userId,
                        isEnabled = isEnabled,
                        updatedAt = clock.currentTimeMillis()
                    )
                )
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }
}
