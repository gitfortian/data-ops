import {
  executionDetailToRunResult,
  executionSubmissionToRunResult,
  isDevelopmentExecutionActive,
  isDevelopmentExecutionRetryable,
} from './execution-state';

import type {
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionSubmission,
} from '../types';

describe('data-development execution state', () => {
  it('classifies active and retryable terminal states', () => {
    expect(isDevelopmentExecutionActive('PENDING')).toBe(true);
    expect(isDevelopmentExecutionActive('RUNNING')).toBe(true);
    expect(isDevelopmentExecutionActive('SUCCESS')).toBe(false);
    expect(isDevelopmentExecutionRetryable('FAILED')).toBe(true);
    expect(isDevelopmentExecutionRetryable('TIMEOUT')).toBe(true);
    expect(isDevelopmentExecutionRetryable('CANCELLED')).toBe(true);
    expect(isDevelopmentExecutionRetryable('SUCCESS')).toBe(false);
  });

  it('projects submission and durable detail into the existing workbench result contract', () => {
    const submission: DevelopmentTaskExecutionSubmission = {
      id: '10',
      nodeId: '7',
      taskType: 'SQL',
      runtimeExecutionId: 'sql-1',
      status: 'RUNNING',
    };
    expect(executionSubmissionToRunResult(submission)).toMatchObject({
      executionId: '10',
      runtimeExecutionId: 'sql-1',
      status: 'RUNNING',
    });

    const detail: DevelopmentTaskExecutionDetail = {
      id: '10',
      nodeId: '7',
      taskName: '测试 SQL',
      taskType: 'SQL',
      schemaVersion: 1,
      triggerType: 'MANUAL',
      runtimeExecutionId: 'sql-1',
      retryOfExecutionId: null,
      status: 'SUCCESS',
      operatorName: 'bruce',
      durationMs: 88,
      errorMessage: null,
      startTime: '2026-08-28T09:00:00',
      endTime: '2026-08-28T09:00:00',
      content: 'select 1',
      configJson: '{}',
      output: { rows: 1 },
    };
    expect(executionDetailToRunResult(detail)).toEqual({
      executionId: '10',
      runtimeExecutionId: 'sql-1',
      status: 'SUCCESS',
      message: '',
      durationMs: 88,
      output: { rows: 1 },
    });
  });
});
