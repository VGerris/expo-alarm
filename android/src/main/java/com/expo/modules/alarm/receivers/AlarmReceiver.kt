package com.expo.modules.alarm.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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

        // Ignore if this alarm was already dismissed
        val prefs = context.getSharedPreferences("ExpoAlarmModule", Context.MODE_PRIVATE)
        val dismissedId = prefs.getString("dismissed_alarm_id", null)
        val dismissedAt = prefs.getLong("dismissed_alarm_time", 0)
        if (dismissedId == identifier && System.currentTimeMillis() - dismissedAt < 3000) {
            // Same alarm dismissed within 3 seconds — ignore duplicate delivery
            return
        }

        // Emit alarmTriggered event to React Native
        expo.modules.alarm.ExpoAlarmModule.sendEvent("alarmTriggered", mapOf(
            "identifier" to identifier,
            "title" to title,
            "body" to body
        ))

        // Persist firing state so React Native can detect it on resume
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

        // Notification is handled by AlarmService (foreground service with dismiss action)
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

    // AlarmActivity is no longer launched — RN AlarmModal handles the UI
}