package com.expo.modules.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expo.modules.alarm.receivers.AlarmReceiver
import expo.modules.alarm.ExpoAlarmModule

class AlarmService : Service() {
    companion object {
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val CHANNEL_NAME = "Alarm Service"
        private const val NOTIFICATION_ID = 1

        // Deterministic notification ID to avoid hashCode collisions
        fun notificationIdFor(identifier: String): Int {
            var hash = 1
            for (c in identifier) {
                hash = 31 * hash + c.code
            }
            return hash.coerceIn(0, Int.MAX_VALUE / 2)
        }
    }

    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_ALARM" -> {
                val identifier = intent.getStringExtra(AlarmReceiver.EXTRA_IDENTIFIER) ?: "default"
                val title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "Alarm"

                startVibration()

                val notification = createServiceNotification(title, identifier)
                startForeground(NOTIFICATION_ID, notification)

                // Notify React Native that alarm is firing
                expo.modules.alarm.ExpoAlarmModule.sendEvent("alarmTriggered", mapOf("identifier" to identifier))
            }
            "STOP_ALARM" -> {
                val identifier = intent.getStringExtra(AlarmReceiver.EXTRA_IDENTIFIER) ?: "default"
                stopAlarm(identifier)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
    }

    private fun startVibration() {
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0)
                )
            } else {
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    fun stopAlarm(identifier: String) {
        stopVibration()
        stopForeground(true)
        stopSelf()

        // Notify React Native
        expo.modules.alarm.ExpoAlarmModule.sendEvent("alarmDismissed", mapOf("identifier" to identifier))

        // Clear firing state and mark as dismissed to prevent duplicate re-trigger
        val prefs = getSharedPreferences("ExpoAlarmModule", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_alarm_firing", false)
            .remove("firing_alarm_id")
            .putString("dismissed_alarm_id", identifier)
            .putLong("dismissed_alarm_time", System.currentTimeMillis())
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Channel for alarm service notifications"
            }

            val notificationManager = getSystemService(NotificationManager::class.java) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(title: String, identifier: String): Notification {
        // PendingIntent to bring the app to foreground when notification is tapped
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("alarm_firing", true)
        }
        val pendingLaunch = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent to dismiss the alarm
        val dismissIntent = Intent(this, com.expo.modules.alarm.receivers.NotificationActionReceiver::class.java).apply {
            action = "DISMISS_ACTION"
            putExtra("identifier", identifier)
        }
        val pendingDismiss = PendingIntent.getBroadcast(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Alarm is ringing")
            .setSmallIcon(R.drawable.expo_alarm_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingLaunch)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                pendingDismiss
            )
            .build()
    }
}
