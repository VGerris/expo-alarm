export type AlarmTriggerInput = {
  /** Unique identifier for the alarm */
  identifier: string;
  /** Title of the alarm */
  title: string;
  /** Body/description of the alarm */
  body?: string;
  /** Date and time when the alarm should trigger */
  date: Date;
  /** Whether the alarm should repeat */
  repeating?: boolean;
  /** Repeat interval in milliseconds (for repeating alarms) */
  repeatInterval?: number;
  /** Sound to play (optional, uses default if not specified) */
  sound?: string;
};

export type AlarmInfo = {
  /** Unique identifier for the alarm */
  identifier: string;
  /** Title of the alarm */
  title: string;
  /** Body/description of the alarm */
  body?: string;
  /** Date and time when the alarm should trigger */
  date: Date;
  /** Whether the alarm should repeat */
  repeating: boolean;
  /** Repeat interval in milliseconds (for repeating alarms) */
  repeatInterval?: number;
  /** Sound to play */
  sound?: string;
  /** Whether the alarm is currently enabled */
  enabled: boolean;
};

export type ExpoAlarmModuleEvents = {
  alarmTriggered: (params: AlarmTriggeredEventPayload) => void;
  alarmDismissed: (params: AlarmDismissedEventPayload) => void;
};

export type AlarmTriggeredEventPayload = {
  identifier: string;
  title: string;
  body?: string;
  date: Date;
};

export type AlarmDismissedEventPayload = {
  identifier: string;
};

export type SetAlarmEnabledInput = {
  /** Unique identifier for the alarm */
  identifier: string;
  /** Whether the alarm should be enabled */
  enabled: boolean;
};
