import HttpUtils from '@/utils/HttpUtils';
import {
  listDataSourceTableOptions,
  listDataSourceReferenceTableOptions,
} from './catalog';

describe('Data Source multi workflow catalog list contracts', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('preserves GET /catalog/list/{id} and the label/value option rows', async () => {
    const rows = [
      { label: 'Orders', value: 'warehouse.orders' },
      { label: 'Customers', value: 'warehouse.customers' },
    ];
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(rows);

    await expect(listDataSourceTableOptions('12')).resolves.toEqual(rows);
    expect(getData).toHaveBeenCalledTimes(1);
    expect(getData).toHaveBeenCalledWith('/api/v1/data-source/catalog/list/12');
  });

  it('retains reference-table matchMode and keyword query names/order', async () => {
    const rows = [{ label: 'Orders', value: 'sales.orders' }];
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue(rows);

    await expect(listDataSourceReferenceTableOptions(12, '2', 'ord%')).resolves.toEqual(rows);
    expect(getData).toHaveBeenCalledWith(
      '/api/v1/data-source/catalog/listByMatchMode/12?matchMode=2&keyword=ord%25',
    );
  });

  it('omits missing filters but preserves numeric match modes and zero', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue([]);

    await listDataSourceReferenceTableOptions('42');
    await listDataSourceReferenceTableOptions('42', 0);
    expect(getData).toHaveBeenNthCalledWith(
      1, '/api/v1/data-source/catalog/listByMatchMode/42',
    );
    expect(getData).toHaveBeenNthCalledWith(
      2, '/api/v1/data-source/catalog/listByMatchMode/42?matchMode=0',
    );
  });

  it('propagates business and transport failures to the hook error path', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData')
      .mockRejectedValue(new Error('catalog unavailable'));
    await expect(listDataSourceTableOptions('12')).rejects.toThrow('catalog unavailable');
    await expect(listDataSourceReferenceTableOptions('12', '3', 'sale'))
      .rejects.toThrow('catalog unavailable');
    expect(getData).toHaveBeenCalledTimes(2);
  });

  it('preserves valid empty lists without changing them to envelope-like objects', async () => {
    const getData = jest.spyOn(HttpUtils, 'getData').mockResolvedValue([]);
    await expect(listDataSourceTableOptions('12')).resolves.toEqual([]);
    await expect(listDataSourceReferenceTableOptions('12', '2')).resolves.toEqual([]);
    expect(getData).toHaveBeenCalledTimes(2);
  });
});
