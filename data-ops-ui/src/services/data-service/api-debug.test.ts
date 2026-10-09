import HttpUtils from '@/utils/HttpUtils';
import {
  getDataServiceDocumentation,
  listDataServices,
  testDataService,
} from './api';

describe('Data Service debug API data-only contracts', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('lists services at the original GET endpoint with unwrapped rows', async () => {
    const rows = [{ id: 11, name: 'Orders API' }];
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(rows);

    await expect(listDataServices()).resolves.toEqual(rows);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-service');
  });

  it('loads the chosen API documentation from its original GET endpoint', async () => {
    const doc = { apiId: 11, parameters: [{ name: 'orderId', example: '42' }] };
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(doc);

    await expect(getDataServiceDocumentation(11)).resolves.toEqual(doc);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-service/11/documentation');
  });

  it('POSTs test parameters to the original route and returns unwrapped query result', async () => {
    const payload = { orderId: '42' };
    const rows = { columns: ['id'], rows: [{ id: 42 }], rowCount: 1, durationMs: 23, truncated: false };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(rows);

    await expect(testDataService(11, payload)).resolves.toEqual(rows);
    expect(postData).toHaveBeenCalledWith('/api/v1/data-service/11/test', payload);
  });

  it('propagates backend and transport failures to the existing page error handlers', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockRejectedValue(new Error('文档不可用'));
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValue(new Error('无执行权限'));

    await expect(getDataServiceDocumentation(11)).rejects.toThrow('文档不可用');
    await expect(testDataService(11, {})).rejects.toThrow('无执行权限');
    expect(getData).toHaveBeenCalledWith('/api/v1/data-service/11/documentation');
    expect(postData).toHaveBeenCalledWith('/api/v1/data-service/11/test', {});
  });
});
