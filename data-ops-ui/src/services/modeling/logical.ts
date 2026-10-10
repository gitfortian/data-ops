import HttpUtils from '@/utils/HttpUtils';
import type { SemanticProcessRecord } from '@/services/semantic/types';

const ROOT = '/api/v1/modeling/logical-models';

/** Canonical owner is Modeling; Semantic data remains referenced, not copied. */
export interface LogicalModelDraft {
  id: number;
  projectId: number;
  domainId: number;
  processId: number;
  code: string;
  name: string;
  description?: string;
  status: 'DRAFT';
  updateTime?: string;
}
export interface LogicalEntity {
  id: number;
  logicalModelId: number;
  code: string;
  name: string;
  businessName?: string;
  description?: string;
}
export interface LogicalAttribute {
  id: number;
  entityId: number;
  code: string;
  name: string;
  stdFieldId?: number;
  logicalType?: string;
  description?: string;
  primaryFlag?: boolean;
  nullable?: boolean;
}
export interface LogicalRelation {
  id: number;
  sourceEntityId: number;
  targetEntityId: number;
  relationType: string;
  cardinality: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY' | 'UNKNOWN';
  description?: string;
}
export interface LogicalDraftDetail {
  model: LogicalModelDraft;
  process: SemanticProcessRecord;
  entities: { entity: LogicalEntity; attributes: LogicalAttribute[] }[];
  relations: LogicalRelation[];
}
export interface LogicalDraftVersion {
  id: number;
  versionNo: number;
  status: 'DRAFT';
  createdBy?: string;
  createTime?: string;
}

export const listLogicalDrafts = (): Promise<LogicalModelDraft[]> =>
  HttpUtils.getData<LogicalModelDraft[]>(ROOT);
export const getLogicalDraft = (id: number): Promise<LogicalDraftDetail> =>
  HttpUtils.getData<LogicalDraftDetail>(`${ROOT}/${id}`);
export const createLogicalDraft = (payload: {
  processId: number; code: string; name: string; description?: string;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(ROOT, payload);
export const addLogicalEntity = (id: number, payload: {
  code: string; name: string; businessName?: string; description?: string;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(`${ROOT}/${id}/entities`, payload);
export const addLogicalAttribute = (id: number, entityId: number, payload: {
  code: string; name: string; stdFieldId?: number; logicalType?: string;
  description?: string; primaryFlag?: boolean; nullable?: boolean;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(`${ROOT}/${id}/entities/${entityId}/attributes`, payload);
export const updateLogicalEntity = (id: number, entityId: number, payload: {
  code: string; name: string; businessName?: string; description?: string;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.putData<LogicalDraftDetail>(`${ROOT}/${id}/entities/${entityId}`, payload);
export const updateLogicalAttribute = (id: number, entityId: number, attrId: number, payload: {
  code: string; name: string; stdFieldId?: number; logicalType?: string;
  description?: string; primaryFlag?: boolean; nullable?: boolean;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.putData<LogicalDraftDetail>(`${ROOT}/${id}/entities/${entityId}/attributes/${attrId}`, payload);
export const updateLogicalRelation = (id: number, relationId: number, payload: {
  sourceEntityId: number; targetEntityId: number; cardinality: LogicalRelation['cardinality']; description?: string;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.putData<LogicalDraftDetail>(`${ROOT}/${id}/relations/${relationId}`, payload);
export const addLogicalRelation = (id: number, payload: {
  sourceEntityId: number; targetEntityId: number; cardinality: LogicalRelation['cardinality'];
  description?: string;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(`${ROOT}/${id}/relations`, payload);
export const freezeLogicalDraft = (id: number): Promise<LogicalDraftVersion> =>
  HttpUtils.postData<LogicalDraftVersion>(`${ROOT}/${id}/versions`, {});
export const listLogicalVersions = (id: number): Promise<LogicalDraftVersion[]> =>
  HttpUtils.getData<LogicalDraftVersion[]>(`${ROOT}/${id}/versions`);
export const getLogicalVersionSnapshot = (id: number, version: number): Promise<string> =>
  HttpUtils.getData<string>(`${ROOT}/${id}/versions/${version}`);
