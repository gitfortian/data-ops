import HttpUtils from '@/utils/HttpUtils';
import { queryDataSourceColumnOptions } from './catalog';

describe('Data Source catalog table-or-SQL column query contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('keeps table_path in the original POST request body and unwraps the rows', async () => {
    const rows = [{ fieldName: 'id', fieldType: 'BIGINT', fieldKey: 'PRI' }];
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue(rows);

    await expect(queryDataSourceColumnOptions('21', { table_path: 'warehouse.orders' }))
      .resolves.toEqual(rows);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/column/21',
      { table_path: 'warehouse.orders' },
    );
  });

  it('preserves SQL query and paramsList rather than converting them to GET columns', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData').mockResolvedValue([]);
    const payload = {
      query: 'SELECT id FROM orders WHERE status = :status',
      paramsList: [{ paramName: 'status', paramValue: 'OPEN' }],
    };

    await queryDataSourceColumnOptions(21, payload);
    expect(postData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/column/21',
      payload,
    );
  });

  it('propagates HTTP and business errors to the existing hook fallback', async () => {
    const postData = jest.spyOn(HttpUtils, 'postData')
      .mockRejectedValue(new Error('catalog unavailable'));

    await expect(queryDataSourceColumnOptions(21, { table_path: 'orders' }))
      .rejects.toThrow('catalog unavailable');
    expect(postData).toHaveBeenCalledTimes(1);
  });
});
