import HttpUtils from '@/utils/HttpUtils';
import {
  batchStartOfflineSyncTasks, batchStopOfflineSyncTasks,
  getOfflineSyncClientLogs, getOfflineSyncInstanceDetail,
  getOfflineSyncInstanceLog, getOfflineSyncScheduleTimes,
  getOfflineSyncTask, listOfflineSyncInstances, listOfflineSyncTableMetrics,
  listOfflineSyncTasks, normalizeOfflineInstancePageRequest,
} from './api';
import { linkupJobInstanceApi } from './definition-legacy';

describe('Offline Sync modern runtime transport and legacy adapter parity', () => {
  afterEach(() => jest.restoreAllMocks());

  it('normalizes pagination aliases without dropping other filters', () => {
    expect(normalizeOfflineInstancePageRequest({
      pageNum: 3, pageSize: 20, jobDefinitionId: ' 18 ', keyword: 'customer',
    })).toEqual({ current: 3, pageSize: 20, jobDefinitionId: 18, keyword: 'customer' });
    expect(normalizeOfflineInstancePageRequest({ pageNo: 2 })).toEqual({
      current: 2, pageSize: 10,
    });
    expect(normalizeOfflineInstancePageRequest({ current: 5, pageNum: 2, pageSize: 8 }))
      .toEqual({ current: 5, pageSize: 8 });
  });

  it.each([
    [{ current: 0 }, '页码'], [{ pageNum: '-1' }, '页码'],
    [{ pageSize: 0 }, '每页条数'], [{ pageSize: 201 }, '每页条数'],
    [{ jobDefinitionId: '1.4' }, '任务定义 ID'],
    [{ jobDefinitionId: '9007199254740992' }, '任务定义 ID'],
  ])('rejects an invalid page or identifier: %p', (query, reason) => {
    expect(() => normalizeOfflineInstancePageRequest(query)).toThrow(reason);
  });

  it('reads task detail and list from the same canonical definition endpoints', async () => {
    const item = { id: 12, jobName: 'orders' };
    const page = { bizData: [item], pagination: { total: 1 } };
    const get = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(item);
    const post = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(page);
    await expect(getOfflineSyncTask(12)).resolves.toEqual(item);
    await expect(listOfflineSyncTasks({ current: 1, pageSize: 10 })).resolves.toEqual(page);
    expect(get).toHaveBeenCalledWith('/api/v1/job/batch-definition/12');
    expect(post).toHaveBeenCalledWith('/api/v1/job/batch-definition/page',
      { current: 1, pageSize: 10 });
  });

  it('keeps historical and modern execution-history page request normalization identical', async () => {
    const page = { bizData: [{ id: 99 }], pagination: { total: 1 } };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(page);
    const post = jest.spyOn(HttpUtils, 'post').mockResolvedValue({ code: 200, data: page });
    const query = { pageNum: 2, pageSize: 20, jobDefinitionId: '12' };
    await expect(listOfflineSyncInstances(query)).resolves.toEqual(page);
    await expect(linkupJobInstanceApi.page(query)).resolves.toEqual({ code: 200, data: page });
    const payload = { current: 2, pageSize: 20, jobDefinitionId: 12 };
    expect(postData).toHaveBeenCalledWith('/api/v1/job/batch-instance/page', payload);
    expect(post).toHaveBeenCalledWith('/api/v1/job/batch-instance/page', payload);
  });

  it('fetches instance detail and execution log without a response envelope', async () => {
    const detail = { id: 'b 7', status: 'SUCCESS' };
    const get = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce(detail).mockResolvedValueOnce('task started');
    await expect(getOfflineSyncInstanceDetail('b 7')).resolves.toEqual(detail);
    await expect(getOfflineSyncInstanceLog('b 7')).resolves.toBe('task started');
    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/job/batch-instance/b%207');
    expect(get).toHaveBeenNthCalledWith(2, '/api/v1/job/batch-instance/b%207/log');
  });

  it('queries table-level metrics with exact instance identity', async () => {
    const metrics = [{ id: 't1', readRowCount: 123 }];
    const get = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(metrics);
    await expect(listOfflineSyncTableMetrics(22)).resolves.toEqual(metrics);
    expect(get).toHaveBeenCalledWith('/api/v1/job/batch-instance/22/table-metrics');
  });

  it('preserves cron and client log query URL encoding', async () => {
    const get = jest.spyOn(HttpUtils, 'getData')
      .mockResolvedValueOnce(['2026-10-10 02:00:00'])
      .mockResolvedValueOnce(['line 1']);
    await expect(getOfflineSyncScheduleTimes('0 0 2 * * ?')).resolves.toHaveLength(1);
    await expect(getOfflineSyncClientLogs(9, 'BATCH & MANUAL')).resolves.toEqual(['line 1']);
    expect(get).toHaveBeenNthCalledWith(1,
      '/api/v1/job/schedule/last5-execution-times?cron=0%200%202%20*%20*%20%3F');
    expect(get).toHaveBeenNthCalledWith(2,
      '/api/v1/devops/client/instance/9/logs?jobMode=BATCH%20%26%20MANUAL');
  });

  it('preserves numeric batch operation bodies and partial failure counts', async () => {
    const summary = { successCount: 1, failedCount: 1, errors: [{ jobDefinitionId: 2 }] };
    const post = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(summary);
    await expect(batchStartOfflineSyncTasks(['1', '2'])).resolves.toEqual(summary);
    await expect(batchStopOfflineSyncTasks(['1', '2'])).resolves.toEqual(summary);
    expect(post).toHaveBeenNthCalledWith(1,
      '/api/v1/job/batch-execution/batch-execute', { jobDefinitionIds: [1, 2] });
    expect(post).toHaveBeenNthCalledWith(2,
      '/api/v1/job/batch-execution/batch-pause', { jobDefinitionIds: [1, 2] });
  });

  it('propagates Project/RBAC or business failures instead of manufacturing empty state', async () => {
    const get = jest.spyOn(HttpUtils, 'getData').mockRejectedValue(
      new Error('403 project denied'),
    );
    await expect(getOfflineSyncInstanceDetail(5)).rejects.toThrow('403 project denied');
    await expect(getOfflineSyncScheduleTimes('* * * * *')).rejects.toThrow('403 project denied');
    const post = jest.spyOn(HttpUtils, 'postData').mockRejectedValue(
      new Error('409 definition changed'),
    );
    await expect(listOfflineSyncInstances({ pageNum: 1 })).rejects.toThrow('409 definition changed');
    await expect(batchStartOfflineSyncTasks([5])).rejects.toThrow('409 definition changed');
    expect(get).toHaveBeenCalledTimes(2);
    expect(post).toHaveBeenCalledTimes(2);
  });
});
