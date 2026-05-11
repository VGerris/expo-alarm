import { NativeModule, requireNativeModule } from "expo";

import {
  ExpoAlarmModuleEvents,
  AlarmTriggerInput,
  AlarmInfo,
  SetAlarmEnabledInput,
} from "./ExpoAlarm.types";

declare class ExpoAlarmModule extends NativeModule<ExpoAlarmModuleEvents> {
  /**
   * Check if alarm scheduling is supported on the current platform and version
   */
  isSupported(): boolean;

  /**
   * Request necessary permissions for alarm functionality
   */
  requestPermissionsAsync(): Promise<{
    granted: boolean;
    canAskAgain: boolean;
  }>;

  /**
   * Get current permission status for alarms
   */
  getPermissionsAsync(): Promise<{ granted: boolean; canAskAgain: boolean }>;

  /**
   * Schedule a new alarm
   */
  scheduleAlarmAsync(alarm: AlarmTriggerInput): Promise<void>;

  /**
   * Cancel an existing alarm by identifier
   */
  cancelAlarmAsync(identifier: string): Promise<void>;

  /**
   * Cancel all scheduled alarms
   */
  cancelAllAlarmsAsync(): Promise<void>;

  /**
   * Get all scheduled alarms
   */
  getAllAlarmsAsync(): Promise<AlarmInfo[]>;

  /**
   * Get a specific alarm by identifier
   */
  getAlarmAsync(identifier: string): Promise<AlarmInfo | null>;

  /**
   * Check if a specific alarm exists
   */
  hasAlarmAsync(identifier: string): Promise<boolean>;

  /**
   * Enable or disable a specific alarm by identifier
   */
  setAlarmEnabledAsync(input: SetAlarmEnabledInput): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoAlarmModule>("ExpoAlarm");
