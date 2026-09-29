import HttpUtils from '@/utils/HttpUtils';

import type {
  ModelingModelId,
  ModelingStandardCapturePayload,
  ModelingStandardCaptureResult,
  ModelingStandardRecommendation,
  ModelingStructureRecommendPayload,
} from './types';

const MODELING_API_PREFIX = '/api/v1/modeling';

/** 字段标准推荐(ticket 39,经 semantic SPI;仅提示不阻断)。 */
export const recommendModelingStandards = (
  modelId: ModelingModelId,
  payload: ModelingStructureRecommendPayload,
): Promise<ModelingStandardRecommendation> =>
  HttpUtils.postData<ModelingStandardRecommendation>(
    `${MODELING_API_PREFIX}/models/${modelId}/standards/recommend`,
    payload,
  );

/** 沉淀为标准(ticket 40,重名幂等返回既有标准)。 */
export const captureModelingStandard = (
  modelId: ModelingModelId,
  payload: ModelingStandardCapturePayload,
): Promise<ModelingStandardCaptureResult> =>
  HttpUtils.postData<ModelingStandardCaptureResult>(
    `${MODELING_API_PREFIX}/models/${modelId}/standards/capture`,
    payload,
  );

/** 上报标准采纳/绕过事件(ticket 42,fail-open)。 */
export const reportModelingStandardUsage = (
  modelId: ModelingModelId,
  payload: { standardId: number; usageType: 'APPLY' | 'BYPASS'; scene?: string },
): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${MODELING_API_PREFIX}/models/${modelId}/standards/usage-report`, payload);
