import type {
  CollectProviderType,
  CollectRunStatus,
  CollectRunRecord,
  CollectTriggerType,
} from '@/services/metadata/types';

export const PROVIDER_LABELS: Record<CollectProviderType, string> = {
  HARVESTED: '物理采集',
  REGISTERED: '投影对账',
};

export const PROVIDER_COLORS: Record<CollectProviderType, string> = {
  HARVESTED: 'geekblue',
  REGISTERED: 'purple',
};

export const RUN_STATUS_LABELS: Record<CollectRunStatus, string> = {
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  SUSPECT: '已熔断',
};

export const RUN_STATUS_COLORS: Record<CollectRunStatus, string> = {
  RUNNING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  SUSPECT: 'warning',
};

export const TRIGGER_LABELS: Record<CollectTriggerType, string> = {
  SCHEDULE: '定时',
  MANUAL: '立即运行',
  DRY_RUN: '预演',
};

/** 后端 LocalDateTime 的 ISO 串 → 'YYYY-MM-DD HH:mm:ss'（与 lifecycle 同法，不引新依赖）。 */
export { formatMetadataTime } from '@/services/metadata/presentation';

export const formatDuration = (ms?: number | null): string => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return rest > 0 ? `${minutes} 分 ${rest} 秒` : `${minutes} 分`;
};

/** 四个计数的人话摘要：dry-run 弹窗与历史行共用一份口径。 */
export const summarizeRunCounts = (run: CollectRunRecord): string => {
  const parts = [
    `共 ${run.cntTotal ?? 0}`,
    `新增 ${run.cntNew ?? 0}`,
    `变更 ${run.cntChanged ?? 0}`,
    `未变 ${run.cntUnchanged ?? 0}`,
  ];
  if ((run.cntGone ?? 0) > 0) parts.push(`消失 ${run.cntGone}`);
  if ((run.cntPartialFailed ?? 0) > 0) parts.push(`部分失败 ${run.cntPartialFailed}`);
  return parts.join(' · ');
};
