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
