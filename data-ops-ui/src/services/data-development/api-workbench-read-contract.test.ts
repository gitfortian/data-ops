import HttpUtils from '@/utils/HttpUtils';
import {
  getActiveDevelopmentTaskExecution,
  getDevelopmentTaskDraft,
  getDevelopmentTaskExecution,
} from './api';

describe('Data Development Workbench modern GET contracts', () => {
  afterEach(() => jest.restoreAllMocks());

  it('GETs the same persisted Draft endpoint and returns the unwrapped exact revision', async () => {
    const draft = { nodeId: 'node/42', draftRevision: 9,
      definition: { taskType: 'SQL', content: 'select 1' } };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(draft);
    await expect(getDevelopmentTaskDraft('node/42')).resolves.toEqual(draft);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-development/nodes/node%2F42/draft');
  });

  it('encodes international and whitespace-bearing node identities for Draft GET', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue({ draftRevision: 1 });
    await getDevelopmentTaskDraft('SQL 项目 1');
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/nodes/SQL%20%E9%A1%B9%E7%9B%AE%201/draft',
    );
  });

  it('uses the original active-execution query endpoint and its encoded nodeId', async () => {
    const active = { id: 'run-1', runtimeExecutionId: 'engine-7', status: 'RUNNING' };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(active);
    await expect(getActiveDevelopmentTaskExecution('node/42')).resolves.toEqual(active);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions/active?nodeId=node%2F42',
    );
  });

  it('preserves null when the server reports no active execution', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(null);
    await expect(getActiveDevelopmentTaskExecution('node-1')).resolves.toBeNull();
    expect(getData).toHaveBeenCalledTimes(1);
  });

  it('uses stable encoded execution IDs for polling and terminal snapshots', async () => {
    const detail = { id: 'run/42', status: 'FAILED',
      retryOfExecutionId: 'run/41', runtimeExecutionId: 'engine-8' };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(detail);
    await expect(getDevelopmentTaskExecution('run/42')).resolves.toEqual(detail);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions/run%2F42',
    );
  });

  it('propagates business errors for Draft, active recovery, and execution detail reads', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockRejectedValueOnce(new Error('403 draft denied'))
      .mockRejectedValueOnce(new Error('500 recovery unavailable'))
      .mockRejectedValueOnce(new Error('404 execution unknown'));
    await expect(getDevelopmentTaskDraft('node-1')).rejects.toThrow('403 draft denied');
    await expect(getActiveDevelopmentTaskExecution('node-1'))
      .rejects.toThrow('500 recovery unavailable');
    await expect(getDevelopmentTaskExecution('run-1'))
      .rejects.toThrow('404 execution unknown');
    expect(getData).toHaveBeenCalledTimes(3);
  });
});
