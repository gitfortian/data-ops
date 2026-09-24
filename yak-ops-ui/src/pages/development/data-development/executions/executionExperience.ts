import type { DevelopmentTaskExecutionStatus } from '../types';

const FAILURE_MESSAGE_IDS: Record<string, string> = {
  VALIDATION_FAILED: 'pages.dataDevelopment.execution.failure.validation',
  RUNTIME_SUBMIT_FAILED: 'pages.dataDevelopment.execution.failure.runtimeSubmit',
  TASK_FAILED: 'pages.dataDevelopment.execution.failure.task',
  TIMEOUT: 'pages.dataDevelopment.execution.failure.timeout',
  RUNTIME_NOT_ATTACHED: 'pages.dataDevelopment.execution.failure.runtimeNotAttached',
  RUNTIME_STATE_LOST: 'pages.dataDevelopment.execution.failure.runtimeStateLost',
};

export const isActiveExecutionStatus = (
  status?: DevelopmentTaskExecutionStatus | string | null,
) => status === 'PENDING' || status === 'RUNNING';

export const executionFailureMessageId = (reason?: string | null) =>
  reason ? FAILURE_MESSAGE_IDS[reason.trim().toUpperCase()] : undefined;

export const developmentNodeUrl = (nodeId: string | number) =>
  `/data-development?nodeId=${encodeURIComponent(String(nodeId))}`;

export const developmentNodeIdFromSearch = (search: string) => {
  const nodeId = new URLSearchParams(search).get('nodeId')?.trim();
  return nodeId || undefined;
};
