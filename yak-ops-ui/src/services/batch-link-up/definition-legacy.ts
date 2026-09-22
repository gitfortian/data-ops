import type { ApiResponse } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';

import type {
  BatchLinkUpId,
  OfflineExecutionLogPage,
  OfflineJobDefinitionVO,
  OfflineJobExecutionDetailVO,
  OfflineJobExecutionVO,
  OfflineTableMetric,
  PagingData,
} from './types';

export enum Operate {
  Add,
  Edit,
}

const toPositiveSafeInteger = (value: unknown, fieldName: string) => {
  const normalizedValue = typeof value === 'string' ? value.trim() : value;
  const numericValue = Number(normalizedValue);
  if (!Number.isSafeInteger(numericValue) || numericValue < 1) {
    throw new Error(`${fieldName} 必须是安全的正整数`);
  }
  return numericValue;
};

const normalizeOfflineInstancePageRequest = (
  data: Record<string, unknown>,
): Record<string, unknown> => {
  const { pageNo, pageNum, jobDefinitionId, ...rest } = data;
  const current = toPositiveSafeInteger(
    data.current ?? pageNo ?? pageNum ?? 1,
    '页码',
  );
  const pageSize = toPositiveSafeInteger(data.pageSize ?? 10, '每页条数');
  if (pageSize > 200) throw new Error('每页条数不能超过 200');
  return {
    ...rest,
    current,
    pageSize,
    ...(jobDefinitionId === undefined ||
    jobDefinitionId === null ||
    jobDefinitionId === ''
      ? {}
      : {
          jobDefinitionId: toPositiveSafeInteger(
            jobDefinitionId,
            '任务定义 ID',
          ),
        }),
  };
};

export const apiPrefix = '/api/v1/job/batch-definition';
export const linkupJobDefinitionApi = {
  createDraft: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<BatchLinkUpId>> =>
    HttpUtils.post(`${apiPrefix}/draft`, data),
  saveOrUpdateGuideSingle: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<BatchLinkUpId>> =>
    HttpUtils.post(`${apiPrefix}/guide-single/saveOrUpdate`, data),
  saveOrUpdateGuideMulti: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<BatchLinkUpId>> =>
    HttpUtils.post(`${apiPrefix}/guide-multi/saveOrUpdate`, data),
  selectById: (
    id: BatchLinkUpId,
  ): Promise<ApiResponse<OfflineJobDefinitionVO>> =>
    HttpUtils.get(`${apiPrefix}/${id}`),
  selectEditDetail: (
    id: BatchLinkUpId,
  ): Promise<ApiResponse<Record<string, unknown>>> =>
    HttpUtils.get(`${apiPrefix}/${id}/edit-detail`),
  getUniqueId: (): Promise<ApiResponse<BatchLinkUpId>> =>
    HttpUtils.get(`${apiPrefix}/get-unique-id`),
  delete: (id: BatchLinkUpId): Promise<ApiResponse<boolean>> =>
    HttpUtils.delete(`${apiPrefix}/${id}`),
  online: (id: BatchLinkUpId): Promise<ApiResponse<boolean>> =>
    HttpUtils.put(`${apiPrefix}/${id}/online`),
  offline: (id: BatchLinkUpId): Promise<ApiResponse<boolean>> =>
    HttpUtils.put(`${apiPrefix}/${id}/offline`),
  page: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<PagingData<OfflineJobDefinitionVO>>> =>
    HttpUtils.post(`${apiPrefix}/page`, data),
  buildGuideSingleConfig: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<string>> =>
    HttpUtils.post(`${apiPrefix}/guide-single/build-config`, data),
  buildGuideMultiConfig: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<string>> =>
    HttpUtils.post(`${apiPrefix}/guide-multi/build-config`, data),
  buildJobSpec: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<string>> =>
    HttpUtils.post(`${apiPrefix}/build-job-spec`, data),
};

