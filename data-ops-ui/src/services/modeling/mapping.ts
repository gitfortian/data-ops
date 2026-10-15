import HttpUtils from '@/utils/HttpUtils';
import type { ModelingMappingSavePayload, ModelingMappingView, ModelingModelId } from './types';

const MODELING_API_PREFIX = '/api/v1/modeling';

/** 来源映射(ticket 19)。 */
export const listModelingMappings = (modelId: ModelingModelId): Promise<ModelingMappingView[]> =>
  HttpUtils.getData<ModelingMappingView[]>(`${MODELING_API_PREFIX}/models/${modelId}/mappings`);

export const setModelingMapping = (
  modelId: ModelingModelId,
  targetColumn: string,
  payload: ModelingMappingSavePayload,
  definition?: string,
): Promise<boolean> =>
  HttpUtils.putData<boolean>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/${encodeURIComponent(targetColumn)}${definition ? '/edit-context' : ''}`,
    payload,
    definition ? { headers: { 'If-Match': definition } } : undefined,
  );

export const clearModelingMapping = (modelId: ModelingModelId, targetColumn: string): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/${encodeURIComponent(targetColumn)}`,
  );

export const clearAllModelingMappings = (modelId: ModelingModelId): Promise<number> =>
  HttpUtils.deleteData<number>(`${MODELING_API_PREFIX}/models/${modelId}/mappings`);

export const validateModelingExpression = (
  modelId: ModelingModelId,
  expression?: string,
): Promise<{ valid: boolean; message?: string }> =>
  HttpUtils.postData<{ valid: boolean; message?: string }>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/validate-expression`,
    { expression },
  );

export interface MappingEditContext { definition: string; mapping: ModelingMappingView }
export const getModelingMappingContext = (modelId: ModelingModelId, targetColumn: string): Promise<MappingEditContext> =>
  HttpUtils.getData<MappingEditContext>(`${MODELING_API_PREFIX}/models/${modelId}/mappings/${encodeURIComponent(targetColumn)}/edit-context`);

/** Existing ODS draft: authoritative source catalog versus persisted column mappings. */
export interface OdsMappingReconcilePreview {
  modelId: number;
  sourceDatasourceId: number;
  sourceDatabase: string;
  sourceSchema?: string | null;
  sourceTable: string;
  definition: string;
  sourceColumns: string[];
  rows: {
    targetColumn: string;
    dataType: string;
    status: 'MAPPED' | 'UNMAPPED' | 'TECHNICAL' | 'SOURCE_CONFLICT';
    existingSourceColumn?: string | null;
    suggestedSourceColumn?: string | null;
  }[];
  mappedCount: number;
  unmappedCount: number;
  technicalCount: number;
  orphanCount: number;
  conflictCount: number;
}
export interface OdsMappingReconcileReceipt {
  modelId: number;
  applied: number;
  mappedCount: number;
  confirmedTargets: string[];
  status: 'SAVED_AND_VERIFIED';
}
export const previewOdsMappingReconcile = (
  modelId: ModelingModelId,
): Promise<OdsMappingReconcilePreview> =>
  HttpUtils.getData<OdsMappingReconcilePreview>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/ods-reconcile`,
  );
export const confirmOdsMappingReconcile = (
  modelId: ModelingModelId, expectedDefinition: string,
  selections: { targetColumn: string; sourceColumn: string }[],
): Promise<OdsMappingReconcileReceipt> =>
  HttpUtils.postData<OdsMappingReconcileReceipt>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/ods-reconcile`,
    { expectedDefinition, selections },
  );

/** Live ODS source evidence, not a frozen publish version or running data result. */
export interface OdsSourceEvidenceInspection {
  modelId: number;
  modelStatus: string;
  sourceDatasourceId?: number | null;
  sourceDatabase?: string | null;
  sourceTable?: string | null;
  status: 'CONSISTENT_NOW' | 'NEEDS_REVIEW' | 'NOT_CHECKABLE';
  evidenceSha256?: string | null;
  targetCount: number;
  sourceCount: number;
  mappingCount: number;
  fields: {
    targetColumn?: string | null;
    targetType?: string | null;
    sourceColumn?: string | null;
    sourceType?: string | null;
    status: string;
    reason: string;
  }[];
  blockers: string[];
}
export const inspectOdsSourceEvidence = (
  modelId: ModelingModelId,
): Promise<OdsSourceEvidenceInspection> =>
  HttpUtils.getData<OdsSourceEvidenceInspection>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/ods-source-evidence`,
  );
