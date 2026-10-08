import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { message } from 'antd';
import { queryDataSourceColumnOptions } from '@/services/data-source/catalog';
import TableColumnsPopover from './TableColumnsPopover';

jest.mock('@/services/data-source/catalog', () => ({
  queryDataSourceColumnOptions: jest.fn(),
}));

// Preserve the component's click and data flow while keeping portal/animation
// internals out of a catalog contract regression test.
jest.mock('antd', () => {
  const React = require('react') as typeof import('react');
  return {
    message: { warning: jest.fn(), error: jest.fn() },
    Popover: ({
      children, content, placement,
    }: {
      children: React.ReactNode;
      content: React.ReactNode;
      placement: string;
    }) => React.createElement('section', {
      'data-testid': 'columns-popover',
      'data-placement': placement,
    }, children, content),
    Table: ({
      dataSource, loading,
    }: {
      dataSource: Array<{
        fieldName?: string;
        fieldType?: string;
        fieldComment?: string;
        fieldKey?: string;
      }>;
      loading: boolean;
    }) => React.createElement('output', {
      'data-testid': 'column-rows',
      'data-loading': String(loading),
    }, dataSource.map((row, index) => React.createElement(
      'span',
      { key: row.fieldName ?? index },
      [row.fieldName, row.fieldType, row.fieldComment, row.fieldKey]
        .filter(Boolean).join(' / '),
    ))),
  };
});

describe('TableColumnsPopover Data Source catalog migration', () => {
  const query = jest.mocked(queryDataSourceColumnOptions);

  beforeEach(() => {
    query.mockReset();
    jest.mocked(message.warning).mockClear();
    jest.mocked(message.error).mockClear();
  });

  it('uses the existing SINGLE_TABLE POST body and renders unwrapped columns', async () => {
    query.mockResolvedValue([
      { fieldName: 'id', fieldType: 'BIGINT', fieldComment: '主键', fieldKey: 'PRI' },
    ]);

    render(
      <TableColumnsPopover sourceId="12" table="sales.orders" type="source">
        <button type="button">Open columns</button>
      </TableColumnsPopover>,
    );
    expect(screen.getByTestId('columns-popover')).toHaveAttribute(
      'data-placement', 'bottomLeft',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open columns' }));
    expect(query).toHaveBeenCalledWith('12', {
      taskExecuteType: 'SINGLE_TABLE',
      table_path: 'sales.orders',
      query: '',
      read_mode: 'table',
    });
    await waitFor(() => expect(screen.getByText('id / BIGINT / 主键 / PRI')).toBeInTheDocument());
    expect(screen.getByTestId('column-rows')).toHaveAttribute('data-loading', 'false');
    expect(message.error).not.toHaveBeenCalled();
  });

  it('keeps sink placement, reloads new table, and clears rows for an empty success', async () => {
    query.mockResolvedValueOnce([{ fieldName: 'old_field' }])
      .mockResolvedValueOnce([]);

    const { rerender } = render(
      <TableColumnsPopover sourceId={23} table="old_table" type="sink">
        <button type="button">Show fields</button>
      </TableColumnsPopover>,
    );
    expect(screen.getByTestId('columns-popover')).toHaveAttribute(
      'data-placement', 'bottomRight',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Show fields' }));
    await waitFor(() => expect(screen.getByText('old_field')).toBeInTheDocument());

    rerender(
      <TableColumnsPopover sourceId={23} table="new_table" type="sink">
        <button type="button">Show fields</button>
      </TableColumnsPopover>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Show fields' }));
    await waitFor(() => expect(screen.queryByText('old_field')).not.toBeInTheDocument());
    expect(query).toHaveBeenNthCalledWith(2, 23, {
      taskExecuteType: 'SINGLE_TABLE',
      table_path: 'new_table',
      query: '',
      read_mode: 'table',
    });
  });

  it('warns for a missing datasource ID without calling catalog', () => {
    render(
      <TableColumnsPopover table="orders" type="source">
        <button type="button">Open columns</button>
      </TableColumnsPopover>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open columns' }));
    expect(message.warning).toHaveBeenCalledWith('DatasourceId is missing');
    expect(query).not.toHaveBeenCalled();
    expect(screen.getByTestId('column-rows')).toHaveAttribute('data-loading', 'false');
  });

  it('shows the existing failure toast and stops loading on rejected catalog reads', async () => {
    query.mockRejectedValue(new Error('catalog unavailable'));
    render(
      <TableColumnsPopover sourceId="12" table="orders" type="source">
        <button type="button">Open columns</button>
      </TableColumnsPopover>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open columns' }));
    await waitFor(() => expect(message.error).toHaveBeenCalledWith('Load columns failed'));
    expect(screen.getByTestId('column-rows')).toHaveAttribute('data-loading', 'false');
  });

  it('shows a spinner while the catalog request is pending', async () => {
    let resolve!: (rows: Array<{ fieldName: string }>) => void;
    query.mockReturnValue(new Promise((finish) => { resolve = finish; }));
    render(
      <TableColumnsPopover sourceId="12" table="orders" type="source">
        <button type="button">Open columns</button>
      </TableColumnsPopover>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open columns' }));
    expect(screen.getByTestId('column-rows')).toHaveAttribute('data-loading', 'true');
    resolve([{ fieldName: 'done_field' }]);
    await waitFor(() => expect(screen.getByText('done_field')).toBeInTheDocument());
    expect(screen.getByTestId('column-rows')).toHaveAttribute('data-loading', 'false');
  });
});
