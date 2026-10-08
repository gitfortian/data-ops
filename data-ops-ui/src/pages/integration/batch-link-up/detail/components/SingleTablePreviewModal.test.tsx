import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { previewDataSourceTop20 } from '@/services/data-source/catalog';
import type { DataSourcePreviewResult } from '@/services/data-source/catalog';
import SingleTablePreviewModal from './SingleTablePreviewModal';

jest.mock('@/services/data-source/catalog', () => ({
  previewDataSourceTop20: jest.fn(),
}));

jest.mock('@ant-design/icons', () => ({
  ReloadOutlined: () => null,
}));

// Isolate the React preview state and rendered values from Ant Design portal/animation behavior.
jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  const Empty = Object.assign(
    () => element('div', null, 'No rows'),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  return {
    Modal: ({
      open, children, footer,
    }: {
      open: boolean;
      children: ReactNode;
      footer: ReactNode;
    }) => open ? element('section', { 'data-testid': 'preview-modal' }, children, footer) : null,
    Button: ({
      children, onClick, loading,
    }: {
      children: ReactNode;
      onClick?: () => void;
      loading?: boolean;
    }) => element('button', {
      type: 'button',
      onClick,
      'data-loading': String(Boolean(loading)),
    }, children),
    Tag: ({ children }: { children: ReactNode }) => element('span', null, children),
    Alert: ({ description }: { description: ReactNode }) =>
      element('div', { role: 'alert' }, description),
    Empty,
    Table: ({
      dataSource, columns, loading,
    }: {
      dataSource: Array<Record<string, unknown>>;
      columns: Array<{
        key?: string;
        title?: ReactNode;
        render?: (value: unknown, row: Record<string, unknown>) => ReactNode;
      }>;
      loading: boolean;
    }) => element('div', {
      'data-testid': 'preview-table',
      'data-loading': String(loading),
    }, dataSource.map((row, index) => element('div', {
      key: String(row.__yakPreviewRowKey ?? index),
    }, columns.map((col, colIndex) => element(
      'span',
      { key: col.key ?? colIndex },
      col.render?.(undefined, row),
    )))),
  };
});

