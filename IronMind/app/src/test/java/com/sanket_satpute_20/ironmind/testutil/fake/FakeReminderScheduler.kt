package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler

class FakeReminderScheduler : ReminderScheduler {
    val scheduledReminders = mutableMapOf<String, Pair<Long, String>>()
    val cancelledReminders = mutableListOf<String>()

    override fun scheduleReminder(commitmentId: String, timeInMillis: Long, title: String) {
        scheduledReminders[commitmentId] = Pair(timeInMillis, title)
    }

    override fun cancelReminder(commitmentId: String) {
        scheduledReminders.remove(commitmentId)
        cancelledReminders.add(commitmentId)
    }
}
