import {
  DatePicker,
  Input,
  InputNumber,
  Radio,
  Select,
  TimePicker,
  Tooltip,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { CircleHelp, Copy, Info } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import { createDefaultCronScheduleConfig, generateCron, parseCron } from './cron';
import type {
  CronScheduleConfig,
  MonthRule,
  QuartzWeekday,
  SchedulePeriod,
} from './types';

export type { CronScheduleConfig, MonthRule, SchedulePeriod } from './types';
export { generateCron, parseCron } from './cron';

const TIME_FORMAT = 'HH:mm';

const PERIOD_OPTIONS: { label: string; value: SchedulePeriod }[] = [
  { label: '分钟', value: 'minute' },
  { label: '小时', value: 'hour' },
  { label: '日', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
  { label: '年', value: 'year' },
];

const WEEKDAY_OPTIONS: { label: string; value: QuartzWeekday }[] = [
  { label: '星期一', value: 2 },
  { label: '星期二', value: 3 },
  { label: '星期三', value: 4 },
  { label: '星期四', value: 5 },
  { label: '星期五', value: 6 },
  { label: '星期六', value: 7 },
  { label: '星期日', value: 1 },
];

const MONTH_OPTIONS = [
  '一月',
  '二月',
  '三月',
  '四月',
  '五月',
  '六月',
  '七月',
  '八月',
  '九月',
  '十月',
  '十一月',
  '十二月',
].map((label, index) => ({ label, value: index + 1 }));

const ORDINAL = ['第一个', '第二个', '第三个', '第四个'];

const MONTH_RULE_OPTIONS = [
  ...Array.from({ length: 31 }, (_, index) => ({
    label: `每月${index + 1}号`,
    value: `day:${index + 1}`,
    rule: { kind: 'day', day: index + 1 } as MonthRule,
  })),
  ...Array.from({ length: 4 }, (_, nthIndex) =>
    WEEKDAY_OPTIONS.map(({ label, value }) => ({
      label: `每月${ORDINAL[nthIndex]}${label}`,
      value: `nth:${nthIndex + 1}:${value}`,
      rule: {
        kind: 'nthWeekday',
        nth: (nthIndex + 1) as 1 | 2 | 3 | 4,
        weekday: value,
      } as MonthRule,
    })),
  ).flat(),
  { label: '每月最后一天', value: 'last-day', rule: { kind: 'lastDay' } as MonthRule },
  ...WEEKDAY_OPTIONS.map(({ label, value }) => ({
    label: `每月最后一个${label}`,
    value: `last-weekday:${value}`,
    rule: { kind: 'lastWeekday', weekday: value } as MonthRule,
  })),
];

function monthRuleToValue(rule: MonthRule): string {
  switch (rule.kind) {
    case 'day':
      return `day:${rule.day}`;
    case 'nthWeekday':
      return `nth:${rule.nth}:${rule.weekday}`;
    case 'lastDay':
      return 'last-day';
    case 'lastWeekday':
      return `last-weekday:${rule.weekday}`;
  }
}

function monthRuleFromValue(value: string): MonthRule | undefined {
  return MONTH_RULE_OPTIONS.find((item) => item.value === value)?.rule;
}

function timeValue(value: string) {
  const [hour = '0', minute = '0'] = value.split(':');
  return dayjs().hour(Number(hour) || 0).minute(Number(minute) || 0).second(0).millisecond(0);
}

interface FieldRowProps {
  label: string;
  required?: boolean;
  help?: string;
  children: ReactNode;
}

function FieldRow({ label, required, help, children }: FieldRowProps) {
  return (
    <div className="grid min-h-9 grid-cols-1 items-start gap-1.5 py-1 md:grid-cols-[150px_minmax(0,1fr)] md:gap-5">
      <div className="flex min-h-8 items-center text-[13px] text-[#4e5969] md:justify-end">
        {required ? <span className="mr-1 text-[#f04438]">*</span> : null}
        <span>{label}</span>
        {help ? (
          <Tooltip title={help}>
            <CircleHelp size={13} className="ml-1 text-[#98a2b3]" />
          </Tooltip>
        ) : null}
        <span>：</span>
      </div>
      <div className="flex min-h-8 min-w-0 items-start">{children}</div>
    </div>
  );
}

async function copyText(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
}

export interface CronSchedulerEditorProps {
  value?: string;
  onChange?: (cronExpression: string) => void;
  timezone?: string;
  effectiveRange?: [Dayjs, Dayjs];
  onEffectiveRangeChange?: (value?: [Dayjs, Dayjs]) => void;
  disabled?: boolean;
  className?: string;
  showTimezoneTip?: boolean;
  showEffectiveDate?: boolean;
}

export default function CronSchedulerEditor({
  value,
  onChange,
  timezone = 'Asia/Shanghai',
  effectiveRange,
  onEffectiveRangeChange,
  disabled = false,
  className,
  showTimezoneTip = true,
  showEffectiveDate = true,
}: CronSchedulerEditorProps) {
  const initialValueRef = useRef(parseCron(value));
  const [config, setConfig] = useState<CronScheduleConfig>(
    initialValueRef.current || createDefaultCronScheduleConfig(),
  );
  const [manualMode, setManualMode] = useState(
    Boolean(value?.trim() && !initialValueRef.current),
  );
  const previousExternalValue = useRef(value);

  const generatedCron = useMemo(() => generateCron(config).cron, [config]);
  const cronExpression = manualMode ? value?.trim() || '' : generatedCron;

  useEffect(() => {
    if (value === previousExternalValue.current) return;
    previousExternalValue.current = value;
    const parsed = parseCron(value);
    if (parsed) {
      setConfig(parsed);
      setManualMode(false);
    } else if (value?.trim()) {
      setManualMode(true);
    }
  }, [value]);

  const patch = (next: Partial<CronScheduleConfig>) => {
    const nextConfig = { ...config, ...next };
    const nextCron = generateCron(nextConfig).cron;
    setConfig(nextConfig);
    setManualMode(false);
    previousExternalValue.current = nextCron;
    onChange?.(nextCron);
  };

  const renderPeriodFields = () => {
    switch (config.period) {
      case 'minute':
        return (
          <>
            <FieldRow label="开始时间" required help="从该时间开始按分钟间隔触发。">
              <TimePicker
                className="w-[228px] max-w-full"
                value={timeValue(config.minuteStartTime)}
                format={TIME_FORMAT}
                allowClear={false}
                disabled={disabled}
                onChange={(next) => next && patch({ minuteStartTime: next.format(TIME_FORMAT) })}
              />
            </FieldRow>
            <FieldRow label="时间间隔" required>
              <div className="flex items-center gap-2">
                <InputNumber
                  className="w-[228px] max-w-full"
                  min={1}
                  max={59}
                  precision={0}
                  value={config.minuteInterval}
                  disabled={disabled}
                  onChange={(next) => patch({ minuteInterval: Number(next ?? 1) })}
                />
                <span className="text-[13px] text-[#4e5969]">分钟</span>
              </div>
            </FieldRow>
            <FieldRow label="结束时间" required>
              <TimePicker
                className="w-[228px] max-w-full"
                value={timeValue(config.minuteEndTime)}
                format={TIME_FORMAT}
                allowClear={false}
                disabled={disabled}
                onChange={(next) => next && patch({ minuteEndTime: next.format(TIME_FORMAT) })}
              />
            </FieldRow>
          </>
        );

      case 'hour':
        return (
          <>
            <FieldRow label="小时模式" required>
              <Radio.Group
                value={config.hourMode}
                optionType="button"
                buttonStyle="solid"
                disabled={disabled}
                onChange={(event) => patch({ hourMode: event.target.value })}
              >
                <Radio.Button value="range">小时区间</Radio.Button>
                <Radio.Button value="specified">指定小时</Radio.Button>
              </Radio.Group>
            </FieldRow>
            {config.hourMode === 'range' ? (
              <>
                <FieldRow label="开始时间" required>
                  <TimePicker
                    className="w-[228px] max-w-full"
                    value={timeValue(config.hourStartTime)}
                    format={TIME_FORMAT}
                    allowClear={false}
                    disabled={disabled}
                    onChange={(next) => next && patch({ hourStartTime: next.format(TIME_FORMAT) })}
                  />
                </FieldRow>
                <FieldRow label="时间间隔" required>
                  <div className="flex items-center gap-2">
                    <InputNumber
                      className="w-[228px] max-w-full"
                      min={1}
                      max={23}
                      precision={0}
                      value={config.hourInterval}
                      disabled={disabled}
                      onChange={(next) => patch({ hourInterval: Number(next ?? 1) })}
                    />
                    <span className="text-[13px] text-[#4e5969]">小时</span>
                  </div>
                </FieldRow>
                <FieldRow label="结束时间" required>
                  <TimePicker
                    className="w-[228px] max-w-full"
                    value={timeValue(config.hourEndTime)}
                    format={TIME_FORMAT}
                    allowClear={false}
                    disabled={disabled}
                    onChange={(next) => next && patch({ hourEndTime: next.format(TIME_FORMAT) })}
                  />
                </FieldRow>
              </>
            ) : (
              <>
                <FieldRow label="指定小时" required>
                  <Select
                    mode="multiple"
                    className="w-full max-w-[456px]"
                    value={config.specifiedHours}
                    maxTagCount="responsive"
                    disabled={disabled}
                    placeholder="请选择执行小时"
                    options={Array.from({ length: 24 }, (_, hour) => ({
                      label: `${String(hour).padStart(2, '0')}时`,
                      value: hour,
                    }))}
                    onChange={(next) => patch({ specifiedHours: next })}
                  />
                </FieldRow>
                <FieldRow label="执行分钟" required>
                  <div className="flex items-center gap-2">
                    <InputNumber
                      className="w-[228px] max-w-full"
                      min={0}
                      max={59}
                      precision={0}
                      value={config.specifiedHourMinute}
                      disabled={disabled}
                      onChange={(next) => patch({ specifiedHourMinute: Number(next ?? 0) })}
                    />
                    <span className="text-[13px] text-[#4e5969]">分</span>
                  </div>
                </FieldRow>
              </>
            )}
          </>
        );

      case 'day':
        return (
          <FieldRow label="调度时间" required>
            <TimePicker
              className="w-[228px] max-w-full"
              value={timeValue(config.dayTime)}
              format={TIME_FORMAT}
              allowClear={false}
              disabled={disabled}
              onChange={(next) => next && patch({ dayTime: next.format(TIME_FORMAT) })}
            />
          </FieldRow>
        );

      case 'week':
        return (
          <>
            <FieldRow label="指定星期" required>
              <Select
                mode="multiple"
                className="w-full max-w-[456px]"
                value={config.weekdays}
                maxTagCount="responsive"
                disabled={disabled}
                options={WEEKDAY_OPTIONS}
                onChange={(next) => patch({ weekdays: next })}
              />
            </FieldRow>
            <FieldRow label="调度时间" required>
              <TimePicker
                className="w-[228px] max-w-full"
                value={timeValue(config.weekTime)}
                format={TIME_FORMAT}
                allowClear={false}
                disabled={disabled}
                onChange={(next) => next && patch({ weekTime: next.format(TIME_FORMAT) })}
              />
            </FieldRow>
          </>
        );

      case 'month':
        return (
          <>
            <FieldRow label="指定时间" required>
              <Select
                mode="multiple"
                showSearch
                className="w-full max-w-[456px]"
                value={config.monthRules.map(monthRuleToValue)}
                maxTagCount="responsive"
                disabled={disabled}
                optionFilterProp="label"
                options={MONTH_RULE_OPTIONS.map(({ label, value: optionValue }) => ({
                  label,
                  value: optionValue,
                }))}
                onChange={(values) =>
                  patch({
                    monthRules: values
                      .map(monthRuleFromValue)
                      .filter((item): item is MonthRule => Boolean(item)),
                  })
                }
              />
            </FieldRow>
            <FieldRow label="调度时间" required>
              <TimePicker
                className="w-[228px] max-w-full"
                value={timeValue(config.monthTime)}
                format={TIME_FORMAT}
                allowClear={false}
                disabled={disabled}
                onChange={(next) => next && patch({ monthTime: next.format(TIME_FORMAT) })}
              />
            </FieldRow>
          </>
        );

      case 'year':
        return (
          <>
            <FieldRow label="指定月份" required>
              <Select
                mode="multiple"
                className="w-full max-w-[456px]"
                value={config.months}
                maxTagCount="responsive"
                disabled={disabled}
                options={MONTH_OPTIONS}
                onChange={(next) => patch({ months: next })}
              />
            </FieldRow>
            <FieldRow label="指定时间" required>
              <Select
                mode="multiple"
                showSearch
                className="w-full max-w-[456px]"
                value={config.yearRules.map(monthRuleToValue)}
                maxTagCount="responsive"
                disabled={disabled}
                optionFilterProp="label"
                options={MONTH_RULE_OPTIONS.map(({ label, value: optionValue }) => ({
                  label,
                  value: optionValue,
                }))}
                onChange={(values) =>
                  patch({
                    yearRules: values
                      .map(monthRuleFromValue)
                      .filter((item): item is MonthRule => Boolean(item)),
                  })
                }
              />
            </FieldRow>
            <FieldRow label="调度时间" required>
              <TimePicker
                className="w-[228px] max-w-full"
                value={timeValue(config.yearTime)}
                format={TIME_FORMAT}
                allowClear={false}
                disabled={disabled}
                onChange={(next) => next && patch({ yearTime: next.format(TIME_FORMAT) })}
              />
            </FieldRow>
          </>
        );
    }
  };

  const effectiveType = effectiveRange?.length === 2 ? 'range' : 'forever';

  return (
    <div className={['w-full text-[#161823]', className || ''].join(' ')}>
      {showTimezoneTip ? (
        <div className="mb-4 flex min-h-9 items-center gap-2 rounded-md bg-[#f2f5ff] px-3 py-2 text-[12px] text-[#52637a]">
          <Info size={14} className="shrink-0 text-[#6074d9]" />
          <span>
            调度时区为 <span className="font-medium text-[#465bb8]">{timezone}</span>
          </span>
        </div>
      ) : null}

      <FieldRow label="调度周期" required>
        <Select
          className="w-[228px] max-w-full"
          value={config.period}
          disabled={disabled}
          options={PERIOD_OPTIONS}
          onChange={(period) => patch({ period })}
        />
      </FieldRow>

      {renderPeriodFields()}

      {showEffectiveDate ? (
        <FieldRow
          label="生效日期"
          required
          help="生效区间由调度服务控制，不写入 Cron 表达式。"
        >
          <div className="flex min-w-0 flex-col gap-2.5">
            <Radio.Group
              value={effectiveType}
              disabled={disabled}
              onChange={(event) => {
                if (event.target.value === 'forever') {
                  onEffectiveRangeChange?.(undefined);
                  return;
                }
                onEffectiveRangeChange?.([
                  dayjs().startOf('day'),
                  dayjs().add(1, 'year').endOf('day'),
                ]);
              }}
            >
              <Radio value="forever">永久生效</Radio>
              <Radio value="range">指定时间</Radio>
            </Radio.Group>
            {effectiveType === 'range' ? (
              <DatePicker.RangePicker
                showTime
                className="w-full max-w-[456px]"
                value={effectiveRange}
                disabled={disabled}
                onChange={(next) =>
                  onEffectiveRangeChange?.(
                    next?.[0] && next?.[1] ? [next[0], next[1]] : undefined,
                  )
                }
              />
            ) : null}
          </div>
        </FieldRow>
      ) : null}

      <FieldRow label="Cron表达式" help="六段式：秒 分 时 日 月 周。">
        <div className="min-w-0 max-w-full">
          {manualMode ? (
            <div className="w-full max-w-[456px]">
              <Input
                value={value}
                disabled={disabled}
                className="font-mono text-[12px]"
                placeholder="0 0 2 * * ?"
                onChange={(event) => {
                  previousExternalValue.current = event.target.value;
                  onChange?.(event.target.value);
                }}
              />
              <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
                当前表达式包含可视化编辑器尚未覆盖的高级语法；将原样保留。修改上方周期配置后会切换为可视化生成。
              </div>
            </div>
          ) : (
            <div className="flex min-h-8 items-center gap-1.5">
              <code className="break-all font-mono text-[12px] text-[#344054]">
                {cronExpression}
              </code>
              <Tooltip title="复制 Cron 表达式">
                <button
                  type="button"
                  aria-label="复制 Cron 表达式"
                  disabled={disabled}
                  className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md border-0 bg-transparent text-[#6676d8] transition-colors hover:bg-[#f2f4ff] disabled:cursor-not-allowed disabled:text-[#c8ccd4]"
                  onClick={async () => {
                    try {
                      await copyText(cronExpression);
                      message.success('Cron 表达式已复制');
                    } catch {
                      message.error('复制失败，请手动复制');
                    }
                  }}
                >
                  <Copy size={14} />
                </button>
              </Tooltip>
            </div>
          )}
        </div>
      </FieldRow>
    </div>
  );
}
