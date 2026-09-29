import type { ModelingDialect } from '@/services/modeling/types';

/** 物理模型可选目标方言，与后端 ModelDialect 枚举保持一致。 */
export const MODELING_DIALECT_OPTIONS: { value: ModelingDialect; label: string }[] = [
  { value: 'MYSQL', label: 'MySQL' },
  { value: 'POSTGRESQL', label: 'PostgreSQL' },
  { value: 'ORACLE', label: 'Oracle' },
  { value: 'DORIS', label: 'Doris' },
  { value: 'CLICKHOUSE', label: 'ClickHouse' },
  { value: 'STARROCKS', label: 'StarRocks' },
];

/** dbType → 建模方言(同名映射;POSTGRES→POSTGRESQL)。 */
export const dialectOfDbType = (dbType?: string): string | undefined => {
  if (!dbType) return undefined;
  if (dbType.toUpperCase() === 'POSTGRES') return 'POSTGRESQL';
  return MODELING_DIALECT_OPTIONS.some((o) => o.value === dbType.toUpperCase()) ? dbType.toUpperCase() : undefined;
};

/** 简单 snake_case 转换（中文/特殊字符转下划线）。 */
export const toSnakeCase = (value: string): string =>
  value
    .trim()
    .replace(/[^\u4e00-\u9fa5a-zA-Z0-9\s]/g, '')
    .replace(/\s+/g, '_')
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .toLowerCase();

export const MODELING_DIALECT_LABELS: Record<string, string> = Object.fromEntries(
  MODELING_DIALECT_OPTIONS.map(({ value, label }) => [value, label]),
);

export const MODELING_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '停用',
};

/** 发布成功联动事件(01):直发或审批回调发布后广播,统一视图头部/版本面板据此刷新。 */
export const MODELING_PUBLISHED_EVENT = 'yak:modeling:published';

/** 状态列 tooltip(2026-09-17):说明各状态含义与流转。 */
export const MODELING_STATUS_HINTS: Record<string, string> = {
  DRAFT: '草稿：可编辑、未发布；保存发布后生成不可变版本快照',
  PUBLISHED: '已发布：结构只读，改版需基于最新版本新建草稿',
  DISABLED: '停用：不再参与建模与派生，可重新启用',
};

/** 后端 LocalDateTime 序列化为 ISO 字符串，列表/详情统一去掉 T 展示。 */
export const formatModelingTime = (value?: string): string => (value ? value.replace('T', ' ').slice(0, 19) : '-');

/** 列表更新时间(2026-09-17 布局收敛):去秒,配 nowrap 不换行。 */
export const formatModelingTimeMinute = (value?: string): string =>
  value ? value.replace('T', ' ').slice(0, 16) : '-';
