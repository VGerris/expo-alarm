package com.expo.modules.alarm.receivers

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.expo.modules.alarm.AlarmService

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val identifier = intent.getStringExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_IDENTIFIER) ?: return
        val title = intent.getStringExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_TITLE) ?: "Alarm"
        val body = intent.getStringExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_BODY)
        val action = intent.action

        when (action) {
            DISMISS_ACTION -> {
                dismissAlarm(context, identifier)
            }
            SNOOZE_ACTION -> {
                snoozeAlarm(context, identifier, title, body, 5 * 60 * 1000L) // 5 minutes
            }
        }
    }

    private fun dismissAlarm(context: Context, identifier: String) {
        // Cancel the notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(AlarmService.notificationIdFor(identifier))

        // Stop the alarm service if running
        val serviceIntent = Intent(context, com.expo.modules.alarm.AlarmService::class.java).apply {
            action = "STOP_ALARM"
            putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_IDENTIFIER, identifier)
        }
        context.startService(serviceIntent)

        // Cancel the alarm in AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cancelIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_IDENTIFIER, identifier)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                identifier.hashCode(),
                cancelIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun snoozeAlarm(context: Context, identifier: String, title: String, body: String?, snoozeMillis: Long) {
        // Cancel the notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(AlarmService.notificationIdFor(identifier))

        // Stop the current alarm service
        val serviceIntent = Intent(context, com.expo.modules.alarm.AlarmService::class.java).apply {
            action = "STOP_ALARM"
            putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_IDENTIFIER, identifier)
        }
        context.startService(serviceIntent)

        // Reschedule alarm for later
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val newDate = System.currentTimeMillis() + snoozeMillis

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_IDENTIFIER, identifier)
                putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_TITLE, title)
                putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_BODY, body)
                putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_REPEATING, false)
                putExtra(com.expo.modules.alarm.receivers.AlarmReceiver.EXTRA_REPEAT_INTERVAL, 0L)
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                identifier.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    newDate,
                    pendingIntent
                )
            }
        }
    }

    companion object {
        const val DISMISS_ACTION = "DISMISS_ACTION"
        const val SNOOZE_ACTION = "SNOOZE_ACTION"
    }
}