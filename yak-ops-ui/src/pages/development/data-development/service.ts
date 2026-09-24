import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { previewDevelopmentSqlLineageRequest } from '@/services/data-development/legacy';
import HttpUtils from '@/utils/HttpUtils';
import { getIntl } from '@umijs/max';
import { Modal } from 'antd';

import {
  classifyDraftSaveFailure,
  rebaseDraftSavePayload,
} from './components/workbench/draftSaveFailure';
import { getSqlMetadataContext } from './editors/sql/metadata/sqlMetadataContextStore';
import type {
  DevelopmentId,
  DevelopmentSqlLineagePreview,
  DevelopmentSqlLineagePreviewRequest,
  DevelopmentTaskDraft,
  SaveDevelopmentTaskDraftPayload,
} from './types';

/**
 * @deprecated New data-development code should import from
 * `@/services/data-development`.
 */
export * from '@/services/data-development/legacy';

const draftPath = (nodeId: DevelopmentId) =>
  `/api/v1/data-development/nodes/${encodeURIComponent(nodeId)}/draft`;

const saveDraftRequest = (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentTaskDraftPayload,
): Promise<ApiResponse<DevelopmentTaskDraft>> =>
  HttpUtils.put<DevelopmentTaskDraft>(draftPath(nodeId), payload, {
    skipErrorHandler: true,
  });

const loadDraftRequest = (
  nodeId: DevelopmentId,
): Promise<ApiResponse<DevelopmentTaskDraft>> =>
  HttpUtils.get<DevelopmentTaskDraft>(draftPath(nodeId), {
    skipErrorHandler: true,
  });

const responseData = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const formatSaveFailure = (error: unknown) => {
  const intl = getIntl();
  const failure = classifyDraftSaveFailure(error);
  const detail =
    failure.detail ||
    (failure.status
      ? `HTTP ${failure.status}`
      : intl.formatMessage({ id: 'pages.dataDevelopment.common.unavailable' }));

  switch (failure.kind) {
    case 'permission-denied':
      return intl.formatMessage({ id: 'pages.dataDevelopment.authoring.savePermissionDenied' });
    case 'not-found':
      return intl.formatMessage({ id: 'pages.dataDevelopment.authoring.saveResourceMissing' });
    case 'network':
      return intl.formatMessage({ id: 'pages.dataDevelopment.authoring.saveNetworkFailed' });
    default:
      return intl.formatMessage(
        { id: 'pages.dataDevelopment.authoring.saveUnknownFailed' },
        { detail },
      );
  }
};

const confirmConflictOverwrite = (
  revision: number,
  detail?: string,
): Promise<boolean> => {
  const intl = getIntl();
  const description = intl.formatMessage(
    { id: 'pages.dataDevelopment.authoring.conflictDescription' },
    { revision },
  );
  const conflictDetail = detail
    ? ` ${intl.formatMessage(
        { id: 'pages.dataDevelopment.authoring.conflictDetail' },
        { detail },
      )}`
    : '';

  return new Promise((resolve) => {
    Modal.confirm({
      title: intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictTitle' }),
      content: `${description}${conflictDetail}`,
      okText: intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictConfirm' }),
      cancelText: intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictCancel' }),
      okButtonProps: { danger: true },
      closable: true,
      maskClosable: false,
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
};

/**
 * Workbench save corridor with explicit optimistic-conflict recovery.
 *
 * A 409 never discards local editor content. We first load the latest server Draft,
 * then require an explicit user decision before retrying the same local definition
 * against the new base revision. Cancelling leaves both local and remote content intact.
 */
export const saveDevelopmentTaskDraft = async (
  nodeId: DevelopmentId,
  payload: SaveDevelopmentTaskDraftPayload,
): Promise<ApiResponse<DevelopmentTaskDraft>> => {
  try {
    return await saveDraftRequest(nodeId, payload);
  } catch (error) {
    const failure = classifyDraftSaveFailure(error);
    if (failure.kind !== 'conflict') {
      throw new Error(formatSaveFailure(error));
    }

    const intl = getIntl();
    let latest: DevelopmentTaskDraft;
    try {
      latest = responseData(
        await loadDraftRequest(nodeId),
        intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictInspectFailed' }),
      );
    } catch {
      throw new Error(
        intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictInspectFailed' }),
      );
    }

    const overwrite = await confirmConflictOverwrite(
      latest.draftRevision,
      failure.detail,
    );
    if (!overwrite) {
      throw new Error(
        intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictCancelled' }),
      );
    }

    try {
      return await saveDraftRequest(
        nodeId,
        rebaseDraftSavePayload(payload, latest.draftRevision),
      );
    } catch (retryError) {
      const retryFailure = classifyDraftSaveFailure(retryError);
      if (retryFailure.kind === 'conflict') {
        throw new Error(
          intl.formatMessage({ id: 'pages.dataDevelopment.authoring.conflictAgain' }),
        );
      }
      throw new Error(formatSaveFailure(retryError));
    }
  }
};

/**
 * Legacy editor adapter. SQL metadata belongs to the active editor session,
 * so this coordination remains at page level instead of making Service depend
 * on a page store.
 */
export const previewDevelopmentSqlLineage = (
  nodeId: DevelopmentId,
  payload: DevelopmentSqlLineagePreviewRequest,
): Promise<ApiResponse<DevelopmentSqlLineagePreview>> => {
  const metadataContext = getSqlMetadataContext(nodeId);
  return previewDevelopmentSqlLineageRequest(nodeId, {
    ...payload,
    databaseName: payload.databaseName ?? metadataContext?.database,
    schemaName: payload.schemaName ?? metadataContext?.schema,
  });
};
