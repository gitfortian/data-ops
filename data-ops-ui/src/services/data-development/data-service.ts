import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId, DevelopmentSqlResultColumn } from './types';

const NODE_API = '/api/v1/data-development/nodes';

export type DataServiceContractType =
  | 'STRING'
  | 'INTEGER'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'OBJECT';

export interface DevelopmentDataServiceParameter {
  name: string;
  type: DataServiceContractType;
  required: boolean;
  description?: string | null;
  example?: string | null;
}

export interface DevelopmentDataServiceResponseField {
  name: string;
  type: DataServiceContractType;
  nullable: boolean;
  description?: string | null;
  example?: string | null;
}

export interface DevelopmentDataServiceDefinition {
  sourceTaskAssetId?: DevelopmentId;
  sourceTaskRevisionId?: DevelopmentId;
  sourceTaskRevisionNo?: number;
  dataSourceId: DevelopmentId;
  sql: string;
  serviceName: string;
  path: string;
  method: 'GET';
  parameters: DevelopmentDataServiceParameter[];
  responseFields: DevelopmentDataServiceResponseField[];
  maxRows: number;
  timeoutSeconds: number;
  description?: string | null;
  paginationEnabled: boolean;
  autoParseParameters: boolean;
}

export interface DevelopmentDataServiceDraft {
  nodeId: DevelopmentId;
  definition: DevelopmentDataServiceDefinition;
  draftRevision: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface DevelopmentDataServiceRevisionSummary {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  sourceTaskRevisionId?: DevelopmentId;
  sourceTaskRevisionNo?: number;
  checksum: string;
  createTime?: string;
}

export interface DevelopmentDataServiceRevision {
  id: DevelopmentId;
  nodeId: DevelopmentId;
  revisionNo: number;
  sourceDraftRevision: number;
  definition: DevelopmentDataServiceDefinition;
  checksum: string;
  createTime?: string;
}

export interface DevelopmentDataServiceNodeContext {
  nodeId: DevelopmentId;
  nodeName: string;
  configured: boolean;
  draft: DevelopmentDataServiceDraft;
  latestPublishedRevision?: DevelopmentDataServiceRevisionSummary | null;
  revisions: DevelopmentDataServiceRevisionSummary[];
}

export interface PreviewDevelopmentDataServiceSqlResult {
  columns: DevelopmentSqlResultColumn[];
  rows: unknown[][];
  returnedRows: number;
  truncated: boolean;
}

export interface PreviewDevelopmentDataServiceResult {
  dataSourceId: DevelopmentId;
  parameters: DevelopmentDataServiceParameter[];
  responseFields: DevelopmentDataServiceResponseField[];
  result: PreviewDevelopmentDataServiceSqlResult;
  durationMs: number;
}

export interface SaveDevelopmentDataServiceDraftPayload {
  dataSourceId: DevelopmentId;
  sql: string;
  serviceName: string;
  path: string;
  method: 'GET';
  parameters: DevelopmentDataServiceParameter[];
  responseFields: DevelopmentDataServiceResponseField[];
  maxRows: number;
  timeoutSeconds: number;
  description?: string;
  paginationEnabled: boolean;
  autoParseParameters: boolean;
  baseRevision: number;
}

const nodePath = (nodeId: DevelopmentId) =>
  `${NODE_API}/${encodeURIComponent(nodeId)}/data-service`;

export const getDevelopmentDataServiceNode = (
  nodeId: DevelopmentId,
): Promise<DevelopmentDataServiceNodeContext> =>
  HttpUtils.getData<DevelopmentDataServiceNodeContext>(nodePath(nodeId));

export const previewDevelopmentDataServiceNode = (
  nodeId: DevelopmentId,
  dataSourceId: DevelopmentId,
  sql: string,
  maxRows = 1000,
  timeoutSeconds = 30,
  parameterValues: Record<string, unknown> = {},
): Promise<PreviewDevelopmentDataServiceResult> =>
  HttpUtils.postData<PreviewDevelopmentDataServiceResult>(
    `${nodePath(nodeId)}/preview`,
    { dataSourceId, sql, maxRows, timeoutSeconds, parameterValues },
  );

export const saveDevelopmentDataServiceDraft = (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentDataServiceDraftPayload,
): Promise<DevelopmentDataServiceNodeContext> =>
  HttpUtils.putData<DevelopmentDataServiceNodeContext>(
    `${nodePath(nodeId)}/draft`,
    payload,
  );

export const publishDevelopmentDataServiceNode = (
  nodeId: DevelopmentId,
  expectedDraftRevision: number,
): Promise<DevelopmentDataServiceRevision> =>
  HttpUtils.postData<DevelopmentDataServiceRevision>(
    `${nodePath(nodeId)}/publish`,
    { expectedDraftRevision },
  );

export const listDevelopmentDataServiceRevisions = (
  nodeId: DevelopmentId,
): Promise<DevelopmentDataServiceRevisionSummary[]> =>
  HttpUtils.getData<DevelopmentDataServiceRevisionSummary[]>(
    `${nodePath(nodeId)}/revisions`,
  );
