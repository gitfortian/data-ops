import type {
  DatasetFieldDataType,
  DatasetFieldRole as DatasetFieldWireRole,
  DatasetSourceType,
  DatasetStatus,
} from './types';

export type DatasetFieldRole = 'dimension' | 'metric';
export type DatasetFieldType = 'string' | 'number' | 'date' | 'datetime' | 'boolean' | 'unknown';
export type Scalar = string | number | boolean | null;

export interface DatasetField {
  key: string;
  label: string;
  physicalName: string;
  dataType: DatasetFieldType;
  role: DatasetFieldRole;
  nullable: boolean;
  description?: string;
}

export interface PublishedDataset {
  id: string;
  name: string;
  description: string;
  status: DatasetStatus;
  sourceTaskId: string;
  sourceTaskName: string;
  currentVersionNo?: number;
  updatedAt: string;
  fields: DatasetField[];
}

/** Dataset version snapshot used by management surfaces. */
export interface DatasetManagementVersion {
  id: string;
  datasetId: string;
  versionNo: number;
  sourceType: DatasetSourceType;
  sourceTaskAssetId?: string;
  sourceTaskRevisionId?: string;
  sourceTaskRevisionNo?: number;
  dataSourceId?: string;
  sql?: string;
  schemaSnapshot?: string;
  createTime?: string;
}

/** Current-version field metadata used by Dataset management surfaces. */
export interface DatasetManagementField {
  fieldId: string;
  versionId: string;
  physicalName: string;
  displayName: string;
  dataType: DatasetFieldDataType;
  nullable: boolean;
  description?: string;
  defaultRole: DatasetFieldWireRole;
  sortOrder: number;
}

/** Lightweight Dataset catalog row. Versions are loaded lazily on the detail page. */
export interface DatasetManagementItem {
  id: string;
  name: string;
  description: string;
  status: DatasetStatus;
  currentVersionId?: string;
  currentVersion?: DatasetManagementVersion;
  fields: DatasetManagementField[];
  createTime?: string;
  updateTime?: string;
}

export interface DatasetManagementDetail extends DatasetManagementItem {
  versions: DatasetManagementVersion[];
}

export type Aggregation = 'SUM' | 'AVG' | 'COUNT' | 'COUNT_DISTINCT' | 'MAX' | 'MIN';

export interface DatasetQueryMetric {
  fieldId: string;
  aggregation: Aggregation;
}

export interface DatasetQueryFilter {
  fieldId: string;
  operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'LIKE';
  value: Scalar;
}

export interface DatasetQuerySort {
  fieldId: string;
  aggregation?: Aggregation;
  direction: 'ASC' | 'DESC';
}

export interface DatasetQueryPayload {
  /** Omit to query the Dataset currentVersionId snapshot. */
  versionNo?: number;
  dimensions: string[];
  metrics: DatasetQueryMetric[];
  filters: DatasetQueryFilter[];
  sorts: DatasetQuerySort[];
  limit: number;
  timeoutSeconds: number;
}

export interface DatasetQueryColumnBinding {
  key: string;
  fieldId: string;
  displayName: string;
  dataType: string;
  aggregation?: Aggregation | null;
}

export interface DatasetQueryColumn {
  name: string;
  label: string;
  typeName: string;
  jdbcType: number;
  nullable: boolean;
}

export interface DatasetQueryResult {
  queryId?: string;
  datasetId: string;
  datasetVersionId: string;
  datasetVersionNo: number;
  bindings: DatasetQueryColumnBinding[];
  columns: DatasetQueryColumn[];
  rows: Scalar[][];
  returnedRows: number;
  truncated: boolean;
  elapsedMillis: number;
}

export type DatasetQueryStatus = 'SUCCESS' | 'REJECTED' | 'FAILED' | 'TIMEOUT';

/** Persisted, privacy-safe diagnostics for one Dataset Query Runtime attempt. */
export interface DatasetQueryPerformance {
  queryId: string;
  datasetId: string;
  datasetName?: string | null;
  datasetVersionId?: string | null;
  datasetVersionNo?: number | null;
  sourceType?: DatasetSourceType | null;
  dataSourceId?: string | null;
  /** Literal values are redacted by the backend before this preview is persisted. */
  sql?: string | null;
  sqlHash?: string | null;
  status: DatasetQueryStatus;
  failureStage?: string | null;
  errorType?: string | null;
  errorMessage?: string | null;
  waitMillis: number;
  prepareMillis: number;
  executeMillis: number;
  transferMillis: number;
  totalMillis: number;
  returnedRows: number;
  truncated: boolean;
  startedAt?: string;
  finishedAt?: string;
}

export interface DatasetQueryPerformanceQuery {
  datasetIds?: string[];
  queryIds?: string[];
  statuses?: DatasetQueryStatus[];
  minTotalMillis?: number;
  limit?: number;
}
