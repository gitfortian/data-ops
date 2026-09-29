import type {
  DevelopmentSqlRunOutput,
  DevelopmentTaskExecutionStatus,
} from '../types';

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

export const isRetryableExecutionStatus = (
  status?: DevelopmentTaskExecutionStatus | string | null,
) => status === 'FAILED' || status === 'CANCELLED' || status === 'TIMEOUT';

export const executionFailureMessageId = (reason?: string | null) =>
  reason ? FAILURE_MESSAGE_IDS[reason.trim().toUpperCase()] : undefined;

export const developmentNodeUrl = (nodeId: string | number) =>
  `/data-development?nodeId=${encodeURIComponent(String(nodeId))}`;

export const developmentNodeIdFromSearch = (search: string) => {
  const nodeId = new URLSearchParams(search).get('nodeId')?.trim();
  return nodeId || undefined;
};

const textValue = (value: unknown): string | undefined => {
  if (typeof value === 'string') return value.trim() || undefined;
  if (Array.isArray(value)) {
    const lines = value
      .map((item) => (typeof item === 'string' ? item : JSON.stringify(item)))
      .filter(Boolean);
    return lines.length ? lines.join('\n') : undefined;
  }
  if (value && typeof value === 'object') return JSON.stringify(value, null, 2);
  if (value !== undefined && value !== null) return String(value);
  return undefined;
};

/** Extract persisted runtime log-like evidence without pretending raw output is a log stream. */
export const executionLogText = (
  output?: Record<string, unknown> | null,
): string | undefined => {
  if (!output) return undefined;
  const chunks: string[] = [];
  for (const key of ['logs', 'log', 'stdout', 'stderr']) {
    const value = textValue(output[key]);
    if (!value) continue;
    chunks.push(key === 'logs' || key === 'log' ? value : `${key}:\n${value}`);
  }
  return chunks.length ? chunks.join('\n\n') : undefined;
};

export const executionSqlOutput = (
  output?: Record<string, unknown> | null,
): DevelopmentSqlRunOutput | undefined => {
  if (!output) return undefined;
  const kind = output.kind;
  if (kind !== 'RESULT_SET' && kind !== 'UPDATE_COUNT') return undefined;
  return output as DevelopmentSqlRunOutput;
};

export const hasExecutionOutput = (
  output?: Record<string, unknown> | null,
) => Boolean(output && Object.keys(output).length);
