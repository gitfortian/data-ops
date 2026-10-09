import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { listDevelopmentTaskExecutions, type DevelopmentTaskExecutionPage,
  type DevelopmentTaskExecutionSummary } from '@/services/data-development';
import { WorkspaceLoadFailureState } from '../components/WorkspaceStateFeedback';
import ExecutionHistoryPage from './index';

jest.mock('@/services/data-development', () => ({
  listDevelopmentTaskExecutions: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
  useIntl: () => ({
    locale: 'zh-CN',
    formatMessage: ({ id }: { id: string }) => id,
  }),
  history: { push: jest.fn() },
}));

jest.mock('@/components/YakButton', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    __esModule: true,
    default: ({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) =>
      ReactRuntime.createElement('button', { onClick }, children),
  };
});

jest.mock('@/components/ReadableTable', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    __esModule: true,
    default: ({ dataSource, loading }: {
      dataSource: DevelopmentTaskExecutionSummary[];
      loading: boolean;
    }) => ReactRuntime.createElement('div', {
      'data-testid': 'execution-records',
      'data-loading': String(loading),
    }, dataSource.map(row => ReactRuntime.createElement('span', {
      key: row.id,
    }, row.taskName))),
  };
});

jest.mock('@/components/ui', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  return {
    YakFilterSwitch: ({ options, value, onChange }: {
      options: Array<{ label: string; value: string }>;
      value: string;
      onChange: (value: string) => void;
    }) => element('select', {
      'data-testid': 'status-filter',
      value,
      onChange: (event: React.ChangeEvent<HTMLSelectElement>) => onChange(event.target.value),
    }, options.map(option => element('option', {
      key: option.value, value: option.value,
    }, option.label))),
  };
});

jest.mock('../components/WorkspaceStateFeedback', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    WorkspaceLoadFailureState: jest.fn(({ onRetry }: { onRetry: () => void }) =>
      ReactRuntime.createElement('button', { onClick: onRetry }, '重新加载执行记录')),
  };
});

jest.mock('./ExecutionDetailDrawer', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return { __esModule: true, default: () => ReactRuntime.createElement('div', {
    'data-testid': 'execution-detail-drawer',
  }) };
});

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  const Empty = Object.assign(
    ({ description }: { description?: React.ReactNode }) => element('div', null, description),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  return {
    ConfigProvider: ({ children }: { children: React.ReactNode }) => element('div', null, children),
    Button: ({ children, onClick }: {
      children: React.ReactNode;
      onClick?: () => void;
    }) => element('button', { onClick }, children),
    DatePicker: {
      RangePicker: ({ onChange }: { onChange: (value: unknown) => void }) =>
        element('button', { onClick: () => onChange(undefined) }, '日期选择'),
    },
    Empty,
    Input: ({ value, onChange, onPressEnter, placeholder }: {
      value: string;
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
      onPressEnter?: () => void;
      placeholder?: string;
    }) => element('input', {
      'data-testid': 'execution-keyword',
      value,
      placeholder,
      onChange,
      onKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') onPressEnter?.();
      },
    }),
    Pagination: ({ current, pageSize, total, onChange }: {
      current: number; pageSize: number; total: number;
      onChange: (page: number, size: number) => void;
    }) => element('button', {
      'data-testid': 'next-page',
      'data-current': String(current),
      'data-total': String(total),
      onClick: () => onChange(current + 1, pageSize),
    }, '下一页'),
    Select: ({ onChange, options, placeholder }: {
      onChange: (value: string | undefined) => void;
      options?: Array<{ label: string; value: string }>;
      placeholder?: string;
    }) => element('select', {
      'aria-label': placeholder,
      onChange: (event: React.ChangeEvent<HTMLSelectElement>) => onChange(event.target.value || undefined),
    }, [element('option', { key: '', value: '' }, '全部'),
      ...(options || []).map(option => element('option', {
        key: option.value, value: option.value,
      }, option.label))]),
    Tooltip: ({ children }: { children: React.ReactNode }) => element('span', null, children),
  };
});

