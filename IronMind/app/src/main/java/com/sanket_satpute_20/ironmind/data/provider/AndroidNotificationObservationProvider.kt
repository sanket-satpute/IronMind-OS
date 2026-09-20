package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.sanket_satpute_20.ironmind.domain.provider.NotificationObservationProvider

class AndroidNotificationObservationProvider(
    private val context: Context
) : NotificationObservationProvider {

    override fun isPermissionGranted(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }
}
