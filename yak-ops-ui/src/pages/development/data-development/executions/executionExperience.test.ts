import {
  developmentNodeIdFromSearch,
  developmentNodeUrl,
  executionFailureMessageId,
  executionLogText,
  executionSqlOutput,
  hasExecutionOutput,
  isActiveExecutionStatus,
  isRetryableExecutionStatus,
} from './executionExperience';

describe('execution experience', () => {
  it('keeps active execution refresh limited to pending/running states', () => {
    expect(isActiveExecutionStatus('PENDING')).toBe(true);
    expect(isActiveExecutionStatus('RUNNING')).toBe(true);
    expect(isActiveExecutionStatus('SUCCESS')).toBe(false);
    expect(isActiveExecutionStatus('FAILED')).toBe(false);
    expect(isActiveExecutionStatus('TIMEOUT')).toBe(false);
  });

  it('only exposes retry for terminal states whose persisted definition can be replayed', () => {
    expect(isRetryableExecutionStatus('FAILED')).toBe(true);
    expect(isRetryableExecutionStatus('TIMEOUT')).toBe(true);
    expect(isRetryableExecutionStatus('CANCELLED')).toBe(true);
    expect(isRetryableExecutionStatus('RUNNING')).toBe(false);
    expect(isRetryableExecutionStatus('SUCCESS')).toBe(false);
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

  it('keeps SQL result data distinct from raw runtime output', () => {
    const output = {
      kind: 'RESULT_SET',
      columns: [{ name: 'value', label: 'value', typeName: 'INTEGER' }],
      rows: [[1]],
      returnedRows: 1,
      dataSourceId: '9',
      stdout: 'query finished',
    };
    expect(executionSqlOutput(output)).toMatchObject({
      kind: 'RESULT_SET',
      returnedRows: 1,
      dataSourceId: '9',
    });
    expect(hasExecutionOutput(output)).toBe(true);
    expect(executionLogText(output)).toBe('stdout:\nquery finished');
  });

  it('extracts persisted stdout/stderr/logs without treating arbitrary output as logs', () => {
    expect(
      executionLogText({ logs: ['line 1', 'line 2'], stderr: 'boom' }),
    ).toBe('line 1\nline 2\n\nstderr:\nboom');
    expect(executionLogText({ rows: [[1]] })).toBeUndefined();
    expect(executionSqlOutput({ rows: [[1]] })).toBeUndefined();
    expect(hasExecutionOutput({})).toBe(false);
  });
});
