package com.sanket_satpute_20.ironmind.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sanket_satpute_20.ironmind.IronMindApplication

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? IronMindApplication
        val container = application?.container
        val provider = container?.notificationProvider

        val commitmentId = intent.getStringExtra(EXTRA_COMMITMENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = "It's time for your commitment."

        Log.i("IronMindLifecycle", "[Reminder] [DELIVERED] commitmentId=$commitmentId title=\"$title\"")
        
        if (provider != null) {
            provider.showReminderNotification(commitmentId, title, message)
        } else {
            Log.e("ReminderReceiver", "NotificationProvider not found in DI container")
        }
    }

    companion object {
        const val EXTRA_COMMITMENT_ID = "extra_commitment_id"
        const val EXTRA_TITLE = "extra_title"
    }
}
