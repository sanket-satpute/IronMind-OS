package com.sanket_satpute_20.ironmind.data.provider

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler
import com.sanket_satpute_20.ironmind.receiver.ReminderReceiver

class AndroidReminderScheduler(
    private val context: Context
) : ReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleReminder(commitmentId: String, timeInMillis: Long, title: String) {
        // Only schedule if time is in the future
        if (timeInMillis <= System.currentTimeMillis()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("AndroidReminderScheduler", "Cannot schedule exact alarm: missing permission")
                return
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_COMMITMENT_ID, commitmentId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            commitmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            Log.i("IronMindLifecycle", "[Reminder] [SCHEDULED] commitmentId=$commitmentId time=$timeInMillis")
        } catch (e: SecurityException) {
            Log.e("AndroidReminderScheduler", "SecurityException when scheduling exact alarm", e)
        }
    }

    override fun cancelReminder(commitmentId: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            commitmentId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i("IronMindLifecycle", "[Reminder] [CANCELLED] commitmentId=$commitmentId")
        }
    }
}
