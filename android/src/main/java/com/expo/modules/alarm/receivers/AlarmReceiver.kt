package com.expo.modules.alarm.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "alarm_channel"
        private const val CHANNEL_NAME = "Alarms"
        private const val CHANNEL_DESCRIPTION = "Notifications for scheduled alarms"
        const val EXTRA_IDENTIFIER = "identifier"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_SOUND = "sound"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val identifier = intent.getStringExtra("identifier") ?: return
        val title = intent.getStringExtra("title") ?: "Alarm"
        val body = intent.getStringExtra("body")
        val sound = intent.getStringExtra("sound")

        createNotificationChannel(context)

        // Start the alarm service to play sound in background
        val serviceIntent = Intent(context, com.expo.modules.alarm.AlarmService::class.java).apply {
            action = "START_ALARM"
            putExtra(EXTRA_IDENTIFIER, identifier)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_SOUND, sound)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Launch AlarmActivity to show alarm UI over lock screen
        val activityIntent = Intent(context, com.expo.modules.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("identifier", identifier)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("sound", sound)
        }
        context.startActivity(activityIntent)

        // Also show a notification as a fallback
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(identifier.hashCode(), notificationBuilder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}