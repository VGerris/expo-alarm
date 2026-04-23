package expo.modules.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.Exceptions
import com.expo.modules.alarm.receivers.AlarmReceiver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class ExpoAlarmModule : Module() {
  companion object {
    const val PREFS_KEY = "ExpoAlarmModule"
    const val FIRING_KEY = "is_alarm_firing"
    const val FIRING_ALARM_ID_KEY = "firing_alarm_id"

    @Volatile
    var instance: ExpoAlarmModule? = null

    @Volatile
    private var eventEmitter: expo.modules.kotlin.events.EventEmitter? = null

    fun sendEvent(eventName: String, body: Map<String, Any?>) {
      val emitter = eventEmitter ?: run {
        android.util.Log.e("ExpoAlarm", "sendEvent: eventEmitter is null!")
        return
      }
      try {
        emitter.emit(eventName, body)
      } catch (e: Exception) {
        android.util.Log.e("ExpoAlarm", "sendEvent: exception: ${e.message}", e)
      }
    }
  }

  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val alarmManager: AlarmManager
    get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  private val sharedPreferences: SharedPreferences
    get() = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

  private val gson = Gson()

  private fun setFiringState(isFiring: Boolean, alarmId: String? = null) {
    val editor = sharedPreferences.edit()
    editor.putBoolean(FIRING_KEY, isFiring)
    if (alarmId != null) {
      editor.putString(FIRING_ALARM_ID_KEY, alarmId)
    } else {
      editor.remove(FIRING_ALARM_ID_KEY)
    }
    editor.apply()
  }

  private fun isFiring(): Boolean {
    return sharedPreferences.getBoolean(FIRING_KEY, false)
  }

  private fun getFiringAlarmId(): String? {
    return sharedPreferences.getString(FIRING_ALARM_ID_KEY, null)
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoAlarm")

    Events("alarmTriggered", "alarmDismissed")

    OnCreate {
      instance = this@ExpoAlarmModule
      // Capture the JSI event emitter so we can use it from non-module code
      val emitter = appContext.eventEmitter(this@ExpoAlarmModule)
      eventEmitter = emitter
    }

    OnDestroy {
      instance = null
    }

    Function("isSupported") {
      true // Android always supports alarms
    }

    AsyncFunction("requestPermissionsAsync") { promise: Promise ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires SCHEDULE_EXACT_ALARM permission
        val hasPermission = alarmManager.canScheduleExactAlarms()
        promise.resolve(mapOf(
          "granted" to hasPermission,
          "canAskAgain" to !hasPermission
        ))
      } else {
        promise.resolve(mapOf(
          "granted" to true,
          "canAskAgain" to false
        ))
      }
    }

    AsyncFunction("getPermissionsAsync") { promise: Promise ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasPermission = alarmManager.canScheduleExactAlarms()
        promise.resolve(mapOf(
          "granted" to hasPermission,
          "canAskAgain" to !hasPermission
        ))
      } else {
        promise.resolve(mapOf(
          "granted" to true,
          "canAskAgain" to false
        ))
      }
    }

    AsyncFunction("scheduleAlarmAsync") { alarmData: Map<String, Any>, promise: Promise ->
      try {
        val identifier = alarmData["identifier"] as String
        val title = alarmData["title"] as String
        val body = alarmData["body"] as? String
        val dateMillis = (alarmData["date"] as Double).toLong()
        val repeating = alarmData["repeating"] as? Boolean ?: false
        val repeatInterval = (alarmData["repeatInterval"] as? Double)?.toLong()
        val sound = alarmData["sound"] as? String

        // Cancel existing alarm with same identifier
        cancelAlarmInternal(identifier)

        // Create alarm intent
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

        // Schedule alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
          throw Exception("Exact alarm permission not granted")
        }

        if (repeating && repeatInterval != null) {
          alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            dateMillis,
            repeatInterval,
            pendingIntent
          )
        } else {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            dateMillis,
            pendingIntent
          )
        }

        // Store alarm info
        val alarmInfo = mapOf(
          "identifier" to identifier,
          "title" to title,
          "body" to body,
          "date" to dateMillis,
          "repeating" to repeating,
          "repeatInterval" to repeatInterval,
          "sound" to sound,
          "enabled" to true
        )
        
        saveAlarmInfo(identifier, alarmInfo)
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ERR_ALARM_SCHEDULE", e.message, e)
      }
    }

    AsyncFunction("cancelAlarmAsync") { identifier: String, promise: Promise ->
      try {
        cancelAlarmInternal(identifier)
        // Also stop the AlarmService if it's currently firing
        val serviceIntent = Intent(context, com.expo.modules.alarm.AlarmService::class.java).apply {
          action = "STOP_ALARM"
          putExtra("identifier", identifier)
        }
        // Use startService so onStartCommand executes with STOP_ALARM action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(serviceIntent)
        } else {
          context.startService(serviceIntent)
        }
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ERR_ALARM_CANCEL", e.message, e)
      }
    }

    AsyncFunction("cancelAllAlarmsAsync") { promise: Promise ->
      try {
        val alarms = getAllStoredAlarms()
        for (alarmId in alarms.keys) {
          cancelAlarmInternal(alarmId)
        }
        clearAllAlarms()
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("ERR_ALARM_CANCEL_ALL", e.message, e)
      }
    }

    AsyncFunction("getAllAlarmsAsync") { promise: Promise ->
      try {
        val alarms = getAllStoredAlarms()
        val alarmList = alarms.values.toList()
        promise.resolve(alarmList)
      } catch (e: Exception) {
        promise.reject("ERR_ALARM_GET_ALL", e.message, e)
      }
    }

    AsyncFunction("getAlarmAsync") { identifier: String, promise: Promise ->
      try {
        val alarmInfo = getStoredAlarmInfo(identifier)
        promise.resolve(alarmInfo)
      } catch (e: Exception) {
        promise.reject("ERR_ALARM_GET", e.message, e)
      }
    }

    AsyncFunction("hasAlarmAsync") { identifier: String, promise: Promise ->
      try {
        val hasAlarm = getStoredAlarmInfo(identifier) != null
        promise.resolve(hasAlarm)
      } catch (e: Exception) {
        promise.reject("ERR_ALARM_HAS", e.message, e)
      }
    }
  }

  private fun cancelAlarmInternal(identifier: String) {
    val intent = Intent(context, AlarmReceiver::class.java)
    val requestCode = identifier.hashCode()
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    alarmManager.cancel(pendingIntent)
    removeStoredAlarmInfo(identifier)
  }

  private fun saveAlarmInfo(identifier: String, alarmInfo: Map<String, Any?>) {
    val editor = sharedPreferences.edit()
    val json = gson.toJson(alarmInfo)
    editor.putString("alarm_$identifier", json)
    editor.apply()
  }

  private fun getStoredAlarmInfo(identifier: String): Map<String, Any?>? {
    val json = sharedPreferences.getString("alarm_$identifier", null)
    return if (json != null) {
      val type = object : TypeToken<MutableMap<String, Any?>>() {}.type
      val alarmInfo: MutableMap<String, Any?> = gson.fromJson(json, type)
      
      // Fix Gson converting Long/Int to Double
      if (alarmInfo.containsKey("date") && alarmInfo["date"] is Double) {
        alarmInfo["date"] = (alarmInfo["date"] as Double).toLong()
      }
      if (alarmInfo.containsKey("repeatInterval") && alarmInfo["repeatInterval"] is Double) {
        alarmInfo["repeatInterval"] = (alarmInfo["repeatInterval"] as Double).toLong()
      }
      
      alarmInfo
    } else {
      null
    }
  }

  private fun removeStoredAlarmInfo(identifier: String) {
    val editor = sharedPreferences.edit()
    editor.remove("alarm_$identifier")
    editor.apply()
  }

  private fun getAllStoredAlarms(): Map<String, Map<String, Any?>> {
    val allEntries = sharedPreferences.all
    val alarms = mutableMapOf<String, Map<String, Any?>>()
    
    for ((key, value) in allEntries) {
      if (key.startsWith("alarm_") && value is String) {
        val identifier = key.removePrefix("alarm_")
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val alarmInfo: MutableMap<String, Any?> = gson.fromJson(value, type)
        
        // Ensure numeric types are Long for consistency with AlarmScheduler
        if (alarmInfo["date"] is Double) {
          alarmInfo["date"] = (alarmInfo["date"] as Double).toLong()
        }
        if (alarmInfo["repeatInterval"] is Double) {
          alarmInfo["repeatInterval"] = (alarmInfo["repeatInterval"] as? Double)?.toLong()
        }
        
        alarms[identifier] = alarmInfo
      }
    }
    
    return alarms
  }

  private fun clearAllAlarms() {
    val editor = sharedPreferences.edit()
    val allEntries = sharedPreferences.all

    for (key in allEntries.keys) {
      if (key.startsWith("alarm_")) {
        editor.remove(key)
      }
    }

    editor.apply()
  }
}
