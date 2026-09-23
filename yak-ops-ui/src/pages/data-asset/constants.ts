import dayjs from 'dayjs';

import type {
  AssetGapCode,
  AssetSourceType,
  AssetStatus,
  AssetType,
  ChangeHandleStatus,
  ChangeType,
  DirNode,
  HealthGrade,
  RuleType,
} from '@/services/data-asset/types';

export const ASSET_STATUS_LABELS: Record<AssetStatus, string> = {
  PENDING: '待上架',
  PUBLISHED: '已上架',
  OFFLINE: '已下架',
  IGNORED: '已忽略',
  SOURCE_GONE: '源已消失',
};

export const ASSET_STATUS_COLORS: Record<AssetStatus, string> = {
  PENDING: 'orange',
  PUBLISHED: 'green',
  OFFLINE: 'default',
  IGNORED: 'default',
  SOURCE_GONE: 'red',
};

export const ASSET_SOURCE_TYPE_LABELS: Record<AssetSourceType, string> = {
  MODEL: '模型',
  METRIC: '指标',
  METADATA: '物理表',
  DATASET: '数据集',
  DASHBOARD: '仪表盘',
  CHART: '图表',
  TASK: '任务',
  MANUAL: '手工登记',
};

export const ASSET_TYPE_LABELS: Record<AssetType, string> = {
  TABLE: '表',
  METRIC: '指标',
  DATASET: '数据集',
  DASHBOARD: '仪表盘',
  CHART: '图表',
  TASK: '任务',
  DOC: '文档',
};

/** A绿/B蓝/C橙/D红(design §十 视觉口径)。 */
export const HEALTH_GRADE_COLORS: Record<HealthGrade, string> = {
  A: '#52c41a',
  B: '#1677ff',
  C: '#fa8c16',
  D: '#f5222d',
};

export const healthGradeColor = (grade?: string | null) =>
  HEALTH_GRADE_COLORS[(grade ?? '') as HealthGrade] ?? '#98a2b3';

export const GAP_LABELS: Record<AssetGapCode, string> = {
  OWNER_MISSING: '缺负责人',
  DESCRIPTION_MISSING: '缺描述',
  DIRECTORY_MISSING: '未入目录',
  SECURITY_LEVEL_SUGGESTED: '建议安全定级',
};

export const HEALTH_DIMENSION_LABELS: Record<string, string> = {
  COMPLETENESS: '完整性',
  TRUST: '可信度',
  ACTIVITY: '活跃度',
};

export const HEALTH_ITEM_LABELS: Record<string, string> = {
  description: '描述',
  owner: '负责人',
  directory: '已入目录',
  tags: '业务标签',
  field_comments: '字段注释覆盖率',
  quality: '质量监控',
  lineage: '血缘已登记',
  security: '安全已定级',
  changes_confirmed: '变更已确认',
  views: '近30天浏览',
  freshness: '更新新鲜度',
  downstream: '下游引用',
};

export const HEALTH_STATE_LABELS: Record<string, string> = {
  OK: '正常',
  NOT_APPLICABLE: '不适用(剔出分母)',
  UNAVAILABLE: '数据不可用(计 0 分)',
};

export const SORT_LABELS: Record<string, string> = {
  '': '推荐(健康×活跃度)',
  TIME: '更新时间',
  VIEWS: '近30天浏览',
  NAME: '名称',
};

export const CHANGE_TYPE_LABELS: Record<ChangeType, string> = {
  NEW: '新登记',
  META_CHANGED: '元数据变化',
  SOURCE_GONE: '源消失',
  REAPPEARED: '源重新出现',
};

export const CHANGE_TYPE_COLORS: Record<ChangeType, string> = {
  NEW: 'blue',
  META_CHANGED: 'gold',
  SOURCE_GONE: 'red',
  REAPPEARED: 'green',
};

export const HANDLE_STATUS_LABELS: Record<ChangeHandleStatus, string> = {
  OPEN: '待确认',
  CONFIRMED: '已确认',
  IGNORED: '已忽略',
};

export const HANDLE_STATUS_COLORS: Record<ChangeHandleStatus, string> = {
  OPEN: 'orange',
  CONFIRMED: 'green',
  IGNORED: 'default',
};

export const RULE_TYPE_LABELS: Record<RuleType, string> = {
  DIRECTORY: '自动入目录',
  TAG: '自动打标',
};

/** 概览分布行的键翻译(未知键原样展示)。 */
export const distributionLabel = (group: 'status' | 'grade' | 'type' | 'layer', key: string) => {
  if (key === 'NONE') return '未评分';
  if (key === 'UNSET') return '未分层';
  switch (group) {
    case 'status':
      return ASSET_STATUS_LABELS[key as AssetStatus] ?? key;
    case 'type':
      return ASSET_TYPE_LABELS[key as AssetType] ?? key;
    default:
      return key;
  }
};

/** 源对象跳转(详情"源对象"链接,menu.md 跳转契约)。 */
export const sourceObjectPath = (
  sourceType?: string,
  sourceId?: string,
  returnAssetId?: number,
): string | undefined => {
  if (!sourceId) return undefined;
  let path: string | undefined;
  switch (sourceType) {
    case 'MODEL':
      path = `/modeling/models/${sourceId}`;
      break;
    case 'METRIC':
      path = `/metric/manage/${sourceId}`;
      break;
    case 'METADATA':
      // sourceId 是目录行 id，实体视图暂不支持按 id 定位，先跳视图本身
      path = '/data-asset/catalog?view=entity';
      break;
    case 'DATASET':
      path = `/dataset/${sourceId}`;
      break;
    case 'DASHBOARD':
      path = `/dashboard/${sourceId}`;
      break;
    case 'TASK':
      path = `/data-development/task/${sourceId}`;
      break;
    default:
      return undefined;
  }
  if (sourceType !== 'MODEL' || !returnAssetId) return path;
  return `${path}${path.includes('?') ? '&' : '?'}returnAssetId=${returnAssetId}`;
};

export const formatAssetTime = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';

export const collectDirOptions = (nodes: DirNode[]): { value: number; label: string }[] =>
  nodes.flatMap((node) => [
    { value: node.id, label: node.dirName },
    ...collectDirOptions(node.children ?? []),
  ]);

export interface DirTreeNode {
  key: number;
  title: string;
  builtin: boolean;
  children?: DirTreeNode[];
}

export const toTreeData = (nodes: DirNode[]): DirTreeNode[] =>
  nodes.map((node) => ({
    key: node.id,
    title: node.dirName,
    builtin: node.builtin,
    children: node.children?.length ? toTreeData(node.children) : undefined,
  }));
