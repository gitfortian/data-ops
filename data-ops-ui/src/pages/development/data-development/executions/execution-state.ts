import type {
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionStatus,
  DevelopmentTaskExecutionSubmission,
  DevelopmentTaskRunResult,
} from '../types';

export const isDevelopmentExecutionActive = (
  status?: DevelopmentTaskExecutionStatus,
) => status === 'PENDING' || status === 'RUNNING';

export const isDevelopmentExecutionRetryable = (
  status?: DevelopmentTaskExecutionStatus,
) => status === 'FAILED' || status === 'CANCELLED' || status === 'TIMEOUT';

export const executionSubmissionToRunResult = (
  submission: DevelopmentTaskExecutionSubmission,
): DevelopmentTaskRunResult => ({
  executionId: submission.id,
  runtimeExecutionId: submission.runtimeExecutionId,
  status: submission.status,
  message: '',
  durationMs: 0,
  output: {},
});

export const executionDetailToRunResult = (
  detail: DevelopmentTaskExecutionDetail,
): DevelopmentTaskRunResult => ({
  executionId: detail.id,
  runtimeExecutionId: detail.runtimeExecutionId,
  status: detail.status,
  message: detail.errorMessage || '',
  durationMs: detail.durationMs || 0,
  output: detail.output || {},
});
