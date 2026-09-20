package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings

interface ActivityObservationSettingsRepository {
    suspend fun getSettings(userId: String): Result<ActivityObservationSettings, Exception>
    suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception>
}
