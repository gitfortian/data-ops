import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type {
  DevelopmentId,
  DevelopmentSqlLineagePreview,
  DevelopmentSqlLineagePreviewRequest,
  DevelopmentTaskDefinition,
  DevelopmentTaskExecutionSubmission,
  DevelopmentTaskRevision,
} from './types';

/**
 * Deliberately raw envelope requests used only by the Workbench page coordinator.
 *
 * This narrow compatibility contract is not a general Data Development service:
 * Run must pass authoring preflight, Publish needs exact-draft validation and
 * user confirmation, and Lineage preview needs live SQL editor metadata.
 * Other consumers use the modern data-only API.
 */
const nodePath = (nodeId: DevelopmentId) =>
  `/api/v1/data-development/nodes/${nodeId}`;

export const runDevelopmentTask = (
  nodeId: DevelopmentId,
  payload: DevelopmentTaskDefinition,
): Promise<ApiResponse<DevelopmentTaskExecutionSubmission>> =>
  HttpUtils.post<DevelopmentTaskExecutionSubmission>(
    `${nodePath(nodeId)}/run`,
    payload,
  );

export const publishDevelopmentTask = (
  nodeId: DevelopmentId,
  draftRevision: number,
): Promise<ApiResponse<DevelopmentTaskRevision>> =>
  HttpUtils.post<DevelopmentTaskRevision>(
    `${nodePath(nodeId)}/publish`,
    { draftRevision },
  );

export const previewDevelopmentSqlLineageRequest = (
  nodeId: DevelopmentId,
  payload: DevelopmentSqlLineagePreviewRequest,
): Promise<ApiResponse<DevelopmentSqlLineagePreview>> =>
  HttpUtils.post<DevelopmentSqlLineagePreview>(
    `${nodePath(nodeId)}/lineage/preview`,
    payload,
  );
