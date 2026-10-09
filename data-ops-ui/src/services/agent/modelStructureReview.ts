export interface ModelStructureReviewTarget { modelId: string; baselineVersionNo: number; definition: string }

export function readModelStructureReviewTarget(value: unknown): ModelStructureReviewTarget {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('模型结构比较上下文无效');
  const target = value as Record<string, unknown>;
  const { modelId, baselineVersionNo, definition } = target;
  if (typeof modelId !== 'string' || !/^[1-9][0-9]{0,18}$/.test(modelId)
    || (modelId.length === 19 && modelId > '9223372036854775807')
    || !Number.isInteger(baselineVersionNo) || Number(baselineVersionNo) <= 0 || Number(baselineVersionNo) > 2147483647
    || typeof definition !== 'string' || !/^[a-f0-9]{64}$/.test(definition)
    || Object.keys(target).some((key) => !['modelId', 'baselineVersionNo', 'definition'].includes(key))) {
    throw new Error('请从原模型版本页重新准备比较');
  }
  return { modelId, baselineVersionNo: Number(baselineVersionNo), definition };
}

export const modelStructureReviewSourcePath = (target: ModelStructureReviewTarget) =>
  `/modeling/models/${target.modelId}?tab=version&reviewVersion=${target.baselineVersionNo}`;
