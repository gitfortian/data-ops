import HttpUtils from '@/utils/HttpUtils';

export interface OdsExecutionEvidence {
  executionId: number;
  jobDefinitionId: number;
  batchId: number;
  jobRevisionNo: number;
  attemptNo: number;
  status: string;
  externalExecutionId?: string | null;
  engineJobId?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  sourceRows?: number | null;
  sinkCommittedRows?: number | null;
  failedRows?: number | null;
  errorMessage?: string | null;
}

export interface OdsExecutionDiscoveryPage {
  scanPage: number;
  scanSize: number;
  moreCandidates: boolean;
  matches: OdsExecutionEvidence[];
}

/** Offline Owner returns a bounded candidate page, not a full matching-history count. */
export const discoverOdsExecutions = (
  modelId: number, versionNo: number, targetObservationId: number,
  scanPage = 1, scanSize = 50,
): Promise<OdsExecutionDiscoveryPage> =>
  HttpUtils.getData<OdsExecutionDiscoveryPage>(
    '/api/v1/job/batch-instance/ods-evidence/discover' +
    `?modelId=${modelId}&versionNo=${versionNo}&targetObservationId=${targetObservationId}` +
    `&scanPage=${scanPage}&scanSize=${scanSize}`,
  );

export interface OdsExecutionCursorPage {
  nextBeforeId: number;
  moreCandidates: boolean;
  matches: OdsExecutionEvidence[];
}

/** Stable seek: the bound is exclusive and tied to monotonic execution IDs. */
export const discoverOdsExecutionsBefore = (
  modelId: number, versionNo: number, targetObservationId: number,
  beforeId: number, scanSize = 50,
): Promise<OdsExecutionCursorPage> =>
  HttpUtils.getData<OdsExecutionCursorPage>(
    '/api/v1/job/batch-instance/ods-evidence/discover-before' +
    `?modelId=${modelId}&versionNo=${versionNo}&targetObservationId=${targetObservationId}` +
    `&beforeId=${beforeId}&scanSize=${scanSize}`,
  );
