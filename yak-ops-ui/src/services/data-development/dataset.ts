import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId, DevelopmentSqlResultColumn } from './types';

const NODE_API = '/api/v1/data-development/nodes';

export type DevelopmentDatasetStatus = 'ONLINE' | 'OFFLINE';
export type DevelopmentDatasetFieldRole = 'DIMENSION' | 'MEASURE';
export type DevelopmentDatasetFieldType =
  | 'STRING'
  | 'NUMBER'
  | 'DATE'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'UNKNOWN';

export interface DevelopmentDatasetFieldDraft {
  fieldId?: string | null;
  physicalName: string;
  displayName: string;
  dataType: DevelopmentDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DevelopmentDatasetFieldRole;
}

export interface DevelopmentDatasetNodeVersion {
  versionId: DevelopmentId;
  versionNo: number;
  sourceType: string;
  sourceTaskAssetId?: DevelopmentId;
  sourceTaskRevisionId?: DevelopmentId;
  sourceTaskRevisionNo?: number;
  dataSourceId?: DevelopmentId | null;
  sql?: string | null;
  createTime?: string;
}

export interface DevelopmentDatasetNodeField {
  fieldId: string;
  physicalName: string;
  displayName: string;
  dataType: DevelopmentDatasetFieldType;
  nullable: boolean;
  description?: string | null;
  defaultRole: DevelopmentDatasetFieldRole;
  sortOrder: number;
}

export interface DevelopmentDatasetNodeAsset {
  developmentNodeId: DevelopmentId;
  datasetId: DevelopmentId;
  name: string;
  description?: string | null;
  status: DevelopmentDatasetStatus;
  currentVersion?: DevelopmentDatasetNodeVersion | null;
  versions: DevelopmentDatasetNodeVersion[];
  fields: DevelopmentDatasetNodeField[];
  draftDataSourceId?: DevelopmentId | null;
  draftSql?: string | null;
  draftFields?: DevelopmentDatasetFieldDraft[] | null;
  createTime?: string;
  updateTime?: string;
}

export interface DevelopmentDatasetNodeContext {
  nodeId: DevelopmentId;
  nodeName: string;
  configured: boolean;
  dataset?: DevelopmentDatasetNodeAsset | null;
}

export interface DevelopmentDatasetQueryResult {
  fields: DevelopmentDatasetFieldDraft[];
  columns: DevelopmentSqlResultColumn[];
  rows: unknown[][];
  returnedRows: number;
  truncated: boolean;
  durationMs: number;
}

export interface SaveDevelopmentDatasetNodePayload {
  dataSourceId: DevelopmentId;
  sql: string;
  description?: string;
  fields?: DevelopmentDatasetFieldDraft[];
}

const nodePath = (nodeId: DevelopmentId) =>
  `${NODE_API}/${encodeURIComponent(nodeId)}/dataset`;

export const getDevelopmentDatasetNode = (
  nodeId: DevelopmentId,
): Promise<DevelopmentDatasetNodeContext> =>
  HttpUtils.getData<DevelopmentDatasetNodeContext>(nodePath(nodeId));

export const previewDevelopmentDatasetNode = (
  nodeId: DevelopmentId,
  dataSourceId: DevelopmentId,
  sql: string,
): Promise<DevelopmentDatasetFieldDraft[]> =>
  HttpUtils.postData<DevelopmentDatasetFieldDraft[]>(
    `${nodePath(nodeId)}/preview`,
    { dataSourceId, sql },
  );

export const runDevelopmentDatasetNode = (
  nodeId: DevelopmentId,
  dataSourceId: DevelopmentId,
  sql: string,
): Promise<DevelopmentDatasetQueryResult> =>
  HttpUtils.postData<DevelopmentDatasetQueryResult>(
    `${nodePath(nodeId)}/query`,
    { dataSourceId, sql },
  );

export const saveDevelopmentDatasetNode = (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentDatasetNodePayload,
): Promise<DevelopmentDatasetNodeContext> =>
  HttpUtils.putData<DevelopmentDatasetNodeContext>(nodePath(nodeId), payload);

export const publishDevelopmentDatasetNode = (
  nodeId: DevelopmentId,
): Promise<DevelopmentDatasetNodeContext> =>
  HttpUtils.postData<DevelopmentDatasetNodeContext>(
    `${nodePath(nodeId)}/publish`,
    {},
  );

// ── 指标中心引用(01 消费接线；服务端经 MetricUsageApi SPI 落 yak_metric_usage) ──

export const getDevelopmentDatasetMetricRefs = (nodeId: DevelopmentId): Promise<number[]> =>
  HttpUtils.getData<number[]>(`${nodePath(nodeId)}/metric-refs`);

export const syncDevelopmentDatasetMetricRefs = (
  nodeId: DevelopmentId,
  usageName: string,
  metricIds: number[],
): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${nodePath(nodeId)}/metric-refs`, { usageName, metricIds });
