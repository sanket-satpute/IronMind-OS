package com.sanket_satpute_20.ironmind

import android.app.Application
import androidx.work.Configuration
import com.sanket_satpute_20.ironmind.di.AppContainer
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer
import com.sanket_satpute_20.ironmind.infrastructure.worker.IronMindWorkerFactory
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class IronMindApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        val adapter = com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingAppLifecycleAdapter(
            coordinator = container.observationSchedulingCoordinator,
            authRepository = container.authRepository,
            applicationScope = applicationScope
        )
        adapter.start()

        registerStartupReconciliation(container.backgroundExecutor)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(IronMindWorkerFactory(container))
            .build()

    companion object {
        fun registerStartupReconciliation(backgroundExecutor: com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor) {
            try {
                backgroundExecutor.scheduleEventReconciliation()
                println("IronMindLifecycle [EventReconciliation] [SCHEDULED]")
            } catch (e: Exception) {
                println("IronMindLifecycle [EventReconciliation] [SCHEDULE_FAILED] error=${e.message ?: "UNKNOWN"}")
            }
        }
    }
}
