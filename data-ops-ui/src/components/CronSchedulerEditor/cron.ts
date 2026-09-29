import type {
  CronGenerationResult,
  CronScheduleConfig,
  MonthRule,
  QuartzWeekday,
} from './types';

const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, Number.isFinite(value) ? value : min));

const pad2 = (value: number) => String(value).padStart(2, '0');

function parseTime(value: string) {
  const [rawHour = '0', rawMinute = '0'] = value.split(':');
  return {
    hour: clamp(Number(rawHour), 0, 23),
    minute: clamp(Number(rawMinute), 0, 59),
  };
}

function uniqueSorted(values: number[]) {
  return [...new Set(values)].sort((left, right) => left - right);
}

function createResult(
  second: string,
  minute: string,
  hour: string,
  dayOfMonth: string,
  month: string,
  dayOfWeek: string,
): CronGenerationResult {
  const fields = { second, minute, hour, dayOfMonth, month, dayOfWeek };
  return {
    fields,
    cron: [second, minute, hour, dayOfMonth, month, dayOfWeek].join(' '),
  };
}

function splitMonthRules(rules: MonthRule[]) {
  const dayOfMonth: string[] = [];
  const dayOfWeek: string[] = [];

  rules.forEach((rule) => {
    switch (rule.kind) {
      case 'day':
        dayOfMonth.push(String(clamp(rule.day, 1, 31)));
        break;
      case 'lastDay':
        dayOfMonth.push('L');
        break;
      case 'nthWeekday':
        dayOfWeek.push(`${rule.weekday}#${rule.nth}`);
        break;
      case 'lastWeekday':
        dayOfWeek.push(`${rule.weekday}L`);
        break;
      default:
        break;
    }
  });

  return {
    dayOfMonth: [...new Set(dayOfMonth)],
    dayOfWeek: [...new Set(dayOfWeek)],
  };
}

export function createDefaultCronScheduleConfig(): CronScheduleConfig {
  return {
    period: 'day',
    minuteStartTime: '00:00',
    minuteInterval: 5,
    minuteEndTime: '23:59',
    hourMode: 'range',
    hourStartTime: '00:00',
    hourInterval: 1,
    hourEndTime: '23:59',
    specifiedHours: [0],
    specifiedHourMinute: 0,
    dayTime: '02:00',
    weekdays: [2],
    weekTime: '02:00',
    monthRules: [{ kind: 'day', day: 1 }],
    monthTime: '02:00',
    months: [1],
    yearRules: [{ kind: 'day', day: 1 }],
    yearTime: '02:00',
  };
}

/** Generate six-field Cron: second minute hour day-of-month month day-of-week. */
export function generateCron(config: CronScheduleConfig): CronGenerationResult {
  switch (config.period) {
    case 'minute': {
      const start = parseTime(config.minuteStartTime);
      const end = parseTime(config.minuteEndTime);
      const interval = clamp(Math.round(config.minuteInterval), 1, 59);
      const minute = start.minute === 0 ? `*/${interval}` : `${start.minute}/${interval}`;
      const from = Math.min(start.hour, end.hour);
      const to = Math.max(start.hour, end.hour);
      const hour = from === to ? pad2(from) : `${pad2(from)}-${pad2(to)}`;
      return createResult('00', minute, hour, '*', '*', '?');
    }

    case 'hour': {
      if (config.hourMode === 'specified') {
        const hours = uniqueSorted(config.specifiedHours.map((item) => clamp(item, 0, 23)));
        return createResult(
          '00',
          pad2(clamp(config.specifiedHourMinute, 0, 59)),
          hours.length ? hours.map(pad2).join(',') : '00',
          '*',
          '*',
          '?',
        );
      }

      const start = parseTime(config.hourStartTime);
      const end = parseTime(config.hourEndTime);
      const interval = clamp(Math.round(config.hourInterval), 1, 23);
      const from = Math.min(start.hour, end.hour);
      const to = Math.max(start.hour, end.hour);
      const hour = from === to ? pad2(from) : `${pad2(from)}-${pad2(to)}/${interval}`;
      return createResult('00', pad2(start.minute), hour, '*', '*', '?');
    }

    case 'day': {
      const time = parseTime(config.dayTime);
      return createResult('00', pad2(time.minute), pad2(time.hour), '*', '*', '?');
    }

    case 'week': {
      const time = parseTime(config.weekTime);
      const weekdays = uniqueSorted(config.weekdays).join(',') || '2';
      return createResult('00', pad2(time.minute), pad2(time.hour), '?', '*', weekdays);
    }

    case 'month': {
      const time = parseTime(config.monthTime);
      const parsed = splitMonthRules(config.monthRules);
      return createResult(
        '00',
        pad2(time.minute),
        pad2(time.hour),
        parsed.dayOfMonth.join(',') || '?',
        '*',
        parsed.dayOfWeek.join(',') || '?',
      );
    }

    case 'year': {
      const time = parseTime(config.yearTime);
      const parsed = splitMonthRules(config.yearRules);
      const month = uniqueSorted(config.months.map((item) => clamp(item, 1, 12))).join(',') || '1';
      return createResult(
        '00',
        pad2(time.minute),
        pad2(time.hour),
        parsed.dayOfMonth.join(',') || '?',
        month,
        parsed.dayOfWeek.join(',') || '?',
      );
    }
  }
}

