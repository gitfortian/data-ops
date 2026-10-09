import HttpUtils from '@/utils/HttpUtils';
import {
  getRealtimeRuntimeCapabilities,
  getRealtimeSyncObservability,
  getRealtimeSyncRuntimeLog,
  getRealtimeSyncSubmissionLog,
  getRealtimeSyncTask,
  listRealtimeCatalogColumns,
  listRealtimeCatalogTables,
  listRealtimeDataSources,
  parseRealtimeSyncYaml,
  performRealtimeSyncAction,
  renderRealtimeSyncYaml,
  updateRealtimeSyncTask,
  validateRealtimeSyncDefinition,
} from './api';

describe('Realtime Sync editor / runtime data-only compatibility retirement', () => {
  afterEach(() => jest.restoreAllMocks());

  it('keeps the exact task detail GET identity without returning an envelope', async () => {
    const job = { id: 17, name: 'orders', desiredState: 'STOPPED' };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(job);
    await expect(getRealtimeSyncTask(17)).resolves.toEqual(job);
    expect(getData).toHaveBeenCalledWith('/api/v1/realtime-sync/17');
  });

  it('reads Data Source options and catalog tables as data-only arrays', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValueOnce([])
      .mockResolvedValueOnce([{ name: 'orders', type: 'TABLE' }]);
    await expect(listRealtimeDataSources()).resolves.toEqual([]);
    await expect(listRealtimeCatalogTables(9)).resolves.toEqual([{ name: 'orders', type: 'TABLE' }]);
    expect(getData).toHaveBeenNthCalledWith(1, '/api/v1/data-source/option');
    expect(getData).toHaveBeenNthCalledWith(2, '/api/v1/data-source/catalog/9/tables');
  });

  it('keeps catalog column table identity and optional database/schema query encoding', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue([]);
    await expect(listRealtimeCatalogColumns(9, {
      name: 'order items', database: 'sales', schema: 'public',
    })).resolves.toEqual([]);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/9/columns?database=sales&schema=public&table=order+items',
    );
  });

  it('parses YAML to a spec and renders only the returned YAML string', async () => {
    const spec = { sourceDataSourceRef: 1, sinkDataSourceRef: 2, tables: [] } as any;
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockResolvedValueOnce(spec).mockResolvedValueOnce({ yaml: 'version: 1' });
    await expect(parseRealtimeSyncYaml('version: 1')).resolves.toEqual(spec);
    await expect(renderRealtimeSyncYaml(spec)).resolves.toBe('version: 1');
    expect(postData).toHaveBeenNthCalledWith(1,
      '/api/v1/realtime-sync/yaml/parse', { yaml: 'version: 1' });
    expect(postData).toHaveBeenNthCalledWith(2,
      '/api/v1/realtime-sync/yaml/render', { spec });
  });

  it('preserves Spec/environment validation and Draft update POST/PUT contracts', async () => {
    const spec = { sourceDataSourceRef: 1, sinkDataSourceRef: 2, tables: [] } as any;
    const validation = { valid: true, deliverySemantics: 'at-least-once' };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(validation);
    const putData = jest.spyOn(HttpUtils, 'putData').mockResolvedValue(17);
    await expect(validateRealtimeSyncDefinition(spec, 5)).resolves.toEqual(validation);
    const payload = { name: 'orders', runtimeEnvironmentId: 5, spec };
    await expect(updateRealtimeSyncTask(17, payload)).resolves.toBe(17);
    expect(postData).toHaveBeenCalledWith('/api/v1/realtime-sync/spec/validate',
      { spec, runtimeEnvironmentId: 5 });
    expect(putData).toHaveBeenCalledWith('/api/v1/realtime-sync/17', payload);
  });

  it('runtime start keeps an idempotency header; stop does not invent one', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue({ id: 22 });
    await expect(performRealtimeSyncAction(17, 'start')).resolves.toEqual({ id: 22 });
    expect(postData).toHaveBeenNthCalledWith(1, '/api/v1/realtime-sync/17/start', {},
      expect.objectContaining({ headers: expect.objectContaining({
        'Idempotency-Key': expect.any(String),
      }) }));
    await performRealtimeSyncAction(17, 'stop');
    expect(postData).toHaveBeenNthCalledWith(2,
      '/api/v1/realtime-sync/17/stop', {}, undefined);
  });

  it('queries exact environment capability and runtime observability without envelopes', async () => {
    const observation = { engineJobId: 'flink-123', sampledAt: 12345 };
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce({ deployEnabled: true }).mockResolvedValueOnce(observation);
    await expect(getRealtimeRuntimeCapabilities(5)).resolves.toEqual({ deployEnabled: true });
    await expect(getRealtimeSyncObservability(17)).resolves.toEqual(observation);
    expect(getData).toHaveBeenNthCalledWith(1,
      '/api/v1/realtime-sync/runtime/capabilities?environmentId=5');
    expect(getData).toHaveBeenNthCalledWith(2,
      '/api/v1/realtime-sync/17/observability');
  });

  it('reads submission logs as a string and runtime exceptions as their own payload', async () => {
    const runtimeLog = { truncated: false, exceptions: [] };
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce({ logs: 'submitted' }).mockResolvedValueOnce(runtimeLog);
    await expect(getRealtimeSyncSubmissionLog(17)).resolves.toBe('submitted');
    await expect(getRealtimeSyncRuntimeLog(17)).resolves.toEqual(runtimeLog);
    expect(getData).toHaveBeenNthCalledWith(1,
      '/api/v1/realtime-sync/17/logs/submission?tail=500');
    expect(getData).toHaveBeenNthCalledWith(2,
      '/api/v1/realtime-sync/17/logs/runtime?maxExceptions=50');
  });

  it('propagates transport and business failures rather than misreporting empty data', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockRejectedValue(new Error('403 Project access denied'));
    await expect(getRealtimeSyncTask(17)).rejects.toThrow('403 Project access denied');
    await expect(listRealtimeCatalogColumns(9, { name: 'orders' })).rejects.toThrow(
      '403 Project access denied');
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValue(new Error('409 DefinitionVersion changed'));
    await expect(performRealtimeSyncAction(17, 'apply-published-version'))
      .rejects.toThrow('409 DefinitionVersion changed');
  });
});
