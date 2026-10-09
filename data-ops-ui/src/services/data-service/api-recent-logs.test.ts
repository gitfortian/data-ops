import HttpUtils from '@/utils/HttpUtils';
import { listRecentDataServiceLogs } from './api';

describe('Data Service recent logs modern data-only contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('retains the historical GET /logs/recent route and returns unwrapped logs', async () => {
    const rows = [{
      id: 9,
      apiId: 4,
      serviceName: 'orders',
      servicePath: '/api/orders',
      callerType: 'CONSOLE',
      success: true,
      durationMs: 11,
      rowCount: 2,
    }];
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(rows);

    await expect(listRecentDataServiceLogs()).resolves.toEqual(rows);
    expect(getData).toHaveBeenCalledTimes(1);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-service/logs/recent');
  });

  it('preserves an empty response', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue([]);
    await expect(listRecentDataServiceLogs()).resolves.toEqual([]);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-service/logs/recent');
  });

  it('surfaces transport and business failures to the page catch', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockRejectedValue(new Error('recent call log unavailable'));

    await expect(listRecentDataServiceLogs()).rejects.toThrow('recent call log unavailable');
    expect(getData).toHaveBeenCalledTimes(1);
  });
});
