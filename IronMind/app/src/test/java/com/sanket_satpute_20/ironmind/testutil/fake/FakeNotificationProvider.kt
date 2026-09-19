package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.provider.NotificationProvider

class FakeNotificationProvider : NotificationProvider {
    val shownNotifications = mutableListOf<Triple<String, String, String>>()

    override fun showReminderNotification(id: String, title: String, message: String) {
        shownNotifications.add(Triple(id, title, message))
    }
}
