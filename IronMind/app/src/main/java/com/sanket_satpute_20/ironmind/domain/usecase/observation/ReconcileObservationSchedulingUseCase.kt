package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler

sealed class ObservationSchedulingReconciliationResult {
    object Activated : ObservationSchedulingReconciliationResult()
    data class Cancelled(
        val reason: ObservationSchedulingPolicyResult.Denied.Reason
    ) : ObservationSchedulingReconciliationResult()
    object Failed : ObservationSchedulingReconciliationResult()
}

class ReconcileObservationSchedulingUseCase(
    private val activateUseCase: ActivateObservationSchedulingUseCase,
    private val observationScheduler: ObservationScheduler
) {
    suspend operator fun invoke(): ObservationSchedulingReconciliationResult {
        return when (val activationResult = activateUseCase()) {
            is ObservationSchedulingActivationResult.Activated -> {
                println("IronMindLifecycle [ObservationSchedulingReconciliation] [ACTIVATED]")
                ObservationSchedulingReconciliationResult.Activated
            }
            is ObservationSchedulingActivationResult.Denied -> {
                when (observationScheduler.cancelObservationCollection()) {
                    is com.sanket_satpute_20.ironmind.domain.common.Result.Success -> {
                        println("IronMindLifecycle [ObservationSchedulingReconciliation] [CANCELLED] reason=${activationResult.reason.name}")
                        ObservationSchedulingReconciliationResult.Cancelled(activationResult.reason)
                    }
                    is com.sanket_satpute_20.ironmind.domain.common.Result.Failure -> {
                        ObservationSchedulingReconciliationResult.Failed
                    }
                }
            }
            is ObservationSchedulingActivationResult.ScheduleFailed -> {
                ObservationSchedulingReconciliationResult.Failed
            }
        }
    }
}
