package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ObservationSchedulingAppLifecycleAdapter(
    private val coordinator: ObservationSchedulingCoordinator,
    private val authRepository: AuthRepository,
    private val applicationScope: CoroutineScope
) {
    fun start() {
        applicationScope.launch {
            println("IronMindLifecycle [ObservationSchedulingLifecycle] [APPLICATION_STARTED]")
            coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        }
        
        applicationScope.launch {
            authRepository.currentUser
                .map { it != null }
                .distinctUntilChanged()
                .collect { isAvailable ->
                    if (isAvailable) {
                        println("IronMindLifecycle [ObservationSchedulingLifecycle] [AUTH_AVAILABLE]")
                        coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable)
                    } else {
                        println("IronMindLifecycle [ObservationSchedulingLifecycle] [AUTH_UNAVAILABLE]")
                        coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable)
                    }
                }
        }
    }
}
