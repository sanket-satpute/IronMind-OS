package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler

sealed class ObservationSchedulingReconciliationResult {
    object Activated : ObservationSchedulingReconciliationResult()
    data class Cancelled(
        val reason: ObservationSchedulingPolicyResult.Denied.Reason
    ) : ObservationSchedulingReconciliationResult()
}

class ReconcileObservationSchedulingUseCase(
    private val policyUseCase: ObservationSchedulingPolicyUseCase,
    private val activateUseCase: ActivateObservationSchedulingUseCase,
    private val observationScheduler: ObservationScheduler
) {
    suspend operator fun invoke(): ObservationSchedulingReconciliationResult {
        return when (val policyResult = policyUseCase()) {
            is ObservationSchedulingPolicyResult.Allowed -> {
                activateUseCase()
                println("IronMindLifecycle [ObservationSchedulingReconciliation] [ACTIVATED]")
                ObservationSchedulingReconciliationResult.Activated
            }
            is ObservationSchedulingPolicyResult.Denied -> {
                observationScheduler.cancelObservationCollection()
                println("IronMindLifecycle [ObservationSchedulingReconciliation] [CANCELLED] reason=${policyResult.reason.name}")
                ObservationSchedulingReconciliationResult.Cancelled(policyResult.reason)
            }
        }
    }
}
