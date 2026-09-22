import HttpUtils from '@/utils/HttpUtils';
import type { ModelingLayerFieldMappingPayload, ModelingLayerFieldMappingView, ModelingModelId } from './types';

const MODELING_API_PREFIX = '/api/v1/modeling';

/** 字段分层映射(ticket 43)。 */
export const listModelingLayerFieldMappings = (modelId: ModelingModelId): Promise<ModelingLayerFieldMappingView[]> =>
  HttpUtils.getData<ModelingLayerFieldMappingView[]>(`${MODELING_API_PREFIX}/models/${modelId}/layer-mappings`);

export const upsertModelingLayerFieldMapping = (
  modelId: ModelingModelId,
  payload: ModelingLayerFieldMappingPayload,
): Promise<boolean> => HttpUtils.putData<boolean>(`${MODELING_API_PREFIX}/models/${modelId}/layer-mappings`, payload);

export const deleteModelingLayerFieldMapping = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${MODELING_API_PREFIX}/layer-mappings/${id}`);
