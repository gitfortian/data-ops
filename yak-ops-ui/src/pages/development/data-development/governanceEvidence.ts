import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type { DevelopmentId } from './types';

export type DevelopmentLineageEvidenceStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'FAILED'
  | 'SUCCEEDED'
  | 'NOT_APPLICABLE'
  | 'UNAVAILABLE';

export interface DevelopmentLineageEvidence {
  nodeId: DevelopmentId;
  revisionId: DevelopmentId;
  revisionNo: number;
  publishTime?: string | null;
  status: DevelopmentLineageEvidenceStatus;
  reason?: string | null;
  lineageAssetKey?: string | null;
  attempts: number;
  lastError?: string | null;
  nextAttemptTime?: string | null;
  deliveryCreateTime?: string | null;
  deliveryUpdateTime?: string | null;
  deliveryTaskId?: string | null;
}

export const getDevelopmentLineageEvidence = (
  nodeId: DevelopmentId,
  revisionNo: number,
): Promise<ApiResponse<DevelopmentLineageEvidence>> =>
  HttpUtils.get<DevelopmentLineageEvidence>(
    `/api/v1/data-development/nodes/${encodeURIComponent(nodeId)}`
      + `/revisions/${revisionNo}/lineage-evidence`,
    { skipErrorHandler: true },
  );

export const lineageEvidenceStatusLabel = (
  status: DevelopmentLineageEvidenceStatus,
) => ({
  PENDING: '等待 Lineage 投影',
  RUNNING: '正在写入 Lineage',
  FAILED: 'Lineage 投影失败，等待重试',
  SUCCEEDED: 'Lineage Evidence 已就绪',
  NOT_APPLICABLE: '不适用',
  UNAVAILABLE: 'Lineage Evidence 不可用',
}[status]);

export const canOpenLineageEvidence = (evidence?: DevelopmentLineageEvidence) =>
  evidence?.status === 'SUCCEEDED' && Boolean(evidence.lineageAssetKey);

export const lineageEvidenceUrl = (
  evidence: DevelopmentLineageEvidence,
  nodeId: DevelopmentId,
) => {
  if (!evidence.lineageAssetKey) return undefined;
  const returnTo = `/data-development?nodeId=${encodeURIComponent(nodeId)}`;
  return `/data-analysis/lineage?assetKey=${encodeURIComponent(evidence.lineageAssetKey)}`
    + `&returnTo=${encodeURIComponent(returnTo)}`;
};
