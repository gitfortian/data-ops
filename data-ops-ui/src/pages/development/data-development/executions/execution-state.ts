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

/** SQL submission may already have written to a target even when its
 * Runtime receipt is lost or timed out. Blind editor retry is unsafe. */
export const isAmbiguousSqlExecution = (
  detail: Pick<DevelopmentTaskExecutionDetail, 'taskType' | 'status' | 'failureReason'>,
): boolean => detail.taskType?.toUpperCase() === 'SQL' && (
  detail.status === 'TIMEOUT'
  || ['RUNTIME_STATE_LOST', 'RUNTIME_NOT_ATTACHED', 'RUNTIME_SUBMIT_FAILED']
    .includes(detail.failureReason?.toUpperCase() ?? '')
);

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
