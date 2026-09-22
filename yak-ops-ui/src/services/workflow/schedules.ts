import type { ApiResponse } from '@/services/http/response';
import { request } from '@umijs/max';

export type WorkflowScheduleStatus = 'ONLINE' | 'OFFLINE';
export type WorkflowScheduleExecutionStrategy =
  | 'PARALLEL'
  | 'SERIAL_WAIT'
  | 'SERIAL_DISCARD';
export type WorkflowScheduleMisfireStrategy = 'SKIP' | 'FIRE_ONCE';
export type WorkflowScheduleTriggerStatus =
  | 'RECEIVED'
  | 'WAITING'
  | 'LAUNCHING'
  | 'REACTIVATING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED'
  | 'SKIPPED';
export type WorkflowBackfillStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'
  | 'CANCELED';
export type WorkflowBackfillExecutionStrategy = 'SERIAL_WAIT' | 'PARALLEL';
export type WorkflowBackfillOperationType = 'BACKFILL' | 'BUSINESS_DATE_RERUN';

export interface WorkflowSchedule {
  id: string;
  workflowId: string;
  name: string;
  triggerType: 'CRON';
  cronExpression: string;
  timezone: string;
  startTime?: string;
  endTime?: string;
  status: WorkflowScheduleStatus;
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  input: Record<string, unknown>;
  lastFireTime?: string;
  nextFireTime?: string;
  createTime: string;
  updateTime: string;
}

export interface WorkflowScheduleTrigger {
  id: string;
  scheduleId: string;
  workflowId: string;
  backfillId?: string;
  triggerId: string;
  triggerSource:
    | 'CRON'
    | 'MANUAL'
    | 'MISFIRE_RECOVERY'
    | 'BACKFILL'
    | 'BUSINESS_DATE_RERUN'
    | string;
  businessDate?: string;
  plannedFireTime: string;
  actualFireTime: string;
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  status: WorkflowScheduleTriggerStatus;
  workflowExecutionId?: string;
  executionStatus?: string;
  message?: string;
  errorMessage?: string;
  launchedAt?: string;
  completedAt?: string;
  createTime: string;
  updateTime: string;
}

export interface WorkflowBackfillPayload {
  scheduleId: string;
  name?: string;
  startBusinessDate: string;
  endBusinessDate: string;
  executionStrategy: WorkflowBackfillExecutionStrategy;
  input: Record<string, unknown>;
}

export interface WorkflowBackfillPreviewOccurrence {
  businessDate: string;
  scheduleInstant: string;
  scheduleTime: string;
}

export interface WorkflowBackfillPreview {
  scheduleId: string;
  cronExpression: string;
  timezone: string;
  startBusinessDate: string;
  endBusinessDate: string;
  totalCount: number;
  truncated: boolean;
  occurrences: WorkflowBackfillPreviewOccurrence[];
}

export interface WorkflowBackfill {
  id: string;
  workflowId: string;
  workflowVersionId: string;
  workflowVersionNo: number;
  scheduleId: string;
  scheduleName: string;
  name: string;
  status: WorkflowBackfillStatus;
  operationType: WorkflowBackfillOperationType;
  sourceExecutionId?: string;
  startBusinessDate: string;
  endBusinessDate: string;
  cronExpression: string;
  timezone: string;
  executionStrategy: WorkflowBackfillExecutionStrategy;
  input: Record<string, unknown>;
  totalCount: number;
  waitingCount: number;
  runningCount: number;
  succeededCount: number;
  failedCount: number;
  canceledCount: number;
  skippedCount: number;
  createTime: string;
  updateTime: string;
}

export interface WorkflowSchedulePayload {
  name: string;
  cronExpression: string;
  timezone: string;
  startTime?: string;
  endTime?: string;
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  input: Record<string, unknown>;
}

export const listWorkflowSchedules = async (params?: {
  workflowId?: string;
  status?: WorkflowScheduleStatus;
}) => {
  const response = await request<ApiResponse<WorkflowSchedule[]>>(
    '/api/v1/workflows/schedules',
    { params },
  );
  return response.data || [];
};

export const listWorkflowScheduleTriggers = async (params?: {
  scheduleId?: string;
  workflowId?: string;
  backfillId?: string;
  status?: WorkflowScheduleTriggerStatus;
  limit?: number;
}) => {
  const response = await request<ApiResponse<WorkflowScheduleTrigger[]>>(
    '/api/v1/workflows/schedules/triggers',
    { params },
  );
  return response.data || [];
};

export const previewWorkflowBackfill = async (payload: WorkflowBackfillPayload) => {
  const response = await request<ApiResponse<WorkflowBackfillPreview>>(
    '/api/v1/workflows/backfills/preview',
    { method: 'POST', data: payload },
  );
  return response.data;
};

export const createWorkflowBackfill = async (payload: WorkflowBackfillPayload) => {
  const response = await request<ApiResponse<WorkflowBackfill>>(
    '/api/v1/workflows/backfills',
    { method: 'POST', data: payload },
  );
  return response.data;
};

export const listWorkflowBackfills = async (params?: {
  workflowId?: string;
  scheduleId?: string;
  status?: WorkflowBackfillStatus;
}) => {
  const response = await request<ApiResponse<WorkflowBackfill[]>>(
    '/api/v1/workflows/backfills',
    { params },
  );
  return response.data || [];
};

export const getWorkflowBackfill = async (id: string) => {
  const response = await request<ApiResponse<WorkflowBackfill>>(
    `/api/v1/workflows/backfills/${encodeURIComponent(id)}`,
  );
  return response.data;
};

export const cancelWorkflowBackfill = async (id: string) => {
  const response = await request<ApiResponse<WorkflowBackfill>>(
    `/api/v1/workflows/backfills/${encodeURIComponent(id)}/cancel`,
    { method: 'POST' },
  );
  return response.data;
};

export const createWorkflowSchedule = async (
  workflowId: string,
  payload: WorkflowSchedulePayload,
) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    '/api/v1/workflows/schedules',
    { method: 'POST', data: { workflowId, ...payload } },
  );
  return response.data;
};

export const updateWorkflowSchedule = async (
  id: string,
  payload: WorkflowSchedulePayload,
) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}`,
    { method: 'PUT', data: payload },
  );
  return response.data;
};

const scheduleAction = async (id: string, action: 'online' | 'offline') => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}/${action}`,
    { method: 'POST' },
  );
  return response.data;
};

export const onlineWorkflowSchedule = (id: string) => scheduleAction(id, 'online');
export const offlineWorkflowSchedule = (id: string) => scheduleAction(id, 'offline');

export const deleteWorkflowSchedule = async (id: string) => {
  await request<ApiResponse<boolean>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}`,
    { method: 'DELETE' },
  );
};