describe('SingleTablePreviewModal Data Source catalog migration', () => {
  const requestPreview = jest.mocked(previewDataSourceTop20);
  const cancel = jest.fn();

  beforeEach(() => {
    requestPreview.mockReset();
    cancel.mockClear();
  });

  it('loads table-mode Top20 rows with identical request and configured columns', async () => {
    requestPreview.mockResolvedValue({
      columns: [{ title: 'Identifier', dataIndex: 'id' }],
      data: [{ id: 7 }, { id: null }],
      total: 2,
    });

    render(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ readMode: 'table', table: ' warehouse.orders ' }}
        onCancel={cancel}
      />,
    );
    expect(requestPreview).toHaveBeenCalledWith('12', {
      readMode: 'table',
      read_mode: 'table',
      table_path: 'warehouse.orders',
    });
    await waitFor(() => expect(screen.getByText('7')).toBeInTheDocument());
    expect(screen.getByText('NULL')).toBeInTheDocument();
    expect(screen.getByTestId('preview-table')).toHaveAttribute('data-loading', 'false');
    expect(screen.getByText('2 条')).toBeInTheDocument();
  });

  it('keeps SQL preview mode, trimmed SQL and parameterized request body', async () => {
    requestPreview.mockResolvedValue({ data: [{ name: 'A' }], total: 1 });
    const paramsList = [{ paramName: 'id', paramValue: 1 }];
    render(
      <SingleTablePreviewModal
        open
        dataSourceId={13}
        sourceConfig={{
          readMode: 'sql',
          sql: ' SELECT name FROM t WHERE id = :id ',
          paramsList,
        }}
        onCancel={cancel}
      />,
    );
    expect(requestPreview).toHaveBeenCalledWith(13, {
      readMode: 'sql',
      read_mode: 'sql',
      query: 'SELECT name FROM t WHERE id = :id',
      paramsList,
    });
    await waitFor(() => expect(screen.getByText('A')).toBeInTheDocument());
    expect(screen.getByText('SQL 查询')).toBeInTheDocument();
  });

  it('rejects missing source ID or selected table before any HTTP call', () => {
    const { rerender } = render(
      <SingleTablePreviewModal
        open
        sourceConfig={{ readMode: 'table', table: 'orders' }}
        onCancel={cancel}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('请先选择来源数据源');
    rerender(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ readMode: 'table', table: '' }}
        onCancel={cancel}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('请先选择来源表');
    rerender(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ readMode: 'sql', sql: '   ' }}
        onCancel={cancel}
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('请先填写查询 SQL');
    expect(requestPreview).not.toHaveBeenCalled();
  });

  it('normalizes malformed preview arrays and renders fallback columns for data-only results', async () => {
    requestPreview.mockResolvedValue({ data: [{ id: { nested: true } }], total: 'bad' } as unknown as DataSourcePreviewResult);
    render(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ table: 'orders' }}
        onCancel={cancel}
      />,
    );
    await waitFor(() => expect(screen.getByText('{"nested":true}')).toBeInTheDocument());
    expect(screen.getByText('1 条')).toBeInTheDocument();
  });

  it('retains error feedback and loading reset on rejected preview requests', async () => {
    requestPreview.mockRejectedValue(new Error('Preview read failed'));
    render(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ table: 'orders' }}
        onCancel={cancel}
      />,
    );
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Preview read failed'));
    expect(screen.getByTestId('preview-table')).toHaveAttribute('data-loading', 'false');
  });

  it('refreshes from the same source and ignores responses from an older source', async () => {
    let finishPrevious!: (value: DataSourcePreviewResult) => void;
    const previous = new Promise<DataSourcePreviewResult>((resolve) => {
      finishPrevious = resolve;
    });
    requestPreview.mockReturnValueOnce(previous)
      .mockResolvedValueOnce({ data: [{ id: 'new' }], total: 1 })
      .mockResolvedValueOnce({ data: [{ id: 'refresh' }], total: 1 });
    const { rerender } = render(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ table: 'older' }}
        onCancel={cancel}
      />,
    );
    rerender(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ table: 'newer' }}
        onCancel={cancel}
      />,
    );
    await waitFor(() => expect(screen.getByText('new')).toBeInTheDocument());
    await act(async () => {
      finishPrevious({ data: [{ id: 'stale' }], total: 1 });
      await previous;
    });
    expect(screen.queryByText('stale')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(screen.getByText('refresh')).toBeInTheDocument());
    expect(requestPreview).toHaveBeenNthCalledWith(1, '12', {
      readMode: 'table', read_mode: 'table', table_path: 'older',
    });
    expect(requestPreview).toHaveBeenNthCalledWith(2, '12', {
      readMode: 'table', read_mode: 'table', table_path: 'newer',
    });
    expect(requestPreview).toHaveBeenCalledTimes(3);
  });

  it('ignores a pending response after the preview modal closes', async () => {
    let finish!: (value: DataSourcePreviewResult) => void;
    const pending = new Promise<DataSourcePreviewResult>((resolve) => {
      finish = resolve;
    });
    requestPreview.mockReturnValue(pending);
    const { rerender } = render(
      <SingleTablePreviewModal
        open
        dataSourceId="12"
        sourceConfig={{ table: 'orders' }}
        onCancel={cancel}
      />,
    );
    expect(screen.getByTestId('preview-table')).toHaveAttribute('data-loading', 'true');
    rerender(
      <SingleTablePreviewModal
        open={false}
        dataSourceId="12"
        sourceConfig={{ table: 'orders' }}
        onCancel={cancel}
      />,
    );
    await act(async () => {
      finish({ data: [{ id: 'closed' }], total: 1 });
      await pending;
    });
    expect(screen.queryByText('closed')).not.toBeInTheDocument();
    expect(screen.queryByTestId('preview-modal')).not.toBeInTheDocument();
  });
});
