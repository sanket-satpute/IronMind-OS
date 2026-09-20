package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings

interface CalendarObservationSettingsRepository {
    suspend fun getSettings(userId: String): Result<CalendarObservationSettings, Exception>
    suspend fun updateSettings(settings: CalendarObservationSettings): Result<Unit, Exception>
}
