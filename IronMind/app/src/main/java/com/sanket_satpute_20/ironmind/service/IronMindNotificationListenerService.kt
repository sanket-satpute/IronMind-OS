package com.sanket_satpute_20.ironmind.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sanket_satpute_20.ironmind.IronMindApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IronMindNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // In a real app this would come from an Auth framework.
    // Here we hardcode "user-1" as per the existing IronMind architecture.
    private val currentUserId = "user-1"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val container = (application as? IronMindApplication)?.container ?: return
        val useCase = container.handleIncomingNotificationUseCase
        
        serviceScope.launch {
            useCase(
                userId = currentUserId,
                packageName = sbn.packageName,
                isClearable = sbn.isClearable,
                timestamp = sbn.postTime
            )
        }
    }
}
