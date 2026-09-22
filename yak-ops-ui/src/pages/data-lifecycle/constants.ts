import type {
  BindingSource,
  DispatchStatus,
  Granularity,
  LifecycleStatus,
  ModelState,
  PolicyScope,
  StorageType,
} from '@/services/data-lifecycle/types';

export const LAYER_LABELS: Record<string, string> = {
  ODS: 'ODS 贴源层',
  DIM: 'DIM 维度层',
  DWD: 'DWD 明细层',
  DWS: 'DWS 汇总层',
  ADS: 'ADS 应用层',
};

export const GRANULARITY_LABELS: Record<Granularity, string> = {
  DAY: '日',
  MONTH: '月',
  YEAR: '年',
};

export const POLICY_SCOPE_LABELS: Record<PolicyScope, string> = {
  LAYER_DEFAULT: '分层默认',
  CUSTOM: '自定义',
};

export const STATUS_LABELS: Record<LifecycleStatus, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
};

export const MODEL_STATE_LABELS: Record<ModelState, string> = {
  UNSET: '未配置',
  APPLIED: '已生效',
  DRIFT: '待下发',
  FAILED: '下发失败',
};

export const MODEL_STATE_COLORS: Record<ModelState, string> = {
  UNSET: 'default',
  APPLIED: 'green',
  DRIFT: 'orange',
  FAILED: 'red',
};

export const BINDING_SOURCE_LABELS: Record<BindingSource, string> = {
  OVERRIDE: '模型覆盖',
  LAYER_DEFAULT: '继承分层默认',
  LEGACY_LAYER: '分层旧配置(合成)',
  NONE: '未绑定',
};

export const STORAGE_TYPE_LABELS: Record<StorageType, string> = {
  DORIS: 'Doris',
  PAIMON: 'Paimon',
  UNSUPPORTED: '暂不支持',
};

export const DISPATCH_STATUS_LABELS: Record<DispatchStatus, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  RETRYING: '重试中',
  EXHAUSTED: '重试耗尽',
};

export const DISPATCH_STATUS_COLORS: Record<DispatchStatus, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  RETRYING: 'gold',
  EXHAUSTED: 'volcano',
};

/** 三段保留期展示:空=不限,销毁空=永久。 */
export const formatRetention = (hot?: number | null, cold?: number | null, destroy?: number | null) => {
  const one = (v?: number | null) => (v == null ? '不限' : `${v} 天`);
  return `热 ${one(hot)} / 冷 ${one(cold)} / 销毁 ${destroy == null ? '永久' : `${destroy} 天`}`;
};

export const formatBytes = (bytes?: number | null): string => {
  if (bytes == null) return '-';
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.min(Math.floor(Math.log2(bytes) / 10), units.length - 1);
  const v = bytes / 2 ** (10 * i);
  return `${v >= 100 ? Math.round(v) : v.toFixed(1)} ${units[i]}`;
};

export const formatLifecycleTime = (value?: string | null): string =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '-';
