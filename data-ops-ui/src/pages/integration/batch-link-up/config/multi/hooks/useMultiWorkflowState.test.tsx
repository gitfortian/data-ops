import { act, renderHook, waitFor } from '@testing-library/react';
import type { FormInstance } from 'antd';
import { message } from 'antd';
import { listDataSourceOptions } from '@/services/data-source';
import {
  listDataSourceTableOptions,
  listDataSourceReferenceTableOptions,
} from '@/services/data-source/catalog';
import { useMultiWorkflowState } from './useMultiWorkflowState';

jest.mock('antd', () => ({
  message: { error: jest.fn(), warning: jest.fn(), success: jest.fn() },
}));

jest.mock('@/services/data-source', () => ({
  listDataSourceOptions: jest.fn(),
}));

jest.mock('@/services/data-source/catalog', () => ({
  listDataSourceTableOptions: jest.fn(),
  listDataSourceReferenceTableOptions: jest.fn(),
}));

jest.mock('@/services/batch-link-up', () => ({
  saveOfflineSyncMultiGuideWithState: jest.fn(),
  renderOfflineSyncMultiGuideConfig: jest.fn(),
}));

describe('useMultiWorkflowState modern Data Source migration', () => {
  const options = jest.mocked(listDataSourceOptions);
  const tables = jest.mocked(listDataSourceTableOptions);
  const references = jest.mocked(listDataSourceReferenceTableOptions);
  const errorToast = jest.mocked(message.error);

  const form = {
    getFieldsValue: jest.fn(() => ({ sourceId: 12, sinkId: 20 })),
    setFieldsValue: jest.fn(),
    setFieldValue: jest.fn(),
    getFieldValue: jest.fn(),
    validateFields: jest.fn(),
  } as unknown as FormInstance;

  const props = {
    form,
    params: {
      workflow: {
        source: { datasourceId: 12, dbType: 'MYSQL' },
        target: { datasourceId: 20, dbType: 'POSTGRESQL' },
        tableMatch: { mode: '1', tables: ['orders'] },
      },
    },
    setParams: jest.fn(),
    basicConfig: {},
    scheduleConfig: {},
    envConfig: {},
  };

  beforeEach(() => {
    options.mockReset();
    tables.mockReset();
    references.mockReset();
    errorToast.mockClear();
    options.mockImplementation(async (dbType) => [
      { id: dbType, label: dbType, value: dbType },
    ]);
    tables.mockResolvedValue([
      { label: 'Orders', value: 'orders' },
      { label: 'Customers', value: 'customers' },
    ]);
    references.mockResolvedValue([
      { label: 'Matched', value: 'matched_orders' },
    ]);
  });

  it('initializes source/target options and restores manually selected table IDs', async () => {
    const { result } = renderHook(() => useMultiWorkflowState(props));

    await waitFor(() => expect(result.current.tableData).toHaveLength(2));
    await waitFor(() => expect(result.current.multiTableList).toEqual(['orders']));
    expect(options).toHaveBeenCalledWith('MYSQL');
    expect(options).toHaveBeenCalledWith('POSTGRESQL');
    expect(result.current.sourceOption).toEqual([
      { id: 'MYSQL', label: 'MYSQL', value: 'MYSQL' },
    ]);
    expect(result.current.targetOption).toEqual([
      { id: 'POSTGRESQL', label: 'POSTGRESQL', value: 'POSTGRESQL' },
    ]);
    expect(tables).toHaveBeenCalledWith('12');
    expect(result.current.tableData.map(item => item.key)).toEqual(['orders', 'customers']);
    expect(errorToast).not.toHaveBeenCalled();
  });

  it('keeps the all-tables mode auto selection and hand-selected mode clearing', async () => {
    const params = {
      ...props.params,
      workflow: {
        ...props.params.workflow,
        tableMatch: { mode: '4', tables: [] },
      },
    };
    const { result } = renderHook(() => useMultiWorkflowState({ ...props, params }));
    await waitFor(() =>
      expect(result.current.multiTableList).toEqual(['orders', 'customers']),
    );

    await act(async () => {
      await result.current.handleMatchModeChange('1');
    });
    expect(result.current.multiTableList).toEqual([]);
    expect(tables).toHaveBeenLastCalledWith('12');
    expect(result.current.matchMode).toBe('1');
  });

  it('keeps reference-list keyword, mode and label/value normalization', async () => {
    const params = {
      ...props.params,
      workflow: {
        ...props.params.workflow,
        tableMatch: { mode: '2', keyword: 'ord', tables: [] },
      },
    };
    const { result } = renderHook(() => useMultiWorkflowState({ ...props, params }));
    await waitFor(() => expect(result.current.readOnlyTables).toHaveLength(1));
    expect(references).toHaveBeenCalledWith('12', '2', 'ord');
    expect(result.current.readOnlyTables[0]).toMatchObject({
      key: 'matched_orders',
      title: 'Matched',
      rawTitle: 'matched_orders',
    });

    await act(async () => {
      await result.current.handleMatchModeChange('3');
    });
    expect(references).toHaveBeenLastCalledWith('12', '3', 'ord');
  });

  it('maps unsuccessful source option requests to empty lists without aborting init', async () => {
    options.mockRejectedValueOnce(new Error('option unavailable'))
      .mockResolvedValueOnce([{ id: 20, label: 'Target', value: 20 }]);
    const { result } = renderHook(() => useMultiWorkflowState(props));

    await waitFor(() => expect(result.current.tableData).toHaveLength(2));
    expect(result.current.sourceOption).toEqual([]);
    expect(result.current.targetOption).toEqual([{ id: 20, label: 'Target', value: 20 }]);
    expect(errorToast).not.toHaveBeenCalled();
  });

  it('retains table-load failure toast and resets loading without selecting tables', async () => {
    tables.mockRejectedValue(new Error('catalog unavailable'));
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    try {
      const { result } = renderHook(() => useMultiWorkflowState(props));
      await waitFor(() => expect(errorToast).toHaveBeenCalledWith('获取表列表失败'));
      await waitFor(() => expect(result.current.loading).toBe(false));
      expect(result.current.tableData).toEqual([]);
      expect(tables).toHaveBeenCalledWith('12');
    } finally {
      consoleError.mockRestore();
    }
  });

  it('retains reference-table failure toast without stale table data', async () => {
    references.mockRejectedValue(new Error('reference unavailable'));
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    try {
      const params = {
        ...props.params,
        workflow: {
          ...props.params.workflow,
          tableMatch: { mode: '3', keyword: 'sale', tables: [] },
        },
      };
      const { result } = renderHook(() => useMultiWorkflowState({ ...props, params }));
      await waitFor(() => expect(errorToast).toHaveBeenCalledWith('获取参考表失败'));
      await waitFor(() => expect(result.current.loading).toBe(false));
      expect(result.current.readOnlyTables).toEqual([]);
      expect(references).toHaveBeenCalledWith('12', '3', 'sale');
    } finally {
      consoleError.mockRestore();
    }
  });
});
