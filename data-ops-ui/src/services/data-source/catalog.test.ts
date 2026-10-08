import HttpUtils from '@/utils/HttpUtils';
import { searchDataSourceTables } from './catalog';

describe('Data Source catalog table search contract', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('preserves backend database, schema, keyword and limit on the data-only API', async () => {
    const rows = [{ name: 'orders', type: 'TABLE' }];
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(rows);

    const result = await searchDataSourceTables('12', 'ord', {
      database: 'warehouse',
      schema: 'public',
      limit: 100,
    });

    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/12/tables/search?database=warehouse&schema=public&keyword=ord&limit=100',
    );
    expect(result).toEqual(rows);
  });

  it('keeps the previous keyword-only call shape and does not send empty options', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue([]);

    await searchDataSourceTables(12, 'sales');
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/12/tables/search?keyword=sales',
    );
  });

  it('rejects failed reads instead of treating an error envelope as table data', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockRejectedValue(new Error('catalog unavailable'));

    await expect(searchDataSourceTables(12)).rejects.toThrow('catalog unavailable');
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/12/tables/search',
    );
  });
});
