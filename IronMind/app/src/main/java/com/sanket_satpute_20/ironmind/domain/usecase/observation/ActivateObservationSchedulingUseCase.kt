package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler

sealed class ObservationSchedulingActivationResult {
    object Activated : ObservationSchedulingActivationResult()
    data class Denied(
        val reason: ObservationSchedulingPolicyResult.Denied.Reason
    ) : ObservationSchedulingActivationResult()
    object ScheduleFailed : ObservationSchedulingActivationResult()
}

class ActivateObservationSchedulingUseCase(
    private val policyUseCase: ObservationSchedulingPolicyUseCase,
    private val observationScheduler: ObservationScheduler
) {
    suspend operator fun invoke(): ObservationSchedulingActivationResult {
        return when (val policyResult = policyUseCase()) {
            is ObservationSchedulingPolicyResult.Allowed -> {
                when (observationScheduler.scheduleObservationCollection()) {
                    is com.sanket_satpute_20.ironmind.domain.common.Result.Success -> {
                        println("IronMindLifecycle [ObservationSchedulingActivation] [ACTIVATED]")
                        ObservationSchedulingActivationResult.Activated
                    }
                    is com.sanket_satpute_20.ironmind.domain.common.Result.Failure -> {
                        ObservationSchedulingActivationResult.ScheduleFailed
                    }
                }
            }
            is ObservationSchedulingPolicyResult.Denied -> {
                println("IronMindLifecycle [ObservationSchedulingActivation] [DENIED] reason=${policyResult.reason.name}")
                ObservationSchedulingActivationResult.Denied(policyResult.reason)
            }
        }
    }
}
