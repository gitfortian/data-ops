import { act, renderHook, waitFor } from '@testing-library/react';
import { queryDataSourceColumnOptions } from '@/services/data-source/catalog';
import type { DataSourceCatalogColumnOptionRow } from '@/services/data-source/catalog';
import useDataSourceColumns from './useDataSourceColumns';

jest.mock('@/services/data-source/catalog', () => ({
  queryDataSourceColumnOptions: jest.fn(),
}));

describe('useDataSourceColumns modern catalog migration', () => {
  const queryColumns = jest.mocked(queryDataSourceColumnOptions);

  beforeEach(() => {
    queryColumns.mockReset();
  });

  it('preserves table_path and field labels, types, remarks and primary key markers', async () => {
    queryColumns.mockResolvedValue([
      { fieldName: 'id', fieldType: 'BIGINT', fieldComment: '主键', fieldKey: 'PRI' },
      { fieldName: 'created_at', fieldType: 'TIMESTAMP' },
    ]);
    const { result } = renderHook(() => useDataSourceColumns('21', {
      table_path: 'warehouse.orders',
    }));

    await waitFor(() => expect(result.current.columns).toHaveLength(2));
    expect(queryColumns).toHaveBeenCalledWith('21', {
      table_path: 'warehouse.orders',
    });
    expect(result.current.columns).toEqual([
      {
        value: 'id',
        label: 'id',
        description: 'BIGINT · 主键',
        primaryKey: true,
        typeName: 'BIGINT',
        jdbcType: undefined,
      },
      {
        value: 'created_at',
        label: 'created_at',
        description: 'TIMESTAMP',
        primaryKey: false,
        typeName: 'TIMESTAMP',
        jdbcType: undefined,
      },
    ]);
    expect(result.current.loading).toBe(false);
  });

  it('keeps SQL column discovery for sources configured in SQL read mode', async () => {
    queryColumns.mockResolvedValue([{ fieldName: 'order_id', fieldType: 'VARCHAR' }]);
    const payload = { query: 'SELECT order_id FROM orders' };
    const { result } = renderHook(() => useDataSourceColumns('21', payload));

    await waitFor(() => expect(result.current.columns).toHaveLength(1));
    expect(queryColumns).toHaveBeenCalledWith('21', payload);
    expect(result.current.columns[0].value).toBe('order_id');
  });

  it('avoids requests for missing source ID or empty selection', () => {
    const { result, rerender } = renderHook(
      ({ id, request }: { id: string; request?: Record<string, unknown> }) =>
        useDataSourceColumns(id, request),
      { initialProps: { id: '', request: { table_path: 'orders' } } },
    );

    expect(result.current.columns).toEqual([]);
    rerender({ id: '21', request: {} });
    expect(queryColumns).not.toHaveBeenCalled();
    expect(result.current.loading).toBe(false);
  });

  it('keeps clearing columns and resetting loading when a request fails', async () => {
    queryColumns.mockRejectedValue(new Error('catalog unavailable'));
    const { result } = renderHook(() =>
      useDataSourceColumns('21', { table_path: 'orders' }),
    );

    await waitFor(() => expect(queryColumns).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.columns).toEqual([]);
  });

  it('ignores stale responses after the selected table changes', async () => {
    let finishPrevious!: (rows: DataSourceCatalogColumnOptionRow[]) => void;
    const previous = new Promise<DataSourceCatalogColumnOptionRow[]>((resolve) => {
      finishPrevious = resolve;
    });
    queryColumns.mockReturnValueOnce(previous)
      .mockResolvedValueOnce([{ fieldName: 'new_table_id' }]);

    const { result, rerender } = renderHook(
      ({ table }: { table: string }) =>
        useDataSourceColumns('21', { table_path: table }),
      { initialProps: { table: 'old_table' } },
    );
    rerender({ table: 'new_table' });

    await waitFor(() =>
      expect(result.current.columns.map((row) => row.value))
        .toEqual(['new_table_id']),
    );
    await act(async () => {
      finishPrevious([{ fieldName: 'old_table_id' }]);
      await previous;
    });
    expect(result.current.columns.map((row) => row.value))
      .toEqual(['new_table_id']);
    expect(queryColumns).toHaveBeenNthCalledWith(1, '21', { table_path: 'old_table' });
    expect(queryColumns).toHaveBeenNthCalledWith(2, '21', { table_path: 'new_table' });
  });

  it('keeps accepting legacy catalog row wrappers when supplied', async () => {
    queryColumns.mockResolvedValue({
      records: [{ fieldName: 'legacy_field', fieldType: 'TEXT' }],
    } as unknown as DataSourceCatalogColumnOptionRow[]);
    const { result } = renderHook(() =>
      useDataSourceColumns('21', { table_path: 'orders' }),
    );

    await waitFor(() => expect(result.current.columns).toHaveLength(1));
    expect(result.current.columns[0].description).toBe('TEXT');
  });
});
