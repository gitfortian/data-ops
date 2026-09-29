import type {
  DatasetFieldDataType,
  DatasetFieldRole,
  DatasetSourceType,
  DatasetStatus,
} from '@/services/dataset';

export type CatalogDatasetStatus = DatasetStatus;
export type CatalogDatasetSourceType = DatasetSourceType;
export type CatalogDatasetFieldRole = DatasetFieldRole;
export type CatalogDatasetFieldType = DatasetFieldDataType;

export interface CatalogDirectory {
  id: string;
  parentId?: string;
  name: string;
  path: string;
}

export interface CatalogDatasetVersion {
  id: string;
  versionNo: number;
  sourceType: CatalogDatasetSourceType;
  sourceTaskAssetId?: string;
  sourceTaskRevisionId?: string;
  sourceTaskRevisionNo?: number;
  dataSourceId?: string;
  sql?: string;
  createTime?: string;
}

export interface CatalogDatasetField {
  fieldId: string;
  physicalName: string;
  displayName: string;
  dataType: CatalogDatasetFieldType;
  nullable: boolean;
  description?: string;
  defaultRole: CatalogDatasetFieldRole;
  sortOrder: number;
}

export interface CatalogDataset {
  id: string;
  name: string;
  description: string;
  status: CatalogDatasetStatus;
  currentVersionId?: string;
  currentVersion?: CatalogDatasetVersion;
  versions: CatalogDatasetVersion[];
  fields: CatalogDatasetField[];
  createTime?: string;
  updateTime?: string;
  analysisCount: number;
  /** Current source display label. Historical property name retained for compatibility. */
  sourceTaskName?: string;
  sourceNodeId?: string;
  directoryId?: string;
  directoryPath?: string;
}

export interface CatalogWorkspace {
  datasets: CatalogDataset[];
  directories: CatalogDirectory[];
}

export const LINEAGE_ASSET_TYPES = [
  'TABLE',
  'COLUMN',
  'SQL_TASK',
  'DATASET',
  'DATASET_FIELD',
  'CHART',
  'DASHBOARD',
  'METRIC',
] as const;

export type LineageAssetType = (typeof LINEAGE_ASSET_TYPES)[number];
export type LineageDirection = 'BOTH' | 'UPSTREAM' | 'DOWNSTREAM';
export type LineageRelationType =
  | 'READS_FROM'
  | 'WRITES_TO'
  | 'DERIVES_FROM'
  | 'CONSUMES'
  | 'CONTAINS';

export interface LineageAsset {
  id: string;
  assetKey: string;
  assetType: LineageAssetType;
  name: string;
  sourceType?: string;
  sourceId?: string;
  parentAssetId?: string;
  dataSourceId?: string;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
  columnName?: string;
  properties?: Record<string, unknown>;
  createTime?: string;
  updateTime?: string;
}

export interface LineageRelation {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  relationType: LineageRelationType;
  sourceType?: string;
  sourceId?: string;
  expression?: string;
  confidence?: number;
  version?: string;
  observedAt?: string;
  properties?: Record<string, unknown>;
  createTime?: string;
  updateTime?: string;
}

export interface LineageGraph {
  root: LineageAsset;
  direction: LineageDirection;
  depth: number;
  nodes: LineageAsset[];
  relations: LineageRelation[];
}

export const assetTypeLabel: Record<LineageAssetType, string> = {
  TABLE: '数据表',
  COLUMN: '字段',
  SQL_TASK: 'SQL 任务',
  DATASET: 'Dataset',
  DATASET_FIELD: 'Dataset 字段',
  CHART: '图表',
  DASHBOARD: '仪表盘',
  METRIC: '指标',
};

export const relationTypeLabel: Record<LineageRelationType, string> = {
  READS_FROM: '读取',
  WRITES_TO: '写入',
  DERIVES_FROM: '派生',
  CONSUMES: '消费',
  CONTAINS: '包含',
};
