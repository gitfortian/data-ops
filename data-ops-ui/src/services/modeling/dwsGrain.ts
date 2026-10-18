import HttpUtils from '@/utils/HttpUtils';

export interface DwsGrainMeasure {
  column: string;
  function: string;
}

export interface DwsPinnedUpstream {
  modelId: number;
  versionId: number;
  versionNo: number;
  checksum: string;
}

export interface DwsGrainDesign {
  statPeriod: string;
  timeZone: string;
  businessTimeColumn: string;
  periodBucketColumn: string;
  dimensions: string[];
  measures: DwsGrainMeasure[];
  upstreamVersions: DwsPinnedUpstream[];
}

export interface DwsGrainSaved {
  modelId: number;
  revision: number;
  structureFingerprint: string;
  designSha256: string;
  designJson: string;
  design: DwsGrainDesign;
}

export interface DwsGrainCandidate {
  modelId: number;
  code: string;
  name: string;
  publishedVersionId?: number | null;
  publishedVersionNo?: number | null;
  usable: boolean;
}

export interface DwsGrainHistoryEntry extends DwsGrainSaved {
  updatedBy: string;
  updatedAt: string;
}

export interface SaveDwsGrainRequest {
  statPeriod: string;
  timeZone: string;
  businessTimeColumn: string;
  periodBucketColumn: string;
  dimensions: string[];
  measures: DwsGrainMeasure[];
  upstreamModelIds: number[];
  expectedStructureFingerprint: string;
}

const uri = (modelId: number) => `/api/v1/modeling/models/${modelId}/dws-grain`;

export const getDwsGrain = (modelId: number): Promise<DwsGrainSaved | null> =>
  HttpUtils.getData<DwsGrainSaved | null>(uri(modelId));

export const listDwsGrainCandidates = (modelId: number): Promise<DwsGrainCandidate[]> =>
  HttpUtils.getData<DwsGrainCandidate[]>(`${uri(modelId)}/upstream-candidates`);

export const saveDwsGrain = (
  modelId: number, payload: SaveDwsGrainRequest,
): Promise<DwsGrainSaved> => HttpUtils.putData<DwsGrainSaved>(uri(modelId), payload);

export const getDwsGrainHistory = (
  modelId: number,
): Promise<DwsGrainHistoryEntry[]> =>
  HttpUtils.getData<DwsGrainHistoryEntry[]>(`${uri(modelId)}/history`);

export const restoreDwsGrainRevision = (
  modelId: number, revision: number,
): Promise<DwsGrainSaved> =>
  HttpUtils.postData<DwsGrainSaved>(`${uri(modelId)}/history/${revision}/restore`, {});
