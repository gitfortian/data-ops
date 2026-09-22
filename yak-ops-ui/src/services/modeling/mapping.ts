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
): Promise<boolean> =>
  HttpUtils.putData<boolean>(
    `${MODELING_API_PREFIX}/models/${modelId}/mappings/${encodeURIComponent(targetColumn)}`,
    payload,
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
