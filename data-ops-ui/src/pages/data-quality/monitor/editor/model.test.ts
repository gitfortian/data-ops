import type { MonitorSettingsView } from '@/services/data-quality';

import {
  buildSettings,
  notificationFromSettings,
  scheduleFromSettings,
} from './model';

const baseSettings = (
  patch: Partial<MonitorSettingsView>,
): MonitorSettingsView => ({
  runMode: 'MANUAL',
  ruleFailureAction: 'CONTINUE',
  notifyEnabled: false,
  notifyChannel: 'MESSAGE',
  alertLevel: 'WARNING',
  ...patch,
});

describe('quality monitor editor settings mapping', () => {
  it('maps legacy daily schedules to canonical Cron', () => {
    const schedule = scheduleFromSettings(
      baseSettings({
        runMode: 'SCHEDULE',
        scheduleFrequency: 'DAILY',
        scheduleTime: '09:30',
      }),
    );

    expect(schedule.scheduleEnabled).toBe(true);
    expect(schedule.cronExpression).toBe('0 30 9 * * ?');
  });

  it('maps legacy weekly schedules using Quartz weekday numbering', () => {
    const schedule = scheduleFromSettings(
      baseSettings({
        runMode: 'SCHEDULE',
        scheduleFrequency: 'WEEKLY',
        scheduleTime: '18:30',
        scheduleWeekday: 'FRI',
      }),
    );

    expect(schedule.cronExpression).toBe('0 30 18 ? * 6');
  });

  it('preserves Cron while scheduling is disabled', () => {
    const settings = baseSettings({
      scheduleEnabled: false,
      cronExpression: '0 0 2 * * ?',
    });

    const schedule = scheduleFromSettings(settings);
    const notification = notificationFromSettings(settings);
    const payload = buildSettings(schedule, notification);

    expect(schedule.scheduleEnabled).toBe(false);
    expect(payload.scheduleEnabled).toBe(false);
    expect(payload.cronExpression).toBe('0 0 2 * * ?');
    expect(payload.runMode).toBeUndefined();
    expect(payload.scheduleFrequency).toBeUndefined();
  });
});
