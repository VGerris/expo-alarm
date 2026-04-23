# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Expo module called `expo-alarm` that provides cross-platform alarm scheduling functionality for Android, iOS, and Web. The module uses AlarmManager on Android, UserNotifications on iOS (with AlarmKit structure ready for iOS 16+), and browser notifications on web as a fallback.

## Development Commands

- `npm run build` - Build the module using expo-module build
- `npm run clean` - Clean build artifacts
- `npm run lint` - Run linting on the codebase
- `npm run test` - Run tests
- `npm run prepare` - Prepare the module for development
- `npm run prepublishOnly` - Prepare for publishing
- `npm run open:ios` - Open iOS example project in Xcode
- `npm run open:android` - Open Android example project in Android Studio
- `npm run expo-module` - Access expo-module-scripts CLI directly

## Architecture

### Module Structure
- **JavaScript Layer** (`src/`):
  - `ExpoAlarmModule.ts` - Native module interface and type definitions
  - `ExpoAlarm.types.ts` - TypeScript type definitions for AlarmTriggerInput, AlarmInfo, and events
  - `ExpoAlarmModule.web.ts` - Web implementation using browser notifications and setTimeout
  - `index.ts` - Main export file

- **Android Layer** (`android/src/main/java/expo/modules/alarm/`):
  - `ExpoAlarmModule.kt` - Android native module with AlarmManager integration and SharedPreferences storage
  - `AlarmReceiver.kt` - BroadcastReceiver for handling alarm triggers and notifications
  - `AlarmDismissReceiver.kt` - BroadcastReceiver for handling alarm dismissals
  - `AlarmService.kt` - Foreground service for alarm handling
  - `BootReceiver.kt` - Handles device boot to reschedule alarms
  - `NotificationActionReceiver.kt` - Handles dismiss/snooze notification actions

- **iOS Layer** (`ios/`):
  - `ExpoAlarmModule.swift` - iOS native module with UserNotifications framework and UserDefaults storage
  - Conditional AlarmKit integration for iOS 16+ (structure ready, falls back to UserNotifications)

### Module Configuration
- `expo-module.config.json` defines platform-specific module mappings
- Supports Apple, Android, and Web platforms
- Native module names: `ExpoAlarmModule` (iOS), `expo.modules.alarm.ExpoAlarmModule` (Android)

### Example App
The `example/` directory contains a complete React Native app demonstrating:
- Permission checking and requesting
- Scheduling one-time and repeating alarms
- Viewing and managing active alarms
- Event handling for alarm triggers and dismissals
- Cross-platform alarm functionality

## Development Notes

- Uses `expo-module-scripts` for build tooling
- TypeScript configuration in `tsconfig.json`
- **Android Implementation**: 
  - Uses AlarmManager with `setExactAndAllowWhileIdle` for one-time alarms and `setRepeating` for repeating alarms
  - AlarmReceiver creates high-priority notifications with dismiss actions
  - Requires SCHEDULE_EXACT_ALARM permission on Android 12+
  - Alarm data persisted in SharedPreferences with JSON serialization
  - Foreground service (`AlarmService`) for reliable alarm handling
  - Boot receiver reschedules alarms after device restart
  - Full notification action support (dismiss/snooze)
- **iOS Implementation**: 
  - Uses UserNotifications framework with UNCalendarNotificationTrigger for one-time and UNTimeIntervalNotificationTrigger for repeating
  - Ready for AlarmKit integration on iOS 16+ (currently falls back to notifications)
  - Alarm data persisted in UserDefaults with JSON serialization
- **Web Implementation**: 
  - Limited functionality using browser notifications and setTimeout
  - Reports isSupported() as false due to limitations
  - In-memory alarm storage (does not persist across page reloads)
- Platform-specific code automatically resolved by Expo module system
- Event-driven architecture for alarm lifecycle management with `alarmTriggered` and `alarmDismissed` events

## Key Features
- Cross-platform alarm scheduling (Android, iOS, Web)
- Permission management
- Repeating and one-time alarms
- Alarm persistence and management
- Event notifications for alarm triggers

## Native Module API
All platform implementations expose the same JavaScript interface:
- `isSupported()` - Platform capability check
- `requestPermissionsAsync()` / `getPermissionsAsync()` - Permission handling
- `scheduleAlarmAsync(AlarmTriggerInput)` - Schedule alarms with identifier, title, body, date, repeating options
- `cancelAlarmAsync(identifier)` / `cancelAllAlarmsAsync()` - Alarm cancellation
- `getAllAlarmsAsync()` / `getAlarmAsync(identifier)` / `hasAlarmAsync(identifier)` - Alarm retrieval and checking

## Data Persistence
- **Android**: SharedPreferences with `alarm_${identifier}` keys, JSON serialized AlarmInfo objects
- **iOS**: UserDefaults with single `ExpoAlarmModule_alarms` key containing all alarms dictionary
- **Web**: In-memory Map storage (not persistent)

## Testing and Development
- Example app at `example/App.tsx` demonstrates full API usage with React hooks
- Use `npm run open:ios` and `npm run open:android` to test native implementations
- Web testing can be done through standard React Native Web setup

## Required Permissions (Android)
Module automatically declares the following in AndroidManifest.xml:
- `android.permission.SCHEDULE_EXACT_ALARM` - Required for exact alarm scheduling on Android 12+
- `android.permission.USE_EXACT_ALARM` - Alternative exact alarm permission
- `android.permission.VIBRATE` - For alarm vibration
- `android.permission.RECEIVE_BOOT_COMPLETED` - To reschedule alarms after device boot
- `android.permission.FOREGROUND_SERVICE` - For foreground alarm service
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` - For media playback service type (Android 14+)
- `android.permission.POST_NOTIFICATIONS` - For showing alarm notifications
- `android.permission.WAKE_LOCK` - To wake device when alarm triggers

## Package Information
- **Package Name**: `@VGerris/expo-alarm`
- **Version**: 0.1.0
- **Repository**: https://github.com/VGerris/expo-alarm
- **NPM Registry**: https://registry.npmjs.org/