import HttpUtils from '@/utils/HttpUtils';

import type {
  BatchLinkUpId,
  OfflineBatchOperationResult,
  OfflineJobDefinitionVO,
  OfflineJobExecutionVO,
  OfflinePublishResult,
  OfflineSyncTaskPageQuery,
  OfflineVersionDetail,
  OfflineVersionSummary,
  PagingData,
} from './types';

const DEFINITION_API = '/api/v1/job/batch-definition';
const EXECUTION_API = '/api/v1/job/batch-execution';

type IdentifierResponse = BatchLinkUpId | { id?: BatchLinkUpId };

const identifierFromResponse = (value: IdentifierResponse): BatchLinkUpId | undefined => {
  const identifier =
    value && typeof value === 'object' ? value.id : value;
  return identifier === undefined || identifier === null || identifier === ''
    ? undefined
    : identifier;
};

export const listOfflineSyncTasks = (
  query: OfflineSyncTaskPageQuery,
): Promise<PagingData<OfflineJobDefinitionVO>> =>
  HttpUtils.postData<PagingData<OfflineJobDefinitionVO>>(
    `${DEFINITION_API}/page`,
    query,
  );

export const getOfflineSyncTask = (
  id: BatchLinkUpId,
): Promise<OfflineJobDefinitionVO> =>
  HttpUtils.getData<OfflineJobDefinitionVO>(
    `${DEFINITION_API}/${encodeURIComponent(id)}`,
  );

export const getOfflineSyncEditDetail = (
  id: BatchLinkUpId,
): Promise<Record<string, unknown>> =>
  HttpUtils.getData<Record<string, unknown>>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/edit-detail`,
  );

export const getOfflineSyncUniqueId = async (): Promise<BatchLinkUpId> => {
  const response = await HttpUtils.getData<IdentifierResponse>(
    `${DEFINITION_API}/get-unique-id`,
  );
  const identifier = identifierFromResponse(response);
  if (identifier === undefined) throw new Error('生成任务 ID 失败');
  return identifier;
};

export const createOfflineSyncDraft = async (
  payload: Record<string, unknown>,
): Promise<BatchLinkUpId | undefined> =>
  identifierFromResponse(
    await HttpUtils.postData<IdentifierResponse>(
      `${DEFINITION_API}/draft`,
      payload,
    ),
  );

export const saveOfflineSyncSingleGuide = async (
  payload: Record<string, unknown>,
): Promise<BatchLinkUpId | undefined> =>
  identifierFromResponse(
    await HttpUtils.postData<IdentifierResponse>(
      `${DEFINITION_API}/guide-single/saveOrUpdate`,
      payload,
    ),
  );

export const saveOfflineSyncMultiGuide = async (
  payload: Record<string, unknown>,
): Promise<BatchLinkUpId | undefined> =>
  identifierFromResponse(
    await HttpUtils.postData<IdentifierResponse>(
      `${DEFINITION_API}/guide-multi/saveOrUpdate`,
      payload,
    ),
  );

export const deleteOfflineSyncTask = async (
  id: BatchLinkUpId,
): Promise<void> => {
  await HttpUtils.deleteData<boolean>(
    `${DEFINITION_API}/${encodeURIComponent(id)}`,
  );
};

export const onlineOfflineSyncTask = async (
  id: BatchLinkUpId,
): Promise<void> => {
  await HttpUtils.putData<boolean>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/online`,
  );
};

export const offlineOfflineSyncTask = async (
  id: BatchLinkUpId,
): Promise<void> => {
  await HttpUtils.putData<boolean>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/offline`,
  );
};

export const publishOfflineSyncTask = (
  id: BatchLinkUpId,
): Promise<OfflinePublishResult> =>
  HttpUtils.postData<OfflinePublishResult>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/publish`,
    {},
  );

export const listOfflineSyncVersions = (
  id: BatchLinkUpId,
): Promise<OfflineVersionSummary[]> =>
  HttpUtils.getData<OfflineVersionSummary[]>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/versions`,
  );

export const getOfflineSyncVersion = (
  id: BatchLinkUpId,
  versionNo: number,
): Promise<OfflineVersionDetail> =>
  HttpUtils.getData<OfflineVersionDetail>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/versions/${versionNo}`,
  );

export const rollbackOfflineSyncVersion = (
  id: BatchLinkUpId,
  versionNo: number,
): Promise<OfflinePublishResult> =>
  HttpUtils.postData<OfflinePublishResult>(
    `${DEFINITION_API}/${encodeURIComponent(id)}/versions/${versionNo}/rollback`,
    {},
  );

export const executeOfflineSyncTask = (
  definitionId: BatchLinkUpId,
): Promise<OfflineJobExecutionVO> =>
  HttpUtils.postData<OfflineJobExecutionVO>(
    `${EXECUTION_API}/${encodeURIComponent(definitionId)}/execute`,
    {},
  );

export const stopOfflineSyncExecution = (
  instanceId: BatchLinkUpId,
): Promise<OfflineJobExecutionVO> =>
  HttpUtils.postData<OfflineJobExecutionVO>(
    `${EXECUTION_API}/${encodeURIComponent(instanceId)}/cancel`,
    {},
  );

export const batchStartOfflineSyncTasks = (
  definitionIds: BatchLinkUpId[],
): Promise<OfflineBatchOperationResult> =>
  HttpUtils.postData<OfflineBatchOperationResult>(
    `${EXECUTION_API}/batch-execute`,
    { jobDefinitionIds: definitionIds.map(Number) },
  );

export const batchStopOfflineSyncTasks = (
  definitionIds: BatchLinkUpId[],
): Promise<OfflineBatchOperationResult> =>
  HttpUtils.postData<OfflineBatchOperationResult>(
    `${EXECUTION_API}/batch-pause`,
    { jobDefinitionIds: definitionIds.map(Number) },
  );
