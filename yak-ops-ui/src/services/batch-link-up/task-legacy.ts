import HttpUtils from '@/utils/HttpUtils';
import type { Key } from 'react';

export enum Operate {
  Add,
  Edit,
}

export const apiPrefix = '/api/v1/task-definition';

export const taskDefinitionApi = {
  create: (data: any) => HttpUtils.post(apiPrefix, data),

  batch: (data: any) => HttpUtils.post(`${apiPrefix}/batch`, data),

  page: (data: any): Promise<{ code: number; data: any; message?: string }> =>
    HttpUtils.post(`${apiPrefix}/page`, data),

  delete: (id: string) => HttpUtils.delete(`${apiPrefix}/${id}`),

  getLast5ExecutionTimes: (id: string) =>
    HttpUtils.get(`${apiPrefix}/${id}`),
};

export const taskScheduleApiPrefix = '/api/v1/task-schedule';

export const taskScheduleApi = {
  getLast5ExecutionTimes: (cron: string) =>
    HttpUtils.get<any[]>(
      `${taskScheduleApiPrefix}/last5-execution-times?cron=${encodeURIComponent(cron)}`,
    ),

  stopSchedule: (taskScheduleId: string) =>
    HttpUtils.get<any[]>(
      `${taskScheduleApiPrefix}/stop-schedule?taskScheduleId=${encodeURIComponent(taskScheduleId)}`,
    ),

  startSchedule: (taskScheduleId: string) =>
    HttpUtils.get<any[]>(
      `${taskScheduleApiPrefix}/start-schedule?taskScheduleId=${encodeURIComponent(taskScheduleId)}`,
    ),
};

export const linkupClientApi = {
  getLogsByInstanceId(instanceId: string | number, jobMode: any) {
    return HttpUtils.get<any[]>(
      `/api/v1/devops/client/instance/${instanceId}/logs?jobMode=${encodeURIComponent(jobMode)}`,
    );
  },
};

export const apiPrefixExecutor = '/api/v1/job/batch-execution';

export const batchJobExecutorApi = {
  batchExecute: (jobDefinitionIds: Key[]) =>
    HttpUtils.post(`${apiPrefixExecutor}/batch-execute`, {
      jobDefinitionIds: jobDefinitionIds.map(Number),
    }),

  batchPause: (jobDefinitionIds: Key[]) =>
    HttpUtils.post(`${apiPrefixExecutor}/batch-pause`, {
      jobDefinitionIds: jobDefinitionIds.map(Number),
    }),
};
