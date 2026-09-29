import type { MetricStatus, MetricType, StatPeriod } from '@/services/metric/types';

export const METRIC_TYPE_LABELS: Record<MetricType, string> = {
  ATOMIC: '原子指标',
  DERIVED: '派生指标',
  COMPOSITE: '复合指标',
};

export const METRIC_TYPE_COLORS: Record<MetricType, string> = {
  ATOMIC: 'blue',
  DERIVED: 'orange',
  COMPOSITE: 'purple',
};

export const METRIC_STATUS_LABELS: Record<MetricStatus, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

export const METRIC_STATUS_COLORS: Record<MetricStatus, string> = {
  ENABLED: 'green',
  DISABLED: 'default',
};

export const STAT_PERIOD_LABELS: Record<StatPeriod, string> = {
  DAY: '日',
  WEEK: '周',
  MONTH: '月',
};

export const METRIC_TYPE_OPTIONS = [
  { label: '全部', value: '' as const },
  { label: '原子指标', value: 'ATOMIC' },
  { label: '派生指标', value: 'DERIVED' },
  { label: '复合指标', value: 'COMPOSITE' },
];

export const METRIC_STATUS_OPTIONS = [
  { label: '全部', value: '' as const },
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

export const STAT_PERIOD_OPTIONS = [
  { label: '日', value: 'DAY' },
  { label: '周', value: 'WEEK' },
  { label: '月', value: 'MONTH' },
];

export const formatMetricTime = (value?: string): string =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '-';
