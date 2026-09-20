package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationCandidate

interface NotificationProvider {
    fun showReminderNotification(id: String, title: String, message: String)
    fun showNotification(candidate: NotificationCandidate)
}
