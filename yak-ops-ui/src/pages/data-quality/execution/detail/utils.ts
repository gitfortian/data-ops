import dayjs from 'dayjs';

import type {
  ExecutionWorkspaceRuleView,
  ExecutionWorkspaceView,
} from '../types';

export type ExecutionDetailTabKey =
  | 'overview'
  | 'history'
  | 'issues'
  | 'logs';

export const formatExecutionTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';

export const formatExecutionDuration = (value?: number) => {
  if (value === undefined || value === null) return '--';
  if (value < 1000) return `${value} ms`;

  const seconds = Math.floor(value / 1000);
  if (seconds < 60) return `${seconds} 秒`;

  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  if (minutes < 60) return `${minutes} 分 ${restSeconds} 秒`;

  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  return `${hours} 小时 ${restMinutes} 分`;
};

export const qualityExecutionTriggerLabel = (
  value?: ExecutionWorkspaceView['triggerType'],
) => (value === 'SCHEDULE' ? '调度触发' : '手动触发');

export const qualityRuleScopeLabel = (
  value: ExecutionWorkspaceRuleView['scope'],
) => (value === 'TABLE' ? '表级' : '字段级');

export const qualityExecutionIssueCount = (
  record?: Pick<ExecutionWorkspaceView, 'failedRules' | 'errorRules'>,
) => (record?.failedRules || 0) + (record?.errorRules || 0);