const record = (id: string, taskName: string, status: DevelopmentTaskExecutionSummary['status']):
  DevelopmentTaskExecutionSummary => ({
  id,
  nodeId: 'node-' + id,
  taskName,
  taskType: 'SQL',
  schemaVersion: 1,
  triggerType: 'MANUAL',
  status,
});

const page = (rows: DevelopmentTaskExecutionSummary[], total = rows.length):
  DevelopmentTaskExecutionPage => ({
  records: rows,
  total,
  pageNo: 1,
  pageSize: 20,
});

describe('Data Development execution history modern API migration', () => {
  const list = jest.mocked(listDevelopmentTaskExecutions);

  beforeEach(() => {
    list.mockReset();
    jest.mocked(WorkspaceLoadFailureState).mockClear();
    list.mockResolvedValue(page([record('1', 'Load Orders', 'SUCCESS')], 32));
  });

  it('renders unwrapped execution records and total without reading response.data', async () => {
    render(<ExecutionHistoryPage />);
    expect(await screen.findByText('Load Orders')).toBeInTheDocument();
    expect(screen.getByTestId('execution-records')).toHaveAttribute('data-loading', 'false');
    expect(screen.getByTestId('next-page')).toHaveAttribute('data-total', '32');
    expect(list).toHaveBeenCalledWith(expect.objectContaining({
      pageNo: 1, pageSize: 20,
    }));
  });

  it('preserves server pagination and the query argument shape', async () => {
    render(<ExecutionHistoryPage />);
    await screen.findByText('Load Orders');
    fireEvent.click(screen.getByTestId('next-page'));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 2, pageSize: 20,
    })));
    expect(screen.getByTestId('next-page')).toHaveAttribute('data-current', '2');
  });

  it('retains status filtering and resets to page one when the filter changes', async () => {
    render(<ExecutionHistoryPage />);
    await screen.findByText('Load Orders');
    fireEvent.click(screen.getByTestId('next-page'));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 2,
    })));
    fireEvent.change(screen.getByTestId('status-filter'), { target: { value: 'FAILED' } });
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNo: 1, status: 'FAILED',
    })));
  });

  it('retains trimmed keyword search without introducing another endpoint', async () => {
    render(<ExecutionHistoryPage />);
    await screen.findByText('Load Orders');
    fireEvent.change(screen.getByTestId('execution-keyword'), {
      target: { value: '  orders  ' },
    });
    fireEvent.keyDown(screen.getByTestId('execution-keyword'), { key: 'Enter' });
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'orders', pageNo: 1,
    })));
  });

  it('shows successful empty pages as empty records instead of a failed workspace', async () => {
    list.mockResolvedValue(page([], 0));
    render(<ExecutionHistoryPage />);
    await waitFor(() => expect(screen.getByTestId('execution-records')).toHaveAttribute(
      'data-loading', 'false',
    ));
    expect(screen.getByTestId('execution-records')).toBeEmptyDOMElement();
    expect(screen.getByTestId('next-page')).toHaveAttribute('data-total', '0');
    expect(WorkspaceLoadFailureState).not.toHaveBeenCalled();
  });

  it('preserves the failure state and re-fetch on retry, without inventing success rows', async () => {
    list.mockRejectedValueOnce(new Error('没有执行记录读取权限'))
      .mockResolvedValueOnce(page([record('9', 'Recovered Task', 'SUCCESS')]));
    render(<ExecutionHistoryPage />);
    fireEvent.click(await screen.findByRole('button', { name: '重新加载执行记录' }));
    expect(await screen.findByText('Recovered Task')).toBeInTheDocument();
    expect(list).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('button', { name: '重新加载执行记录' })).not.toBeInTheDocument();
  });

  it('keeps manual refresh using the same modern service call', async () => {
    list.mockResolvedValueOnce(page([record('1', 'Initial Task', 'SUCCESS')]))
      .mockResolvedValueOnce(page([record('2', 'Updated Task', 'FAILED')]));
    render(<ExecutionHistoryPage />);
    await screen.findByText('Initial Task');
    fireEvent.click(screen.getByRole('button', {
      name: 'pages.dataDevelopment.common.refresh',
    }));
    expect(await screen.findByText('Updated Task')).toBeInTheDocument();
    expect(list).toHaveBeenCalledTimes(2);
  });
});
