export type SchedulePeriod = 'minute' | 'hour' | 'day' | 'week' | 'month' | 'year';
export type HourMode = 'range' | 'specified';

/** Quartz-style day-of-week: Sunday=1, Monday=2 ... Saturday=7. */
export type QuartzWeekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export type MonthRule =
  | { kind: 'day'; day: number }
  | { kind: 'nthWeekday'; nth: 1 | 2 | 3 | 4; weekday: QuartzWeekday }
  | { kind: 'lastDay' }
  | { kind: 'lastWeekday'; weekday: QuartzWeekday };

export interface CronScheduleConfig {
  period: SchedulePeriod;

  minuteStartTime: string;
  minuteInterval: number;
  minuteEndTime: string;

  hourMode: HourMode;
  hourStartTime: string;
  hourInterval: number;
  hourEndTime: string;
  specifiedHours: number[];
  specifiedHourMinute: number;

  dayTime: string;

  weekdays: QuartzWeekday[];
  weekTime: string;

  monthRules: MonthRule[];
  monthTime: string;

  months: number[];
  yearRules: MonthRule[];
  yearTime: string;
}

export interface CronGenerationResult {
  cron: string;
  fields: {
    second: string;
    minute: string;
    hour: string;
    dayOfMonth: string;
    month: string;
    dayOfWeek: string;
  };
}
