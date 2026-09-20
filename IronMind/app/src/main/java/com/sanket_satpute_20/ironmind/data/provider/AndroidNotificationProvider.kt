package com.sanket_satpute_20.ironmind.data.provider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sanket_satpute_20.ironmind.domain.provider.NotificationProvider
import com.sanket_satpute_20.ironmind.MainActivity
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationCandidate
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationPriority

class AndroidNotificationProvider(
    private val context: Context
) : NotificationProvider {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "ironmind_reminders"

    init {
        createNotificationChannel()
    }

    override fun showReminderNotification(id: String, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            // .setSmallIcon(R.mipmap.ic_launcher) // Would need to import R, let's use a standard icon or fallback to application info
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id.hashCode(), notification)
    }

    override fun showNotification(candidate: NotificationCandidate) {
        // Ensure channel exists (you could map channelId, but for now we fallback or create standard)
        createChannelIfNeeded(candidate.channelId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            candidate.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priorityLevel = when (candidate.priority) {
            NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            NotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        }

        val notification = NotificationCompat.Builder(context, candidate.channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(candidate.title)
            .setContentText(candidate.message)
            .setPriority(priorityLevel)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(candidate.id.hashCode(), notification)
    }

    private fun createChannelIfNeeded(id: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = if (id == channelId) "Commitment Reminders" else "IronMind Notifications"
            val descriptionText = "Notifications for IronMind"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(id, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotificationChannel() {
        createChannelIfNeeded(channelId)
    }
}
