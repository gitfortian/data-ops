import type {
  CatalogDatasetFieldRole,
  CatalogDatasetSourceType,
} from '@/services/data-analysis';
import type { QueryableDatasetSourceType } from '@/services/dataset';

export const DEFAULT_LEFT_WIDTH = 286;
export const MIN_LEFT_WIDTH = 220;
export const MAX_LEFT_WIDTH = 480;
export const LEFT_WIDTH_STORAGE_KEY = 'yak-data-catalog.left-width';
export const ROOT_KEY = 'catalog:root';
export const UNGROUPED_KEY = 'catalog:ungrouped';

export const SOURCE_TYPE_LABELS: Record<CatalogDatasetSourceType, string> = {
  QUERY_REVISION: 'SQL 任务',
  SQL_QUERY: 'Standalone SQL',
  TABLE: '数据表（未接入）',
  VIEW: '视图（未接入）',
};

export const QUERYABLE_SOURCE_TYPE_OPTIONS: Array<{
  label: string;
  value: QueryableDatasetSourceType;
}> = [
  { label: 'SQL 任务', value: 'QUERY_REVISION' },
  { label: 'Standalone SQL', value: 'SQL_QUERY' },
];

export const FIELD_ROLE_LABELS: Record<CatalogDatasetFieldRole, string> = {
  DIMENSION: '维度',
  MEASURE: '指标',
};

export const FIELD_TYPE_LABELS: Record<string, string> = {
  STRING: '文本',
  NUMBER: '数值',
  DATE: '日期',
  DATETIME: '时间',
  BOOLEAN: '布尔',
  UNKNOWN: '未知',
};
