import type {
  AlertLevel,
  ComparisonOperator,
  MonitorSettingsPayload,
  MonitorSettingsView,
  MonitorView,
  NotifyChannel,
  RuleFailureAction,
  RuleType,
  SaveRulePayload,
  ScheduleFrequency,
  ScheduleWeekday,
  TemplateView,
} from '../../types';

export interface EditorRule extends SaveRulePayload {
  key: string;
  templateCode: string;
  ruleType: RuleType;
  scope: 'TABLE' | 'COLUMN';
  dimension: string;
}

/** Canonical editor state: manual execution is an action, scheduling is an optional capability. */
export interface ScheduleSettingsState {
  scheduleEnabled: boolean;
  cronExpression: string;
  ruleFailureAction: RuleFailureAction;
}

export interface NotificationSettingsState {
  notifyEnabled: boolean;
  notifyChannel: NotifyChannel;
  notifyTarget: string;
  alertLevel: AlertLevel;
}

export const DEFAULT_SCHEDULE: ScheduleSettingsState = {
  scheduleEnabled: false,
  cronExpression: '0 0 9 * * ?',
  ruleFailureAction: 'CONTINUE',
};

export const DEFAULT_NOTIFICATION: NotificationSettingsState = {
  notifyEnabled: false,
  notifyChannel: 'MESSAGE',
  notifyTarget: '',
  alertLevel: 'WARNING',
};

export const OPERATORS: Array<{ value: ComparisonOperator; label: string }> = [
  { value: 'GT', label: '>' },
  { value: 'GTE', label: '>=' },
  { value: 'EQ', label: '=' },
  { value: 'LTE', label: '<=' },
  { value: 'LT', label: '<' },
  { value: 'BETWEEN', label: '区间' },
];

interface ParameterDefaults {
  defaultOperator?: ComparisonOperator;
  defaultThreshold?: number;
  defaultThresholdEnd?: number;
  defaultSql?: string;
}

const parseDefaults = (value?: string): ParameterDefaults => {
  if (!value) return {};
  try {
    return JSON.parse(value) as ParameterDefaults;
  } catch {
    return {};
  }
};

export const ruleDefaults = (template: TemplateView): EditorRule => {
  const schema = parseDefaults(template.parameterSchema);
  const percentRule =
    template.ruleType === 'COLUMN_NOT_NULL'
    || template.ruleType === 'COLUMN_UNIQUE';
  const fallbackOperator: ComparisonOperator =
    template.ruleType === 'TABLE_ROW_COUNT'
      ? 'GT'
      : percentRule
        ? 'GTE'
        : 'EQ';
  return {
    key: `${template.id}-${Date.now()}-${Math.random()}`,
    templateId: template.id,
    templateCode: template.code,
    name: template.name,
    ruleType: template.ruleType,
    scope: template.scope,
    dimension: template.dimension,
    operator: schema.defaultOperator || fallbackOperator,
    threshold: schema.defaultThreshold ?? (percentRule ? 100 : 0),
    thresholdEnd: schema.defaultThresholdEnd,
    enumValues: [],
    customSql:
      template.ruleType === 'CUSTOM_SQL'
        ? template.templateSql
          || schema.defaultSql
          || 'SELECT COUNT(*) AS metric_value FROM ${table} WHERE ${where}'
        : undefined,
    enabled: true,
  };
};

export const monitorRules = (monitor: MonitorView): EditorRule[] =>
  monitor.rules.map((rule) => ({
    key: String(rule.id),
    templateId: rule.templateId,
    templateCode: rule.templateCode,
    name: rule.name,
    ruleType: rule.ruleType,
    scope: rule.scope,
    dimension: rule.dimension,
    columnName: rule.columnName,
    operator: (rule.operator || 'EQ') as ComparisonOperator,
    threshold: rule.threshold,
    thresholdEnd: rule.thresholdEnd,
    enumValues: rule.enumValues || [],
    customSql: rule.customSql,
    enabled: rule.enabled,
  }));

const WEEKDAY_TO_QUARTZ: Record<ScheduleWeekday, number> = {
  SUN: 1,
  MON: 2,
  TUE: 3,
  WED: 4,
  THU: 5,
  FRI: 6,
  SAT: 7,
};

const legacyScheduleCron = (settings: MonitorSettingsView): string | undefined => {
  const explicit = settings.cronExpression?.trim();
  if (explicit) return explicit;

  const frequency = settings.scheduleFrequency as ScheduleFrequency | undefined;
  if (frequency === 'CRON') return undefined;
  if (frequency !== 'DAILY' && frequency !== 'WEEKLY') return undefined;

  const [hour = '09', minute = '00'] = (settings.scheduleTime || '09:00').split(':');
  if (frequency === 'DAILY') {
    return `0 ${Number(minute)} ${Number(hour)} * * ?`;
  }

  const weekday = settings.scheduleWeekday as ScheduleWeekday | undefined;
  if (!weekday) return undefined;
  return `0 ${Number(minute)} ${Number(hour)} ? * ${WEEKDAY_TO_QUARTZ[weekday]}`;
};

/** Hydrates both the new canonical contract and historical DAILY/WEEKLY settings. */
export const scheduleFromSettings = (
  settings: MonitorSettingsView,
): ScheduleSettingsState => ({
  scheduleEnabled:
    settings.scheduleEnabled ?? settings.runMode === 'SCHEDULE',
  cronExpression:
    legacyScheduleCron(settings) || DEFAULT_SCHEDULE.cronExpression,
  ruleFailureAction: settings.ruleFailureAction || 'CONTINUE',
});

export const notificationFromSettings = (
  settings: MonitorSettingsView,
): NotificationSettingsState => ({
  notifyEnabled: Boolean(settings.notifyEnabled),
  notifyChannel: settings.notifyChannel || 'MESSAGE',
  notifyTarget: settings.notifyTarget || '',
  alertLevel: settings.alertLevel || 'WARNING',
});

export const buildSettings = (
  schedule: ScheduleSettingsState,
  notification: NotificationSettingsState,
): MonitorSettingsPayload => ({
  scheduleEnabled: schedule.scheduleEnabled,
  cronExpression: schedule.cronExpression.trim() || undefined,
  ruleFailureAction: schedule.ruleFailureAction,
  notifyEnabled: notification.notifyEnabled,
  notifyChannel: notification.notifyChannel,
  notifyTarget:
    notification.notifyChannel === 'MESSAGE'
      ? undefined
      : notification.notifyTarget.trim() || undefined,
  alertLevel: notification.alertLevel,
});

export const validateEditorSettings = (
  schedule: ScheduleSettingsState,
  notification: NotificationSettingsState,
) => {
  if (schedule.scheduleEnabled && !schedule.cronExpression.trim()) {
    throw new Error('启用调度时请输入 Cron 表达式');
  }

  if (
    notification.notifyEnabled
    && notification.notifyChannel !== 'MESSAGE'
    && !notification.notifyTarget.trim()
  ) {
    throw new Error(
      notification.notifyChannel === 'EMAIL'
        ? '请输入告警接收邮箱'
        : '请输入 Webhook 地址',
    );
  }
};
