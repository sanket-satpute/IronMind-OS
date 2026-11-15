package com.sanket_satpute_20.ironmind

import android.app.Application
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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
        initAppCheckSafely()
        container = DefaultAppContainer(this)

        val adapter = com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingAppLifecycleAdapter(
            coordinator = container.observationSchedulingCoordinator,
            authRepository = container.authRepository,
            applicationScope = applicationScope
        )
        adapter.start()

        if (!isRunningInTest()) {
            registerStartupReconciliation(container.backgroundExecutor)
        }
    }

    private fun isRunningInTest(): Boolean =
        android.os.Build.FINGERPRINT == "robolectric" || runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess

    private fun initAppCheckSafely() {
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            android.util.Log.i("IronMindAppCheck", "INITIALIZATION_SKIPPED: Firebase Emulators active (App Check disabled for isolated test environment).")
            return
        }
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                val appCheck = FirebaseAppCheck.getInstance()
                val providerFactory = if (BuildConfig.DEBUG) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
                appCheck.installAppCheckProviderFactory(providerFactory)
                val providerName = if (BuildConfig.DEBUG) "DebugAppCheckProvider" else "PlayIntegrityAppCheckProvider"
                android.util.Log.i("IronMindAppCheck", "INITIALIZED ($providerName)")
            } else {
                android.util.Log.w("IronMindAppCheck", "INITIALIZATION_SKIPPED: FirebaseApp.getApps() is empty")
            }
        } catch (e: Exception) {
            android.util.Log.e("IronMindAppCheck", "INITIALIZATION_FAILED reason=${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(IronMindWorkerFactory(container))
            .build()

    companion object {
        fun registerStartupReconciliation(backgroundExecutor: com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor) {
            try {
                backgroundExecutor.scheduleEventReconciliation()
                backgroundExecutor.schedulePatternMaintenance()
                backgroundExecutor.schedulePeriodicInterventionEvaluation()
                backgroundExecutor.scheduleInterventionEvaluation()
                println("IronMindLifecycle [EventReconciliation] [SCHEDULED]")
            } catch (e: Exception) {
                println("IronMindLifecycle [EventReconciliation] [SCHEDULE_FAILED] error=${e.message ?: "UNKNOWN"}")
            }
        }
    }
}
