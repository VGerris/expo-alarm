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
import expo.modules.alarm.ExpoAlarmModule

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "alarm_channel"
        private const val CHANNEL_NAME = "Alarms"
        private const val CHANNEL_DESCRIPTION = "Notifications for scheduled alarms"
        const val EXTRA_IDENTIFIER = "identifier"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_SOUND = "sound"
        const val EXTRA_REPEATING = "repeating"
        const val EXTRA_REPEAT_INTERVAL = "repeatInterval"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val identifier = intent.getStringExtra(EXTRA_IDENTIFIER) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Alarm"
        val body = intent.getStringExtra(EXTRA_BODY)
        val sound = intent.getStringExtra(EXTRA_SOUND)

        // Emit alarmTriggered event to React Native
        ExpoAlarmModule.instance?.emit("alarmTriggered", mapOf(
            "identifier" to identifier,
            "title" to title,
            "body" to body
        ))

        // Persist firing state so React Native can detect it on resume
        val prefs = context.getSharedPreferences("ExpoAlarmModule", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_alarm_firing", true).putString("firing_alarm_id", identifier).apply()

        createNotificationChannel(context)

        // Start the alarm service to play sound in background
        val serviceIntent = Intent(context, com.expo.modules.alarm.AlarmService::class.java).apply {
            action = "START_ALARM"
            putExtra(EXTRA_IDENTIFIER, identifier)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_SOUND, sound)
            putExtra(EXTRA_REPEATING, intent.getBooleanExtra(EXTRA_REPEATING, false))
            putExtra(EXTRA_REPEAT_INTERVAL, intent.getLongExtra(EXTRA_REPEAT_INTERVAL, 0L))
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
            .setSmallIcon(R.drawable.expo_alarm_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(createOpenActivityPendingIntent(context, identifier, title, body))

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(com.expo.modules.alarm.AlarmService.notificationIdFor(identifier), notificationBuilder.build())
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

    private fun createOpenActivityPendingIntent(
        context: Context,
        identifier: String,
        title: String,
        body: String?
    ): PendingIntent? {
        val activityIntent = Intent(context, com.expo.modules.alarm.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_IDENTIFIER, identifier)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        return PendingIntent.getActivity(
            context,
            identifier.hashCode(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}