function parseNumber(value: string, min: number, max: number): number | undefined {
  if (!/^\d+$/.test(value)) return undefined;
  const parsed = Number(value);
  return parsed >= min && parsed <= max ? parsed : undefined;
}

function parseNumberList(value: string, min: number, max: number): number[] | undefined {
  const parts = value.split(',');
  const parsed = parts.map((item) => parseNumber(item, min, max));
  if (parsed.some((item) => item === undefined)) return undefined;
  return uniqueSorted(parsed as number[]);
}

function parseRuleFields(dayOfMonth: string, dayOfWeek: string): MonthRule[] | undefined {
  const rules: MonthRule[] = [];

  if (dayOfMonth !== '?') {
    for (const token of dayOfMonth.split(',')) {
      if (token === 'L') {
        rules.push({ kind: 'lastDay' });
        continue;
      }
      const day = parseNumber(token, 1, 31);
      if (!day) return undefined;
      rules.push({ kind: 'day', day });
    }
  }

  if (dayOfWeek !== '?') {
    for (const token of dayOfWeek.split(',')) {
      const nthMatch = token.match(/^([1-7])#([1-4])$/);
      if (nthMatch) {
        rules.push({
          kind: 'nthWeekday',
          weekday: Number(nthMatch[1]) as QuartzWeekday,
          nth: Number(nthMatch[2]) as 1 | 2 | 3 | 4,
        });
        continue;
      }
      const lastMatch = token.match(/^([1-7])L$/);
      if (lastMatch) {
        rules.push({ kind: 'lastWeekday', weekday: Number(lastMatch[1]) as QuartzWeekday });
        continue;
      }
      return undefined;
    }
  }

  return rules.length ? rules : undefined;
}

/**
 * Parses the subset emitted by this editor plus the existing Yak Ops presets.
 * Unsupported advanced expressions deliberately return undefined so callers can
 * preserve them in manual mode instead of rewriting them incorrectly.
 */
export function parseCron(cron?: string): CronScheduleConfig | undefined {
  const normalized = cron?.trim().replace(/\s+/g, ' ');
  if (!normalized) return createDefaultCronScheduleConfig();

  const fields = normalized.split(' ');
  if (fields.length !== 6) return undefined;
  const [second, minute, hour, dayOfMonth, month, dayOfWeek] = fields;
  if (!['0', '00'].includes(second)) return undefined;

  const base = createDefaultCronScheduleConfig();

  if (dayOfMonth === '*' && month === '*' && dayOfWeek === '?') {
    const minuteIntervalMatch = minute.match(/^(\*|\d{1,2})\/(\d{1,2})$/);
    const plainHourRange = hour.match(/^(\d{1,2})-(\d{1,2})$/);
    if (minuteIntervalMatch && (plainHourRange || /^\d{1,2}$/.test(hour) || hour === '*')) {
      const interval = parseNumber(minuteIntervalMatch[2], 1, 59);
      const startMinute = minuteIntervalMatch[1] === '*'
        ? 0
        : parseNumber(minuteIntervalMatch[1], 0, 59);
      const fromHour = hour === '*'
        ? 0
        : plainHourRange
          ? parseNumber(plainHourRange[1], 0, 23)
          : parseNumber(hour, 0, 23);
      const toHour = hour === '*'
        ? 23
        : plainHourRange
          ? parseNumber(plainHourRange[2], 0, 23)
          : parseNumber(hour, 0, 23);
      if (interval && startMinute !== undefined && fromHour !== undefined && toHour !== undefined) {
        return {
          ...base,
          period: 'minute',
          minuteInterval: interval,
          minuteStartTime: `${pad2(fromHour)}:${pad2(startMinute)}`,
          minuteEndTime: `${pad2(toHour)}:59`,
        };
      }
    }

    if (hour === '*') {
      const parsedMinute = parseNumber(minute, 0, 59);
      if (parsedMinute !== undefined) {
        return {
          ...base,
          period: 'hour',
          hourMode: 'range',
          hourStartTime: `00:${pad2(parsedMinute)}`,
          hourEndTime: `23:${pad2(parsedMinute)}`,
          hourInterval: 1,
        };
      }
    }

    const hourRangeMatch = hour.match(/^(\d{1,2})-(\d{1,2})\/(\d{1,2})$/);
    if (hourRangeMatch) {
      const parsedMinute = parseNumber(minute, 0, 59);
      const fromHour = parseNumber(hourRangeMatch[1], 0, 23);
      const toHour = parseNumber(hourRangeMatch[2], 0, 23);
      const interval = parseNumber(hourRangeMatch[3], 1, 23);
      if (parsedMinute !== undefined && fromHour !== undefined && toHour !== undefined && interval) {
        return {
          ...base,
          period: 'hour',
          hourMode: 'range',
          hourStartTime: `${pad2(fromHour)}:${pad2(parsedMinute)}`,
          hourEndTime: `${pad2(toHour)}:${pad2(parsedMinute)}`,
          hourInterval: interval,
        };
      }
    }

    if (hour.includes(',')) {
      const parsedMinute = parseNumber(minute, 0, 59);
      const hours = parseNumberList(hour, 0, 23);
      if (parsedMinute !== undefined && hours?.length) {
        return {
          ...base,
          period: 'hour',
          hourMode: 'specified',
          specifiedHours: hours,
          specifiedHourMinute: parsedMinute,
        };
      }
    }

    const parsedMinute = parseNumber(minute, 0, 59);
    const parsedHour = parseNumber(hour, 0, 23);
    if (parsedMinute !== undefined && parsedHour !== undefined) {
      return {
        ...base,
        period: 'day',
        dayTime: `${pad2(parsedHour)}:${pad2(parsedMinute)}`,
      };
    }
    return undefined;
  }

  if (dayOfMonth === '?' && month === '*' && dayOfWeek !== '?') {
    const parsedMinute = parseNumber(minute, 0, 59);
    const parsedHour = parseNumber(hour, 0, 23);
    const weekdays = parseNumberList(dayOfWeek, 1, 7) as QuartzWeekday[] | undefined;
    if (parsedMinute !== undefined && parsedHour !== undefined && weekdays?.length) {
      return {
        ...base,
        period: 'week',
        weekdays,
        weekTime: `${pad2(parsedHour)}:${pad2(parsedMinute)}`,
      };
    }
    // Expressions such as `3L` and `3#2` are monthly rules, not weekly lists.
  }

  const parsedMinute = parseNumber(minute, 0, 59);
  const parsedHour = parseNumber(hour, 0, 23);
  if (parsedMinute === undefined || parsedHour === undefined) return undefined;

  const rules = parseRuleFields(dayOfMonth, dayOfWeek);
  if (!rules) return undefined;

  if (month === '*') {
    return {
      ...base,
      period: 'month',
      monthRules: rules,
      monthTime: `${pad2(parsedHour)}:${pad2(parsedMinute)}`,
    };
  }

  const months = parseNumberList(month, 1, 12);
  if (!months?.length) return undefined;
  return {
    ...base,
    period: 'year',
    months,
    yearRules: rules,
    yearTime: `${pad2(parsedHour)}:${pad2(parsedMinute)}`,
  };
}
