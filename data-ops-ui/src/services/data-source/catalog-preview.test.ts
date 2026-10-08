import HttpUtils from '@/utils/HttpUtils';
import { previewDataSourceTop20 } from './catalog';

describe('Data Source Top20 catalog preview POST contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('preserves the table-mode request body and unwraps the preview payload', async () => {
    const rows = { columns: [{ title: 'ID', dataIndex: 'id' }], data: [{ id: 42 }], total: 1 };
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(rows);
    const requestBody = {
      readMode: 'table',
      read_mode: 'table',
      table_path: 'warehouse.orders',
    };

    await expect(previewDataSourceTop20(12, requestBody)).resolves.toEqual(rows);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/getTop20Data/12',
      requestBody,
    );
  });

  it('passes SQL text and paramsList without collapsing them to GET/column discovery', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue({
      columns: [], data: [], total: 0,
    });
    const requestBody = {
      readMode: 'sql',
      read_mode: 'sql',
      query: 'SELECT id FROM orders WHERE state = :state',
      paramsList: [{ paramName: 'state', paramValue: 'OPEN' }],
    };

    await previewDataSourceTop20('27', requestBody);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/getTop20Data/27',
      requestBody,
    );
  });

  it('propagates HTTP/business errors for the modal error display', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValue(new Error('数据源不可用'));

    await expect(previewDataSourceTop20('12', { table_path: 'orders' }))
      .rejects.toThrow('数据源不可用');
    expect(postData).toHaveBeenCalledTimes(1);
  });
});
