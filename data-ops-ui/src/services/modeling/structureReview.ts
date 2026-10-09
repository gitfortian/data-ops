import HttpUtils from '@/utils/HttpUtils';

export interface ModelStructureReviewContext {
  projectId: string; modelId: string; baselineVersionNo: number; baselineVersionId: string; definition: string;
  baselineColumnCount: number; savedColumnCount: number;
  changes: Array<{ area: string; name: string; before: string | null; after: string | null }>;
  mappingChecks: Array<{ targetColumn: string; mappingPresent: boolean; transformPresent: boolean; reasons: string[] }>;
  coverageGaps: string[];
}

export const prepareModelStructureReview = (modelId: string, baselineVersionNo: number): Promise<ModelStructureReviewContext> =>
  HttpUtils.getData<ModelStructureReviewContext>(`/api/v1/modeling/models/${modelId}/structure-review?baselineVersionNo=${baselineVersionNo}`);

/** Reject a mismatched or malformed response before it can enable an Agent entry. */
export function matchesStructureReview(value: ModelStructureReviewContext, projectId: string, modelId: string, versionNo: number): boolean {
  return !!value && value.projectId === projectId && value.modelId === modelId && value.baselineVersionNo === versionNo
    && typeof value.definition === 'string' && /^[a-f0-9]{64}$/.test(value.definition)
    && typeof value.baselineVersionId === 'string' && /^[1-9][0-9]{0,18}$/.test(value.baselineVersionId)
    && Number.isInteger(value.baselineColumnCount) && value.baselineColumnCount >= 0 && value.baselineColumnCount <= 100
    && Number.isInteger(value.savedColumnCount) && value.savedColumnCount >= 0 && value.savedColumnCount <= 100
    && Array.isArray(value.changes) && value.changes.length <= 244
    && value.changes.every((item) => item && ['TABLE', 'COLUMN', 'PRIMARY_KEY', 'INDEX', 'PARTITION'].includes(item.area)
      && typeof item.name === 'string' && item.name.length <= 128
      && (item.before === null || typeof item.before === 'string') && (item.after === null || typeof item.after === 'string'))
    && Array.isArray(value.mappingChecks) && value.mappingChecks.length <= 200
    && value.mappingChecks.every((item) => item && typeof item.targetColumn === 'string' && item.targetColumn.length <= 128
      && typeof item.mappingPresent === 'boolean' && typeof item.transformPresent === 'boolean' && Array.isArray(item.reasons)
      && item.reasons.length <= 4 && item.reasons.every((reason) => ['ORPHAN_MAPPING_TARGET', 'UNMAPPED_SAVED_COLUMN', 'CHANGED_TARGET_REVIEW', 'TRANSFORM_MANUAL_REVIEW'].includes(reason)))
    && Array.isArray(value.coverageGaps) && value.coverageGaps.length <= 20
    && value.coverageGaps.every((gap) => typeof gap === 'string' && gap.length <= 128)
    && JSON.stringify(value).length <= 24000;
}
