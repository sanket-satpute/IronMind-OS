package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository

sealed class ObservationSchedulingPolicyResult {
    object Allowed : ObservationSchedulingPolicyResult()
    data class Denied(val reason: Reason) : ObservationSchedulingPolicyResult() {
        enum class Reason {
            DENIED_AUTH,
            DENIED_USER_SETTING,
            DENIED_CONSENT
        }
    }
}

class ObservationSchedulingPolicyUseCase(
    private val authRepository: AuthRepository,
    private val autonomySettingsRepository: AutonomySettingsRepository,
    private val appUsageSettingsRepository: AppUsageObservationSettingsRepository,
    private val activitySettingsRepository: ActivityObservationSettingsRepository,
    private val calendarSettingsRepository: CalendarObservationSettingsRepository,
    private val locationSettingsRepository: LocationObservationSettingsRepository
) {
    suspend operator fun invoke(): ObservationSchedulingPolicyResult {
        println("IronMindLifecycle ObservationPolicy [EVALUATED]")
        
        val user = authRepository.getCurrentUser()
        if (user == null) {
            println("IronMindLifecycle ObservationPolicy [DENIED] reason=DENIED_AUTH")
            return ObservationSchedulingPolicyResult.Denied(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH)
        }

        val autonomyResult = autonomySettingsRepository.getSettings(user.id)
        if (autonomyResult is Result.Failure) {
            // Fail closed on unknown/unavailable required state
            println("IronMindLifecycle ObservationPolicy [DENIED] reason=DENIED_USER_SETTING (unknown state)")
            return ObservationSchedulingPolicyResult.Denied(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING)
        }
        if (autonomyResult is Result.Success && autonomyResult.data.isGlobalPauseActive) {
            println("IronMindLifecycle ObservationPolicy [DENIED] reason=DENIED_USER_SETTING (global pause active)")
            return ObservationSchedulingPolicyResult.Denied(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING)
        }

        // We check if at least ONE observation type is enabled.
        // If ALL are disabled (or missing/error), we DENY scheduling to save resources and respect privacy.
        // Note: We intentionally do NOT check OS permissions here. That is the responsibility of the provider during actual execution.
        
        var isAnyEnabled = false
        
        val appUsageResult = appUsageSettingsRepository.getSettings(user.id)
        if (appUsageResult is Result.Success && appUsageResult.data.isEnabled) isAnyEnabled = true

        val activityResult = activitySettingsRepository.getSettings(user.id)
        if (activityResult is Result.Success && activityResult.data.isEnabled) isAnyEnabled = true

        val calendarResult = calendarSettingsRepository.getSettings(user.id)
        if (calendarResult is Result.Success && calendarResult.data.isEnabled) isAnyEnabled = true

        val locationResult = locationSettingsRepository.getSettings(user.id)
        if (locationResult is Result.Success && locationResult.data.isEnabled) isAnyEnabled = true

        if (!isAnyEnabled) {
            println("IronMindLifecycle ObservationPolicy [DENIED] reason=DENIED_CONSENT")
            return ObservationSchedulingPolicyResult.Denied(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT)
        }

        println("IronMindLifecycle ObservationPolicy [ALLOWED]")
        return ObservationSchedulingPolicyResult.Allowed
    }
}
