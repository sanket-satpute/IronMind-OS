package com.sanket_satpute_20.ironmind.domain.usecase.observation

sealed interface ObservationSchedulingLifecycleEvent {
    object ApplicationStarted : ObservationSchedulingLifecycleEvent
    object AuthenticationAvailable : ObservationSchedulingLifecycleEvent
    object AuthenticationUnavailable : ObservationSchedulingLifecycleEvent
    object ObservationConsentChanged : ObservationSchedulingLifecycleEvent
    object GlobalAutonomyPauseChanged : ObservationSchedulingLifecycleEvent
}

class ObservationSchedulingCoordinator(
    private val reconcileObservationSchedulingUseCase: ReconcileObservationSchedulingUseCase
) {
    suspend fun handle(
        event: ObservationSchedulingLifecycleEvent
    ): ObservationSchedulingReconciliationResult {
        val eventName = when (event) {
            ObservationSchedulingLifecycleEvent.ApplicationStarted -> "APPLICATION_STARTED"
            ObservationSchedulingLifecycleEvent.AuthenticationAvailable -> "AUTHENTICATION_AVAILABLE"
            ObservationSchedulingLifecycleEvent.AuthenticationUnavailable -> "AUTHENTICATION_UNAVAILABLE"
            ObservationSchedulingLifecycleEvent.ObservationConsentChanged -> "OBSERVATION_CONSENT_CHANGED"
            ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged -> "GLOBAL_AUTONOMY_PAUSE_CHANGED"
        }
        
        println("IronMindLifecycle [ObservationSchedulingCoordinator] [RECONCILE] event=$eventName")
        
        return reconcileObservationSchedulingUseCase()
    }
}
