package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings

interface AppUsageObservationSettingsRepository {
    suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception>
    suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception>
}
