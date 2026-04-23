package com.expo.modules.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.expo.modules.alarm.receivers.AlarmReceiver
import expo.modules.alarm.ExpoAlarmModule

class AlarmService : Service() {
    companion object {
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val CHANNEL_NAME = "Alarm Service"
        private const val NOTIFICATION_ID = 1
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

                val notification = createServiceNotification(title, identifier)
                startForeground(NOTIFICATION_ID, notification)

                startVibration()

                // Notify React Native that alarm is firing
                android.util.Log.d("ExpoAlarm", "Service START_ALARM: about to send alarmTriggered for $identifier")
                expo.modules.alarm.ExpoAlarmModule.sendEvent("alarmTriggered", mapOf("identifier" to identifier))
            }
            "STOP_ALARM" -> {
                val identifier = intent.getStringExtra("identifier") ?: "default"
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

        // Send broadcast to AlarmActivity that dismiss is confirmed
        val broadcastIntent = Intent("com.expo.modules.alarm.DISMISS_CONFIRMED").apply {
            putExtra("identifier", identifier)
        }
        sendBroadcast(broadcastIntent)

        // Notify React Native
        expo.modules.alarm.ExpoAlarmModule.sendEvent("alarmDismissed", mapOf("identifier" to identifier))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Channel for alarm service notifications"
                enableLights(true)
                enableVibration(true)
                lightColor = 0xFF007AFF.toInt()
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
