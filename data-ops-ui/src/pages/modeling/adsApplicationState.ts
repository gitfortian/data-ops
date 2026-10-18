import type {
  AdsApplicationContractRequest,
  AdsApplicationContractSaved,
} from '@/services/modeling/adsApplication';

export const newAdsApplicationDraft = (
  code = '',
  name = '',
): AdsApplicationContractRequest => ({
  appCode: code,
  appName: name,
  owner: '',
  purpose: '',
  consumerType: 'REPORT',
  sourceMode: 'DWS',
  upstreamModelId: 0,
  upstreamVersionId: 0,
  upstreamVersionNo: 0,
  upstreamChecksum: '',
  outputFields: [],
  businessKeys: [],
  filterExpression: '',
  sortExpression: '',
  metricVersionId: null,
  refreshTarget: '',
});

export const draftFromAdsSaved = (
  saved: AdsApplicationContractSaved,
): AdsApplicationContractRequest => {
  const value = saved.contract;
  return {
    appCode: value.appCode,
    appName: value.appName,
    owner: value.owner,
    purpose: value.purpose,
    consumerType: value.consumerType,
    sourceMode: value.sourceMode,
    upstreamModelId: value.upstream.modelId,
    upstreamVersionId: value.upstream.versionId,
    upstreamVersionNo: value.upstream.versionNo,
    upstreamChecksum: value.upstream.checksum,
    outputFields: value.outputFields.map((field) => ({ ...field })),
    businessKeys: [...value.businessKeys],
    filterExpression: value.filterExpression,
    sortExpression: value.sortExpression,
    metricVersionId: value.metricVersionId ?? null,
    refreshTarget: value.refreshTarget,
  };
};

/** The contract's pinned source is never silently advanced to a newer publish. */
export const adsSourceDrift = (
  request: AdsApplicationContractRequest,
  publishedVersionId: number | null | undefined,
): 'UNKNOWN' | 'NOT_PINNED' | 'CURRENT' | 'STALE' => {
  if (!request.upstreamVersionId) return 'NOT_PINNED';
  if (publishedVersionId === undefined) return 'UNKNOWN';
  if (publishedVersionId === null || publishedVersionId !== request.upstreamVersionId) {
    return 'STALE';
  }
  return 'CURRENT';
};

export const changeAdsSourceMode = (
  request: AdsApplicationContractRequest,
  mode: 'DWS' | 'DWD',
): AdsApplicationContractRequest => ({
  ...request,
  sourceMode: mode,
  upstreamModelId: 0,
  upstreamVersionId: 0,
  upstreamVersionNo: 0,
  upstreamChecksum: '',
  outputFields: [],
  businessKeys: [],
  filterExpression: '',
  sortExpression: '',
  metricVersionId: null,
});

/** Editing a historic revision is explicitly a new draft, never an in-place overwrite. */
export const adsContractSignature = (value: AdsApplicationContractRequest) =>
  JSON.stringify(value);
