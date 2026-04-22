package com.expo.modules.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expo.modules.alarm.receivers.AlarmReceiver

class AlarmService : Service() {
    companion object {
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val CHANNEL_NAME = "Alarm Service"
        private const val NOTIFICATION_ID = 1
    }

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_ALARM" -> {
                val identifier = intent.getStringExtra(AlarmReceiver.EXTRA_IDENTIFIER) ?: "default"
                val title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "Alarm"
                val soundUri = intent.getStringExtra(AlarmReceiver.EXTRA_SOUND)

                acquireWakeLock()

                val notification = createServiceNotification(title)
                startForeground(NOTIFICATION_ID, notification)

                playAlarmSound(soundUri)
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
        stopAlarmSound()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ExpoAlarm:AlarmWakeLock"
        )
        wakeLock?.acquire(5 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun playAlarmSound(soundUri: String?) {
        try {
            mediaPlayer = MediaPlayer().apply {
                if (!soundUri.isNullOrEmpty()) {
                    setDataSource(applicationContext, android.net.Uri.parse(soundUri))
                } else {
                    val alarmAlert = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                    setDataSource(applicationContext, alarmAlert)
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)

                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                mediaPlayer = MediaPlayer().apply {
                    val alarmAlert = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                    setDataSource(applicationContext, alarmAlert)
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setAudioAttributes(audioAttributes)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
    }

    fun stopAlarm(identifier: String) {
        stopAlarmSound()
        stopForeground(true)
        stopSelf()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(notificationIdFor(identifier))
    }

    companion object {
        // Deterministic notification ID to avoid hashCode collisions
        fun notificationIdFor(identifier: String): Int {
            var hash = 1
            for (c in identifier) {
                hash = 31 * hash + c.code
            }
            return hash.coerceIn(0, Int.MAX_VALUE / 2)
        }
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

    private fun createServiceNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Alarm is ringing")
            .setSmallIcon(R.drawable.expo_alarm_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}