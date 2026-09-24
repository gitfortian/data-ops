import {
  developmentNodeIdFromSearch,
  developmentNodeUrl,
  executionFailureMessageId,
  isActiveExecutionStatus,
} from './executionExperience';

describe('execution experience', () => {
  it('keeps active execution refresh limited to pending/running states', () => {
    expect(isActiveExecutionStatus('PENDING')).toBe(true);
    expect(isActiveExecutionStatus('RUNNING')).toBe(true);
    expect(isActiveExecutionStatus('SUCCESS')).toBe(false);
    expect(isActiveExecutionStatus('FAILED')).toBe(false);
    expect(isActiveExecutionStatus('TIMEOUT')).toBe(false);
  });

  it('maps durable failure reasons to stable product copy ids', () => {
    expect(executionFailureMessageId('RUNTIME_STATE_LOST')).toBe(
      'pages.dataDevelopment.execution.failure.runtimeStateLost',
    );
    expect(executionFailureMessageId('runtime_not_attached')).toBe(
      'pages.dataDevelopment.execution.failure.runtimeNotAttached',
    );
    expect(executionFailureMessageId('UNKNOWN_REASON')).toBeUndefined();
  });

  it('round-trips the exact development node through history navigation', () => {
    expect(developmentNodeUrl('node / 42')).toBe(
      '/data-development?nodeId=node%20%2F%2042',
    );
    expect(
      developmentNodeIdFromSearch('?nodeId=node%20%2F%2042&source=execution'),
    ).toBe('node / 42');
    expect(developmentNodeIdFromSearch('?source=execution')).toBeUndefined();
  });
});
