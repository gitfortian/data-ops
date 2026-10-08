import { act, renderHook } from '@testing-library/react';
import { searchDataSourceTables } from '@/services/data-source/catalog';
import useDataSourceTables, { normalizeTableNames } from './useDataSourceTables';

jest.mock('@/services/data-source/catalog', () => ({
  searchDataSourceTables: jest.fn(),
}));

describe('normalizeTableNames', () => {
  const relations = [
    { name: 'orders', type: 'TABLE' },
    { name: 'v_ods_project', type: 'VIEW' },
  ];

  it('keeps tables and views for source selection', () => {
    expect(normalizeTableNames(relations)).toEqual([
      'orders',
      'v_ods_project',
    ]);
  });

  it('filters views for sink selection', () => {
    expect(normalizeTableNames(relations, false)).toEqual([
      'orders',
    ]);
  });

  it('keeps legacy string catalog entries', () => {
    expect(
      normalizeTableNames(['orders', 'legacy_relation'], false),
    ).toEqual(['orders', 'legacy_relation']);
  });
});

describe('useDataSourceTables modern catalog migration', () => {
  const search = jest.mocked(searchDataSourceTables);

  beforeEach(() => {
    jest.useFakeTimers();
    search.mockReset();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('preserves database and bounded search while reading unwrapped table rows', async () => {
    search.mockResolvedValue([
      { name: 'orders', type: 'TABLE' },
      { name: 'v_orders', type: 'VIEW' },
    ]);

    const { result, unmount } = renderHook(() =>
      useDataSourceTables('12', ' warehouse ', { includeViews: false }),
    );

    await act(async () => {
      jest.advanceTimersByTime(250);
      await Promise.resolve();
    });

    expect(search).toHaveBeenCalledWith('12', undefined, {
      limit: 100,
      database: 'warehouse',
    });
    expect(result.current.tables).toEqual(['orders']);
    expect(result.current.loading).toBe(false);
    unmount();
  });

  it('keeps source views and trims the keyword after debounce', async () => {
    search.mockResolvedValue([
      { name: 'orders', type: 'TABLE' },
      { name: 'v_orders', type: 'VIEW' },
    ]);

    const { result, unmount } = renderHook(() => useDataSourceTables('12'));
    act(() => {
      result.current.search('  order  ');
    });
    await act(async () => {
      jest.advanceTimersByTime(250);
      await Promise.resolve();
    });

    expect(search).toHaveBeenCalledTimes(1);
    expect(search).toHaveBeenCalledWith('12', 'order', {
      limit: 100,
      database: undefined,
    });
    expect(result.current.tables).toEqual(['orders', 'v_orders']);
    unmount();
  });

  it('continues clearing results when catalog search fails', async () => {
    search.mockRejectedValue(new Error('catalog unavailable'));

    const { result, unmount } = renderHook(() => useDataSourceTables('12'));
    await act(async () => {
      jest.advanceTimersByTime(250);
      await Promise.resolve();
    });

    expect(result.current.tables).toEqual([]);
    expect(result.current.loading).toBe(false);
    unmount();
  });
});
