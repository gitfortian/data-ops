import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import {
  previewDevelopmentSqlLineageRequest,
  publishDevelopmentTask as publishDevelopmentTaskRequest,
  runDevelopmentTask as runDevelopmentTaskRequest,
} from '@/services/data-development/legacy';
import HttpUtils from '@/utils/HttpUtils';
import { getIntl } from '@umijs/max';
import { Modal } from 'antd';
import { createElement } from 'react';

import {
  classifyDraftSaveFailure,
  rebaseDraftSavePayload,
} from './components/workbench/draftSaveFailure';
import { getSqlMetadataContext } from './editors/sql/metadata/sqlMetadataContextStore';
import { validateDevelopmentRunDefinition } from './executions/runPreflight';
import {
  publishReadinessSummary,
  type DevelopmentTaskPublishValidation,
} from './publishReadiness';
import type {
  DevelopmentId,
  DevelopmentSqlLineagePreview,
  DevelopmentSqlLineagePreviewRequest,
  DevelopmentTaskDefinition,
  DevelopmentTaskDraft,
  DevelopmentTaskExecutionSubmission,
  DevelopmentTaskRevision,
  SaveDevelopmentTaskDraftPayload,
} from './types';

/**
 * @deprecated New data-development code should import from
 * `@/services/data-development`.
 */
export * from '@/services/data-development/legacy';

const nodePath = (nodeId: DevelopmentId) =>
  `/api/v1/data-development/nodes/${encodeURIComponent(nodeId)}`;
const draftPath = (nodeId: DevelopmentId) => `${nodePath(nodeId)}/draft`;

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

const validatePublishRequest = (
  nodeId: DevelopmentId,
  draftRevision: number,
): Promise<ApiResponse<DevelopmentTaskPublishValidation>> =>
  HttpUtils.post<DevelopmentTaskPublishValidation>(
    `${nodePath(nodeId)}/publish-validation`,
    { draftRevision },
    { skipErrorHandler: true },
  );

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

const confirmPublishReadiness = (
  validation: DevelopmentTaskPublishValidation,
): Promise<boolean> => {
  const intl = getIntl();
  const summary = publishReadinessSummary(validation);
  const issueList = summary.issueLabels.length
    ? createElement(
        'ul',
        { className: 'mt-2 list-disc pl-5 text-[12px]' },
        summary.issueLabels.map((issue, index) =>
          createElement('li', { key: `${index}:${issue}` }, issue),
        ),
      )
    : null;
  const content = createElement(
    'div',
    { className: 'space-y-2 text-[12px] leading-5 text-[#475467]' },
    createElement(
      'div',
      null,
      intl.formatMessage(
        { id: 'pages.dataDevelopment.authoring.publishReadinessDraft' },
        { revision: summary.draftRevision },
      ),
    ),
    createElement(
      'div',
      { className: validation.valid ? 'text-[#027a48]' : 'text-[#b42318]' },
      intl.formatMessage({
        id: validation.valid
          ? 'pages.dataDevelopment.authoring.publishReadinessPassed'
          : 'pages.dataDevelopment.authoring.publishReadinessBlocked',
      }),
    ),
    validation.message
      ? createElement('div', { className: 'text-[#667085]' }, validation.message)
      : null,
    issueList,
    validation.valid
      ? createElement(
          'div',
          { className: 'rounded bg-[#f2f4f7] px-2 py-2 text-[#667085]' },
          intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishResultHint' }),
        )
      : null,
  );

  return new Promise((resolve) => {
    Modal.confirm({
      title: intl.formatMessage({
        id: validation.valid
          ? 'pages.dataDevelopment.authoring.publishReadinessTitle'
          : 'pages.dataDevelopment.authoring.publishBlockedTitle',
      }),
      content,
      okText: intl.formatMessage({
        id: validation.valid
          ? 'pages.dataDevelopment.authoring.publishConfirm'
          : 'pages.dataDevelopment.common.close',
      }),
      cancelText: intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' }),
      cancelButtonProps: validation.valid ? undefined : { style: { display: 'none' } },
      okButtonProps: validation.valid ? undefined : { type: 'default' },
      closable: true,
      maskClosable: false,
      onOk: () => resolve(validation.valid),
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
 * Product-facing editor-run corridor. These checks intentionally happen before the
 * HTTP submission so predictable authoring errors never masquerade as runtime failures.
 * Plugin/runtime validation remains authoritative after submission.
 */
export const runDevelopmentTask = (
  nodeId: DevelopmentId,
  payload: DevelopmentTaskDefinition,
): Promise<ApiResponse<DevelopmentTaskExecutionSubmission>> => {
  const preflight = validateDevelopmentRunDefinition(payload);
  if (preflight) {
    return Promise.reject(
      new Error(getIntl().formatMessage({ id: preflight.messageId })),
    );
  }
  return runDevelopmentTaskRequest(nodeId, payload);
};

/**
 * Product-facing Publish corridor. Preflight is read-only and revision-bound;
 * the backend still revalidates the locked Draft when the user confirms Publish.
 */
export const publishDevelopmentTask = async (
  nodeId: DevelopmentId,
  draftRevision: number,
): Promise<ApiResponse<DevelopmentTaskRevision>> => {
  const intl = getIntl();
  let validation: DevelopmentTaskPublishValidation;
  try {
    validation = responseData(
      await validatePublishRequest(nodeId, draftRevision),
      intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishValidationFailed' }),
    );
  } catch (error) {
    throw new Error(
      error instanceof Error && error.message
        ? error.message
        : intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishValidationFailed' }),
    );
  }

  const confirmed = await confirmPublishReadiness(validation);
  if (!validation.valid) {
    throw new Error(
      validation.message ||
        intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishBlocked' }),
    );
  }
  if (!confirmed) {
    throw new Error(
      intl.formatMessage({ id: 'pages.dataDevelopment.authoring.publishCancelled' }),
    );
  }
  return publishDevelopmentTaskRequest(nodeId, draftRevision);
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
