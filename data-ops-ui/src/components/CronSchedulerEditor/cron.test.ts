import { createDefaultCronScheduleConfig, generateCron, parseCron } from './cron';

describe('CronSchedulerEditor cron codec', () => {
  it('generates minute schedules', () => {
    expect(
      generateCron({
        ...createDefaultCronScheduleConfig(),
        period: 'minute',
        minuteStartTime: '00:00',
        minuteInterval: 5,
        minuteEndTime: '23:59',
      }).cron,
    ).toBe('00 */5 00-23 * * ?');
  });

  it('generates weekly schedules', () => {
    expect(
      generateCron({
        ...createDefaultCronScheduleConfig(),
        period: 'week',
        weekdays: [2, 4, 6],
        weekTime: '09:30',
      }).cron,
    ).toBe('00 30 09 ? * 2,4,6');
  });

  it('generates month rules with last weekday', () => {
    expect(
      generateCron({
        ...createDefaultCronScheduleConfig(),
        period: 'month',
        monthRules: [{ kind: 'lastWeekday', weekday: 3 }],
        monthTime: '00:19',
      }).cron,
    ).toBe('00 19 00 ? * 3L');
  });

  it('generates yearly schedules', () => {
    expect(
      generateCron({
        ...createDefaultCronScheduleConfig(),
        period: 'year',
        months: [1, 7],
        yearRules: [{ kind: 'day', day: 1 }],
        yearTime: '08:15',
      }).cron,
    ).toBe('00 15 08 1 1,7 ?');
  });

  it('parses existing Yak Ops presets', () => {
    const daily = parseCron('0 0 2 * * ?');
    const hourly = parseCron('0 0 * * * ?');
    const tenMinutes = parseCron('0 0/10 * * * ?');

    expect(daily?.period).toBe('day');
    expect(daily?.dayTime).toBe('02:00');
    expect(hourly?.period).toBe('hour');
    expect(tenMinutes?.period).toBe('minute');
    expect(tenMinutes?.minuteInterval).toBe(10);
  });

  it('keeps unsupported expressions out of visual parsing', () => {
    expect(parseCron('0 0 2 LW * ?')).toBeUndefined();
    expect(parseCron('0 0 2 * * ? 2028')).toBeUndefined();
  });
});
