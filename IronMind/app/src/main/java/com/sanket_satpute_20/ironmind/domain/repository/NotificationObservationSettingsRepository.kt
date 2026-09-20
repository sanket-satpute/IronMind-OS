package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.NotificationObservationSettings

interface NotificationObservationSettingsRepository {
    suspend fun getSettings(userId: String): Result<NotificationObservationSettings, Exception>
    suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception>
}

