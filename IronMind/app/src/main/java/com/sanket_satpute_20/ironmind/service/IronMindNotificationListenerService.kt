package com.sanket_satpute_20.ironmind.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sanket_satpute_20.ironmind.IronMindApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IronMindNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val container = (application as? IronMindApplication)?.container ?: return
        val useCase = container.handleIncomingNotificationUseCase
        val userSessionProvider = container.userSessionProvider

        val eventGeneration = userSessionProvider.getSessionGeneration()
        if (eventGeneration <= 0L) return

        serviceScope.launch {
            try {
                userSessionProvider.withUserSession(expectedGeneration = eventGeneration) { userId ->
                    useCase(
                        userId = userId,
                        packageName = sbn.packageName,
                        isClearable = sbn.isClearable,
                        timestamp = sbn.postTime
                    )
                }
            } catch (e: IllegalStateException) {
                // Session generation changed or invalidated between event receipt and execution; drop stale event safely
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
