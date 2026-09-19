package com.sanket_satpute_20.ironmind.domain.provider

interface ReminderScheduler {
    fun scheduleReminder(commitmentId: String, timeInMillis: Long, title: String)
    fun cancelReminder(commitmentId: String)
}
