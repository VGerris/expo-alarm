import ExpoModulesCore
import UserNotifications
import Foundation

#if canImport(AlarmKit)
import AlarmKit
#endif

public class ExpoAlarmModule: Module {
  private var storedAlarms: [String: [String: Any]] = [:]
  private let userDefaults = UserDefaults.standard
  private let alarmsKey = "ExpoAlarmModule_alarms"

  public func definition() {
    Name("ExpoAlarm")

    Events("alarmTriggered", "alarmDismissed")

    Function("isSupported") {
      if #available(iOS 16.0, *) {
        return true // AlarmKit available
      } else {
        return true // fallback to notifications
      }
    }

    AsyncFunction("requestPermissionsAsync") { (promise: Promise) in
      UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
        if let error = error {
          promise.reject("ERR_PERMISSIONS", error.localizedDescription, error)
          return
        }

        promise.resolve([
          "granted": granted,
          "canAskAgain": !granted
        ])
      }
    }

    AsyncFunction("getPermissionsAsync") { (promise: Promise) in
      UNUserNotificationCenter.current().getNotificationSettings { settings in
        let granted = settings.authorizationStatus == .authorized
        promise.resolve([
          "granted": granted,
          "canAskAgain": settings.authorizationStatus != .denied
        ])
      }
    }

    AsyncFunction("scheduleAlarmAsync") { (alarmData: [String: Any], promise: Promise) in
      do {
        guard let identifier = alarmData["identifier"] as? String,
              let title = alarmData["title"] as? String,
              let dateMillis = alarmData["date"] as? Double else {
          promise.reject("ERR_INVALID_ARGS", "Missing required fields", nil)
          return
        }

        let body = alarmData["body"] as? String
        let repeating = alarmData["repeating"] as? Bool ?? false
        let repeatInterval = alarmData["repeatInterval"] as? Double
        let sound = alarmData["sound"] as? String

        let date = Date(timeIntervalSince1970: dateMillis / 1000)

        // Cancel existing alarm with same identifier
        await self.cancelAlarmInternal(identifier: identifier)

        #if canImport(AlarmKit)
        if #available(iOS 16.0, *) {
          // Use AlarmKit for iOS 16+
          try await self.scheduleWithAlarmKit(
            identifier: identifier,
            title: title,
            body: body,
            date: date,
            repeating: repeating,
            repeatInterval: repeatInterval,
            sound: sound
          )
        } else {
          // Use UserNotifications for older iOS versions
          try await self.scheduleWithNotifications(
            identifier: identifier,
            title: title,
            body: body,
            date: date,
            repeating: repeating,
            repeatInterval: repeatInterval,
            sound: sound
          )
        }
        #else
        // Use UserNotifications when AlarmKit isn't available
        try await self.scheduleWithNotifications(
          identifier: identifier,
          title: title,
          body: body,
          date: date,
          repeating: repeating,
          repeatInterval: repeatInterval,
          sound: sound
        )
        #endif

        // Store alarm info
        let alarmInfo: [String: Any] = [
          "identifier": identifier,
          "title": title,
          "body": body ?? NSNull(),
          "date": dateMillis,
          "repeating": repeating,
          "repeatInterval": repeatInterval ?? NSNull(),
          "sound": sound ?? NSNull(),
          "enabled": true
        ]

        self.saveAlarmInfo(identifier: identifier, alarmInfo: alarmInfo)
        promise.resolve(nil)

      } catch {
        promise.reject("ERR_ALARM_SCHEDULE", error.localizedDescription, error)
      }
    }

    AsyncFunction("cancelAlarmAsync") { (identifier: String, promise: Promise) in
      do {
        await self.cancelAlarmInternal(identifier: identifier)
        promise.resolve(nil)
      } catch {
        promise.reject("ERR_ALARM_CANCEL", error.localizedDescription, error)
      }
    }

    AsyncFunction("cancelAllAlarmsAsync") { (promise: Promise) in
      do {
        let alarms = self.getAllStoredAlarms()
        for alarmId in alarms.keys {
          await self.cancelAlarmInternal(identifier: alarmId)
        }
        self.clearAllAlarms()
        promise.resolve(nil)
      } catch {
        promise.reject("ERR_ALARM_CANCEL_ALL", error.localizedDescription, error)
      }
    }

    AsyncFunction("getAllAlarmsAsync") { (promise: Promise) in
      let alarms = self.getAllStoredAlarms()
      let alarmList = Array(alarms.values)
      promise.resolve(alarmList)
    }

    AsyncFunction("getAlarmAsync") { (identifier: String, promise: Promise) in
      let alarmInfo = self.getStoredAlarmInfo(identifier: identifier)
      promise.resolve(alarmInfo)
    }

    AsyncFunction("hasAlarmAsync") { (identifier: String, promise: Promise) in
      let hasAlarm = self.getStoredAlarmInfo(identifier: identifier) != nil
      promise.resolve(hasAlarm)
    }
  }
  
  @available(iOS 16.0, *)
  private func scheduleWithAlarmKit(identifier: String, title: String, body: String?, date: Date, repeating: Bool, repeatInterval: Double?, sound: String?) async throws {
    #if canImport(AlarmKit)
      let alarm = CALarm.alarm(dateComponents: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date), text: title)
      if repeating, let interval = repeatInterval {
        alarm.repeatInterval = .minute(Int(interval / 1000))
      }
      let alarmItem = CAAlarmItem(identifier: identifier, action: UIMutableUserNotificationAction(), alarms: [alarm])
      try await CAAlarm.set(alarmItem)
    #else
      // Fallback to notifications if AlarmKit isn't available
      try await scheduleWithNotifications(identifier: identifier, title: title, body: body, date: date, repeating: repeating, repeatInterval: repeatInterval, sound: sound)
    #endif
  }
  
  private func scheduleWithNotifications(identifier: String, title: String, body: String?, date: Date, repeating: Bool, repeatInterval: Double?, sound: String?) async throws {
    let content = UNMutableNotificationContent()
    content.title = title
    content.body = body ?? ""
    content.sound = UNNotificationSound.default
    
    let calendar = Calendar.current
    let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
    
    let trigger: UNNotificationTrigger
    if repeating, let interval = repeatInterval {
      trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval / 1000, repeats: true)
    } else {
      trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
    }
    
    let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
    
    try await UNUserNotificationCenter.current().add(request)
  }
  
  private func cancelAlarmInternal(identifier: String) async {
    // Cancel notification
    UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [identifier])
    
    // Remove from storage
    removeStoredAlarmInfo(identifier: identifier)
  }
  
  private func saveAlarmInfo(identifier: String, alarmInfo: [String: Any]) {
    var allAlarms = getAllStoredAlarms()
    allAlarms[identifier] = alarmInfo
    
    if let data = try? JSONSerialization.data(withJSONObject: allAlarms, options: []) {
      userDefaults.set(data, forKey: alarmsKey)
    }
  }
  
  private func getStoredAlarmInfo(identifier: String) -> [String: Any]? {
    let allAlarms = getAllStoredAlarms()
    return allAlarms[identifier]
  }
  
  private func removeStoredAlarmInfo(identifier: String) {
    var allAlarms = getAllStoredAlarms()
    allAlarms.removeValue(forKey: identifier)
    
    if let data = try? JSONSerialization.data(withJSONObject: allAlarms, options: []) {
      userDefaults.set(data, forKey: alarmsKey)
    }
  }
  
  private func getAllStoredAlarms() -> [String: [String: Any]] {
    guard let data = userDefaults.data(forKey: alarmsKey),
          let alarms = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: [String: Any]] else {
      return [:]
    }
    return alarms
  }
  
  private func clearAllAlarms() {
    userDefaults.removeObject(forKey: alarmsKey)
  }
}
