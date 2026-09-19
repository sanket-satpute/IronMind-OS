package com.sanket_satpute_20.ironmind

import android.app.Application
import com.sanket_satpute_20.ironmind.di.AppContainer
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer

class IronMindApplication : Application() {
    
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
