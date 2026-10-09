import HttpUtils from '@/utils/HttpUtils';
import { getDevelopmentTaskExecution } from './api';

describe('Data Development execution detail modern read contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('uses the same GET execution detail route and returns unwrapped runtime evidence', async () => {
    const detail = { id: 'run-7', nodeId: 'node-1', runtimeExecutionId: 'engine-11',
      status: 'SUCCESS', output: { stdout: 'done' } };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(detail);
    await expect(getDevelopmentTaskExecution('run-7')).resolves.toEqual(detail);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-development/executions/run-7');
  });

  it('preserves percent-encoded identities for execution detail reads', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue({ id: 'run/7 例' });
    await expect(getDevelopmentTaskExecution('run/7 例')).resolves.toEqual({ id: 'run/7 例' });
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-development/executions/run%2F7%20%E4%BE%8B',
    );
  });

  it('propagates business/transport errors instead of fabricating a missing execution', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockRejectedValue(new Error('403 forbidden'));
    await expect(getDevelopmentTaskExecution('run-7')).rejects.toThrow('403 forbidden');
    expect(getData).toHaveBeenCalledTimes(1);
  });
});
