package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository

class GetCalendarObservationSettingsUseCase(
    private val repository: CalendarObservationSettingsRepository
) {
    suspend operator fun invoke(userId: String): Result<CalendarObservationSettings, Exception> {
        return repository.getSettings(userId)
    }
}
