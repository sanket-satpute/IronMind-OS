package com.sanket_satpute_20.ironmind.domain.usecase.observation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val mutex = Mutex()

    suspend fun handle(
        event: ObservationSchedulingLifecycleEvent
    ): ObservationSchedulingReconciliationResult {
        return mutex.withLock {
            val eventName = when (event) {
                ObservationSchedulingLifecycleEvent.ApplicationStarted -> "APPLICATION_STARTED"
                ObservationSchedulingLifecycleEvent.AuthenticationAvailable -> "AUTHENTICATION_AVAILABLE"
                ObservationSchedulingLifecycleEvent.AuthenticationUnavailable -> "AUTHENTICATION_UNAVAILABLE"
                ObservationSchedulingLifecycleEvent.ObservationConsentChanged -> "OBSERVATION_CONSENT_CHANGED"
                ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged -> "GLOBAL_AUTONOMY_PAUSE_CHANGED"
            }

            println("IronMindLifecycle [ObservationSchedulingCoordinator] [RECONCILE] event=$eventName")

            reconcileObservationSchedulingUseCase()
        }
    }
}
