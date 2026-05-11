import { registerWebModule, NativeModule } from "expo";

import {
  ExpoAlarmModuleEvents,
  AlarmTriggerInput,
  AlarmInfo,
} from "./ExpoAlarm.types";

class ExpoAlarmModule extends NativeModule<ExpoAlarmModuleEvents> {
  private alarms: Map<string, AlarmInfo> = new Map();
  private timeouts: Map<string, ReturnType<typeof setTimeout>> = new Map();

  isSupported(): boolean {
    // Web platform supports alarms via browser notifications + setTimeout
    return "Notification" in window;
  }

  async requestPermissionsAsync(): Promise<{
    granted: boolean;
    canAskAgain: boolean;
  }> {
    // Web doesn't require permissions for notifications
    return { granted: true, canAskAgain: false };
  }

  async getPermissionsAsync(): Promise<{
    granted: boolean;
    canAskAgain: boolean;
  }> {
    return { granted: true, canAskAgain: false };
  }

  async scheduleAlarmAsync(alarm: AlarmTriggerInput): Promise<void> {
    if (!("Notification" in window)) {
      throw new Error("Notifications are not supported in this browser");
    }

    if (Notification.permission !== "granted") {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        throw new Error("Notification permission not granted");
      }
    }

    // Cancel existing alarm with same identifier
    await this.cancelAlarmAsync(alarm.identifier);

    const alarmInfo: AlarmInfo = {
      identifier: alarm.identifier,
      title: alarm.title,
      body: alarm.body,
      date: alarm.date,
      repeating: alarm.repeating || false,
      repeatInterval: alarm.repeatInterval,
      sound: alarm.sound,
      enabled: true,
    };

    this.alarms.set(alarm.identifier, alarmInfo);

    const now = new Date().getTime();
    const triggerTime = alarm.date.getTime();
    const delay = triggerTime - now;

    if (delay > 0) {
      const timeout = setTimeout(() => {
        this.triggerAlarm(alarmInfo);
      }, delay);

      this.timeouts.set(alarm.identifier, timeout);
    } else {
      throw new Error("Alarm time must be in the future");
    }
  }

  private triggerAlarm(alarm: AlarmInfo): void {
    if ("Notification" in window && Notification.permission === "granted") {
      // eslint-disable-next-line no-new
      new Notification(alarm.title, {
        body: alarm.body,
        icon: "/favicon.ico",
      });
    }

    this.emit("alarmTriggered", {
      identifier: alarm.identifier,
      title: alarm.title,
      body: alarm.body,
      date: alarm.date,
    });

    // Handle repeating alarms
    if (alarm.repeating && alarm.repeatInterval) {
      const nextDate = new Date(alarm.date.getTime() + alarm.repeatInterval);
      const updatedAlarm = { ...alarm, date: nextDate };
      this.alarms.set(alarm.identifier, updatedAlarm);

      const timeout = setTimeout(() => {
        this.triggerAlarm(updatedAlarm);
      }, alarm.repeatInterval);

      this.timeouts.set(alarm.identifier, timeout);
    } else {
      this.alarms.delete(alarm.identifier);
      this.timeouts.delete(alarm.identifier);
    }
  }

  async cancelAlarmAsync(identifier: string): Promise<void> {
    const timeout = this.timeouts.get(identifier);
    if (timeout) {
      clearTimeout(timeout);
      this.timeouts.delete(identifier);
    }
    this.alarms.delete(identifier);
  }

  async cancelAllAlarmsAsync(): Promise<void> {
    for (const timeout of this.timeouts.values()) {
      clearTimeout(timeout);
    }
    this.timeouts.clear();
    this.alarms.clear();
  }

  async getAllAlarmsAsync(): Promise<AlarmInfo[]> {
    return Array.from(this.alarms.values());
  }

  async getAlarmAsync(identifier: string): Promise<AlarmInfo | null> {
    return this.alarms.get(identifier) || null;
  }

  async hasAlarmAsync(identifier: string): Promise<boolean> {
    return this.alarms.has(identifier);
  }

  async setAlarmEnabledAsync(input: {
    identifier: string;
    enabled: boolean;
  }): Promise<void> {
    const alarm = this.alarms.get(input.identifier);
    if (!alarm) {
      throw new Error(`Alarm with identifier '${input.identifier}' not found`);
    }

    if (input.enabled) {
      // Re-enable: clear any existing timeout and reschedule
      const existingTimeout = this.timeouts.get(input.identifier);
      if (existingTimeout) {
        clearTimeout(existingTimeout);
        this.timeouts.delete(input.identifier);
      }

      const now = new Date().getTime();
      const delay = alarm.date.getTime() - now;

      if (delay > 0) {
        const timeout = setTimeout(() => {
          this.triggerAlarm(alarm);
        }, delay);
        this.timeouts.set(input.identifier, timeout);
      }
      // If delay <= 0, the alarm time has passed -- leave it disabled
      // (it will fire when the page is reloaded with a future date)
    } else {
      // Disable: clear the timeout and mark as disabled
      const existingTimeout = this.timeouts.get(input.identifier);
      if (existingTimeout) {
        clearTimeout(existingTimeout);
        this.timeouts.delete(input.identifier);
      }
      const updatedAlarm = { ...alarm, enabled: false };
      this.alarms.set(input.identifier, updatedAlarm);
    }
  }
}

export default registerWebModule(ExpoAlarmModule, "ExpoAlarmModule");
