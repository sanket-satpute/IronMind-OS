package com.sanket_satpute_20.ironmind

import android.app.Application
import androidx.work.Configuration
import com.sanket_satpute_20.ironmind.di.AppContainer
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer
import com.sanket_satpute_20.ironmind.infrastructure.worker.IronMindWorkerFactory

class IronMindApplication : Application(), Configuration.Provider {
    
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(IronMindWorkerFactory(container))
            .build()
}
