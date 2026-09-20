package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.provider.NotificationProvider

import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationCandidate

class FakeNotificationProvider : NotificationProvider {
    val shownReminders = mutableListOf<Triple<String, String, String>>()
    val shownNotifications = mutableListOf<NotificationCandidate>()

    override fun showReminderNotification(id: String, title: String, message: String) {
        shownReminders.add(Triple(id, title, message))
    }

    override fun showNotification(candidate: NotificationCandidate) {
        shownNotifications.add(candidate)
    }
}
