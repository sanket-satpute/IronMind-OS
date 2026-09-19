package com.sanket_satpute_20.ironmind.domain.provider

interface NotificationProvider {
    fun showReminderNotification(id: String, title: String, message: String)
}
