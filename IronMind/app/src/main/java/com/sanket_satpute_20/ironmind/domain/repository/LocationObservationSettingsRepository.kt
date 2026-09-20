package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings

interface LocationObservationSettingsRepository {
    suspend fun getSettings(userId: String): Result<LocationObservationSettings, Exception>
    suspend fun updateSettings(settings: LocationObservationSettings): Result<Unit, Exception>
}
