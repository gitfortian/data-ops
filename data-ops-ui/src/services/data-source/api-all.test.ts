import HttpUtils from '@/utils/HttpUtils';
import { listAllDataSources } from './api';

describe('Data Source all-list modern API / single sync editor contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('uses the existing GET /all route and unwraps bizData for sync editor options', async () => {
    const data = {
      bizData: [
        { id: 12, name: 'warehouse', dbType: 'MYSQL' },
        { id: '28', name: 'target', dbType: 'POSTGRESQL' },
      ],
      pagination: { pageNo: 1, pageSize: 2, total: 2 },
    };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(data);

    await expect(listAllDataSources()).resolves.toEqual(data);
    expect(getData).toHaveBeenCalledTimes(1);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-source/all');
    expect((await listAllDataSources()).bizData).toEqual(data.bizData);
  });

  it('preserves a successful empty data source list', async () => {
    const data = {
      bizData: [],
      pagination: { pageNo: 1, pageSize: 0, total: 0 },
    };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(data);

    await expect(listAllDataSources()).resolves.toEqual(data);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-source/all');
  });

  it('rejects backend business and transport errors so page catch clears list and shows message', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockRejectedValue(
      new Error('数据源请求失败'),
    );

    await expect(listAllDataSources()).rejects.toThrow('数据源请求失败');
    expect(getData).toHaveBeenCalledTimes(1);
  });
});