export const executeApiPrefix = '/api/v1/job/batch-execution';
export const linkupJobExecuteApi = {
  health: (): Promise<ApiResponse<Record<string, unknown>>> =>
    HttpUtils.get(`${executeApiPrefix}/health`),
  execute: (
    jobDefineId: BatchLinkUpId,
  ): Promise<ApiResponse<OfflineJobExecutionVO>> =>
    HttpUtils.post(
      `${executeApiPrefix}/${encodeURIComponent(jobDefineId)}/execute`,
      {},
    ),
  pause: (
    jobInstanceId: BatchLinkUpId,
  ): Promise<ApiResponse<OfflineJobExecutionVO>> =>
    HttpUtils.post(
      `${executeApiPrefix}/${encodeURIComponent(jobInstanceId)}/cancel`,
      {},
    ),
};

const instanceApiPrefix = '/api/v1/job/batch-instance';
export const linkupJobInstanceApi = {
  page: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<PagingData<OfflineJobExecutionVO>>> =>
    HttpUtils.post(
      `${instanceApiPrefix}/page`,
      normalizeOfflineInstancePageRequest(data),
    ),
  selectById: (
    id: BatchLinkUpId,
  ): Promise<ApiResponse<OfflineJobExecutionDetailVO>> =>
    HttpUtils.get(`${instanceApiPrefix}/${id}`),
  getLog: (instanceId: BatchLinkUpId): Promise<ApiResponse<string>> =>
    HttpUtils.get(`${instanceApiPrefix}/${instanceId}/log`),
  getLogs: (
    instanceId: BatchLinkUpId,
    cursor = '0:0',
    limit = 500,
  ): Promise<ApiResponse<OfflineExecutionLogPage>> =>
    HttpUtils.get(
      `${instanceApiPrefix}/${encodeURIComponent(instanceId)}/logs?cursor=${encodeURIComponent(cursor)}&limit=${limit}`,
    ),
};

const linkupJobScheduleApiPrefix = '/api/v1/job/schedule';
export const linkupJobScheduleApi = {
  getLast5ExecutionTimes: (cron: string): Promise<ApiResponse<string[]>> =>
    HttpUtils.get(
      `${linkupJobScheduleApiPrefix}/last5-execution-times?cron=${encodeURIComponent(cron)}`,
    ),
  stopSchedule: (jobScheduleId: string) =>
    HttpUtils.get<any[]>(
      `${linkupJobScheduleApiPrefix}/stop-schedule?scheduleId=${encodeURIComponent(jobScheduleId)}`,
    ),
  startSchedule: (jobScheduleId: string) =>
    HttpUtils.get<any[]>(
      `${linkupJobScheduleApiPrefix}/start-schedule?scheduleId=${encodeURIComponent(jobScheduleId)}`,
    ),
};

const linkupCopilotApiPrefix = '/api/v1/copilot/ai';
export const linkupCopilotApi = {
  copilot: (data: any) =>
    HttpUtils.post<any[]>(`${linkupCopilotApiPrefix}/agent`, data),
};

export const batchJobInstanceApi = {
  page: (
    data: Record<string, unknown>,
  ): Promise<ApiResponse<PagingData<OfflineJobExecutionVO>>> =>
    HttpUtils.post(
      '/api/v1/job/batch-instance/page',
      normalizeOfflineInstancePageRequest(data),
    ),
  detail: (
    id: BatchLinkUpId,
  ): Promise<ApiResponse<OfflineJobExecutionDetailVO>> =>
    HttpUtils.get(`/api/v1/job/batch-instance/${id}`),
  tableMetrics: (
    instanceId: BatchLinkUpId,
  ): Promise<ApiResponse<OfflineTableMetric[]>> =>
    HttpUtils.get(
      `/api/v1/job/batch-instance/${instanceId}/table-metrics`,
    ),
  log: (instanceId: BatchLinkUpId): Promise<ApiResponse<string>> =>
    HttpUtils.get(`/api/v1/job/batch-instance/${instanceId}/log`),
  logs: (
    instanceId: BatchLinkUpId,
    cursor = '0:0',
    limit = 500,
  ): Promise<ApiResponse<OfflineExecutionLogPage>> =>
    HttpUtils.get(
      `/api/v1/job/batch-instance/${encodeURIComponent(instanceId)}/logs?cursor=${encodeURIComponent(cursor)}&limit=${limit}`,
    ),
};
