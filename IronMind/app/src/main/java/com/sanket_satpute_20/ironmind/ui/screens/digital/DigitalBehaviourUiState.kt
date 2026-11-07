package com.sanket_satpute_20.ironmind.ui.screens.digital

import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.digital.DailyAppUsageIntelligence
import java.time.LocalDate

data class DigitalBehaviourUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val dailyUsage: DailyAppUsageIntelligence? = null,
    val isUsageAccessPermissionGranted: Boolean = false,
    val isUsageObservationEnabled: Boolean = true,
    val activeProtectionSessions: List<ProtectionSession> = emptyList(),
    val errorMessage: String? = null
) {
    val effectiveAvailability: SourceAvailability
        get() = when {
            !isUsageAccessPermissionGranted -> SourceAvailability.PERMISSION_DENIED
            !isUsageObservationEnabled -> SourceAvailability.PERMISSION_NOT_REQUESTED
            dailyUsage != null -> dailyUsage.availability
            else -> SourceAvailability.EMPTY_NO_DATA
        }
}
