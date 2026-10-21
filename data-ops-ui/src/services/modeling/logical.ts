import HttpUtils from '@/utils/HttpUtils';
import request, { type ApiResponse } from '@/utils/request';
import { parseLogicalId, parseLogicalJson, type LogicalId } from './logicalIdentity';
import type { SemanticProcessRecord } from '@/services/semantic/types';

const ROOT = '/api/v1/modeling/logical-models';

/** Canonical owner is Modeling; Semantic data remains referenced, not copied. */
export interface LogicalModelDraft {
  id: number;
  projectId: number;
  draftRevision: number;
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
const exactLogicalId = (id: unknown): LogicalId => {
  const parsed = parseLogicalId(id);
  if (parsed === undefined) throw new Error('无效的逻辑对象身份');
  return parsed;
};

const exactOwnerResponse = <T>(raw: string): T =>
  HttpUtils.unwrap(parseLogicalJson<ApiResponse<T>>(raw));

/** Preserve Java Long identities before a JSON parser can round them. */
export const getLogicalDraft = (id: LogicalId): Promise<LogicalDraftDetail> => {
  const exact = exactLogicalId(id);
  return request<string>(`${ROOT}/${exact}`, {
    responseType: 'text', businessErrorMode: 'reject',
  }).then(exactOwnerResponse<LogicalDraftDetail>);
};
export const createLogicalDraft = (payload: {
  processId: number; code: string; name: string; description?: string;
}): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(ROOT, payload);
export const addLogicalEntity = (id: number, payload: {
  code: string; name: string; businessName?: string; description?: string;
}, expectedRevision: number): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(`${ROOT}/${id}/entities?expectedRevision=${expectedRevision}`, payload);
export const addLogicalAttribute = (id: number, entityId: number, payload: {
  code: string; name: string; stdFieldId?: number; logicalType?: string;
  description?: string; primaryFlag?: boolean; nullable?: boolean;
}, expectedRevision: number): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(`${ROOT}/${id}/entities/${entityId}/attributes?expectedRevision=${expectedRevision}`, payload);
export const updateLogicalEntity = (id: number, entityId: number, payload: {
  code: string; name: string; businessName?: string; description?: string;
}, expectedRevision: number): Promise<LogicalDraftDetail> =>
  HttpUtils.putData<LogicalDraftDetail>(`${ROOT}/${id}/entities/${entityId}?expectedRevision=${expectedRevision}`, payload);
export const updateLogicalAttribute = (id: LogicalId, entityId: LogicalId, attrId: LogicalId, payload: {
  code: string; name: string; stdFieldId?: number; logicalType?: string;
  description?: string; primaryFlag?: boolean; nullable?: boolean;
}, expectedRevision: number): Promise<LogicalDraftDetail> => {
  const path = `${ROOT}/${exactLogicalId(id)}/entities/${exactLogicalId(entityId)}/attributes/${exactLogicalId(attrId)}`;
  if (!Number.isSafeInteger(expectedRevision) || expectedRevision < 0) {
    throw new Error('无效的逻辑对象修订');
  }
  return request<string>(`${path}?expectedRevision=${expectedRevision}`, {
    method: 'PUT', data: payload, responseType: 'text', businessErrorMode: 'reject',
    headers: { 'Content-Type': 'application/json' },
  }).then(exactOwnerResponse<LogicalDraftDetail>);
};
export const updateLogicalRelation = (id: number, relationId: number, payload: {
  sourceEntityId: number; targetEntityId: number; cardinality: LogicalRelation['cardinality']; description?: string;
}, expectedRevision: number): Promise<LogicalDraftDetail> =>
  HttpUtils.putData<LogicalDraftDetail>(`${ROOT}/${id}/relations/${relationId}?expectedRevision=${expectedRevision}`, payload);
export const addLogicalRelation = (id: number, payload: {
  sourceEntityId: number; targetEntityId: number; cardinality: LogicalRelation['cardinality'];
  description?: string;
}, expectedRevision: number): Promise<LogicalDraftDetail> =>
  HttpUtils.postData<LogicalDraftDetail>(`${ROOT}/${id}/relations?expectedRevision=${expectedRevision}`, payload);
export const freezeLogicalDraft = (id: number, expectedRevision: number): Promise<LogicalDraftVersion> =>
  HttpUtils.postData<LogicalDraftVersion>(`${ROOT}/${id}/versions?expectedRevision=${expectedRevision}`, {});
export const listLogicalVersions = (id: number): Promise<LogicalDraftVersion[]> =>
  HttpUtils.getData<LogicalDraftVersion[]>(`${ROOT}/${id}/versions`);
export const getLogicalVersionSnapshot = (id: number, version: number): Promise<string> =>
  HttpUtils.getData<string>(`${ROOT}/${id}/versions/${version}`);

/** Exact logical DRAFT snapshot compared with live authoring and today's Semantic references.
 * Does not approve the logical model, freeze standards or create physical mappings. */
export interface LogicalVersionSemanticReview {
  logicalModelId: number;
  versionNo: number;
  frozenStatus: string;
  draftStatus: string;
  frozenSnapshotSha256: string;
  frozenDesignSha256: string;
  currentDesignSha256: string;
  entityCount: number;
  attributeCount: number;
  relationCount: number;
  designComparison: 'MATCHES_CURRENT' | 'DIFFERS_FROM_CURRENT';
  reviewStatus: 'REVIEWABLE_ONLY' | 'NEEDS_REVIEW';
  changes: {
    objectType: string;
    objectId: number;
    label: string;
    status: 'ADDED_NOW' | 'REMOVED_NOW' | 'CHANGED_NOW';
    frozenValue?: string | null;
    currentValue?: string | null;
  }[];
  attributes: {
    entityId: number;
    entityName: string;
    attributeId: number;
    attributeName: string;
    stdFieldId?: number | null;
    standardName?: string | null;
    standardRole?: string | null;
    standardStatus: 'PENDING_STANDARD' | 'INVALID_STANDARD' | 'ENABLED_NOW';
    explanation: string;
  }[];
  relations: {
    relationId: number;
    sourceEntity: string;
    targetEntity: string;
    cardinality: string;
    status: 'CARDINALITY_PENDING' | 'DECLARED_NOT_CONFIRMED';
    explanation: string;
  }[];
  blockers: string[];
  limitations: string[];
}
export const reviewLogicalVersionSemantics = (
  logicalId: number, versionNo: number,
): Promise<LogicalVersionSemanticReview> =>
  HttpUtils.getData<LogicalVersionSemanticReview>(
    `${ROOT}/${logicalId}/versions/${versionNo}/semantic-review`,
  );

/** An evidence-only proposal: never a generated model, approved mapping or deployment. */
export interface LogicalPhysicalPreview {
  logicalModelId: number;
  logicalVersionNo: number;
  physicalModelId: number;
  physicalLayer?: string;
  physicalStatus: string;
  readyForReview: boolean;
  /** SHA-256 of the compared frozen logical JSON and live physical DRAFT projection. */
  logicalSnapshotSha256: string;
  physicalStructureSha256: string;
  blockers: string[];
  columns: {
    physicalColumn: string;
    physicalStdFieldId?: number;
    logicalEntity?: string;
    logicalAttribute?: string;
    result: string;
    reason: string;
    physicalColumnId?: number;
    logicalEntityId?: number;
    logicalAttributeId?: number;
  }[];
}
export const previewLogicalPhysical = (
  logicalId: number, versionNo: number, physicalModelId: number,
): Promise<LogicalPhysicalPreview> =>
  HttpUtils.getData<LogicalPhysicalPreview>(
    `${ROOT}/${logicalId}/versions/${versionNo}/physical-preview?physicalModelId=${physicalModelId}`,
  );

/** User-selected, ephemeral mapping review; never a saved or approved Mapping. */
export interface LogicalPhysicalReview {
  logicalModelId: number;
  logicalVersionNo: number;
  physicalModelId: number;
  logicalSnapshotSha256: string;
  physicalStructureSha256: string;
  evidenceUnchanged: boolean;
  readyForManualDesign: boolean;
  status: 'REVIEWABLE_ONLY' | 'BLOCKED';
  blockers: string[];
  reviewedMappings: {
    logicalAttributeId: number;
    physicalColumnId: number;
    stdFieldId: number;
    physicalColumn: string;
    logicalAttribute: string;
  }[];
}
export interface LogicalPhysicalReviewRequest {
  logicalSnapshotSha256: string;
  physicalStructureSha256: string;
  selections: { logicalAttributeId: number; physicalColumnId: number }[];
}
export const reviewLogicalPhysical = (
  logicalId: number, versionNo: number, physicalModelId: number,
  request: LogicalPhysicalReviewRequest,
): Promise<LogicalPhysicalReview> =>
  HttpUtils.postData<LogicalPhysicalReview>(
    `${ROOT}/${logicalId}/versions/${versionNo}/physical-review?physicalModelId=${physicalModelId}`,
    request,
  );
