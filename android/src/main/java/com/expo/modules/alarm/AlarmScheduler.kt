package com.expo.modules.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.expo.modules.alarm.receivers.AlarmReceiver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Static alarm scheduling utilities that can be called from BootReceiver
 * without needing the Expo module instance.
 */
object AlarmScheduler {
    private lateinit var alarmManager: AlarmManager
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        sharedPreferences = context.getSharedPreferences("ExpoAlarmModule", Context.MODE_PRIVATE)
    }

    fun rescheduleAlarms(context: Context) {
        if (!::alarmManager.isInitialized) {
            init(context)
        }
        val alarms = getAllStoredAlarms()
        val currentTime = System.currentTimeMillis()

        for ((identifier, alarmInfo) in alarms) {
            val dateMillis = alarmInfo["date"] as? Long ?: continue
            val repeating = alarmInfo["repeating"] as? Boolean ?: false
            val repeatInterval = (alarmInfo["repeatInterval"] as? Double)?.toLong()
            val title = alarmInfo["title"] as? String ?: continue
            val body = alarmInfo["body"] as? String
            val sound = alarmInfo["sound"] as? String

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("identifier", identifier)
                putExtra("title", title)
                putExtra("body", body)
                putExtra("repeating", repeating)
                putExtra("repeatInterval", repeatInterval)
                putExtra("sound", sound)
            }

            val requestCode = identifier.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = if (dateMillis < currentTime) currentTime else dateMillis

            if (repeating && repeatInterval != null) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    repeatInterval,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }

    private fun getAllStoredAlarms(): Map<String, Map<String, Any?>> {
        val allEntries = sharedPreferences.all
        val alarms = mutableMapOf<String, Map<String, Any?>>()

        for ((key, value) in allEntries) {
            if (key.startsWith("alarm_") && value is String) {
                val identifier = key.removePrefix("alarm_")
                val type = object : TypeToken<Map<String, Any?>>() {}.type
                val alarmInfo: Map<String, Any?> = gson.fromJson(value, type)
                alarms[identifier] = alarmInfo
            }
        }

        return alarms
    }
}
