import HttpUtils from '@/utils/HttpUtils';

const MODELING_API_PREFIX = '/api/v1/modeling/models';

export type AdsSourceMode = 'DWS' | 'DWD';
export type AdsConsumerType = 'REPORT' | 'DATASET' | 'API' | 'ANALYSIS';

export interface AdsApplicationOutputField {
  sourceColumn: string;
  outputColumn: string;
  standardFieldId?: number | null;
  sensitivity?: string | null;
}

export interface AdsApplicationUpstreamPin {
  modelId: number;
  versionId: number;
  versionNo: number;
  checksum: string;
  layerCode: string;
}

export interface AdsApplicationContract {
  appCode: string;
  appName: string;
  owner: string;
  purpose: string;
  consumerType: AdsConsumerType;
  sourceMode: AdsSourceMode;
  upstream: AdsApplicationUpstreamPin;
  outputFields: AdsApplicationOutputField[];
  businessKeys: string[];
  filterExpression: string;
  sortExpression: string;
  metricVersionId?: number | null;
  refreshTarget: string;
  /** Frozen physical target structure identity at the time this contract revision was saved. */
  targetStructureFingerprint: string;
}

export interface AdsApplicationContractRequest {
  appCode: string;
  appName: string;
  owner: string;
  purpose: string;
  consumerType: AdsConsumerType;
  sourceMode: AdsSourceMode;
  upstreamModelId: number;
  upstreamVersionId: number;
  upstreamVersionNo: number;
  upstreamChecksum: string;
  outputFields: AdsApplicationOutputField[];
  businessKeys: string[];
  filterExpression?: string;
  sortExpression?: string;
  metricVersionId?: number | null;
  refreshTarget?: string;
  /** Required for save: 0 means no previous contract; protects concurrent edits atomically in C1. */
  expectedRevision?: number;
  /** The target structure fingerprint the user explicitly reviewed before save. */
  expectedTargetStructureFingerprint?: string;
}

export interface AdsApplicationContractSaved {
  modelId: number;
  revision: number;
  contractSha256: string;
  contractJson: string;
  contract: AdsApplicationContract;
  updatedBy: string;
  updatedAt: string;
}

export interface AdsApplicationContractValidation {
  valid: boolean;
  blockers: string[];
  warnings: string[];
  targetStructureFingerprint: string;
  contract: AdsApplicationContract | null;
  /** This describes delivery configuration only; it is not a query/run result. */
  deliveryStatus: 'DELIVERY_NOT_CONFIGURED' | string;
}

export interface AdsApplicationContractHistoryEntry extends AdsApplicationContractSaved {}

const contractPath = (modelId: number) =>
  `${MODELING_API_PREFIX}/${modelId}/ads-application`;

export const getAdsApplicationContract = (modelId: number) =>
  HttpUtils.getData<AdsApplicationContractSaved | null>(contractPath(modelId));

export const getAdsApplicationContractHistory = (modelId: number) =>
  HttpUtils.getData<AdsApplicationContractHistoryEntry[]>(`${contractPath(modelId)}/history`);

export const validateAdsApplicationContract = (
  modelId: number,
  request: AdsApplicationContractRequest,
) =>
  HttpUtils.postData<AdsApplicationContractValidation>(
    `${contractPath(modelId)}/validate`,
    request,
  );

export const saveAdsApplicationContract = (
  modelId: number,
  request: AdsApplicationContractRequest,
) =>
  HttpUtils.putData<AdsApplicationContractSaved>(contractPath(modelId), request);
