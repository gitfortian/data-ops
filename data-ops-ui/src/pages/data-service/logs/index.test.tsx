import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { message } from 'antd';
import {
  listRecentDataServiceLogs,
  type DataServiceCallLog,
} from '@/services/data-service';
import DataServiceLogsPage from './index';

let mockProjectId = 42;
let mockPermissionCodes = ['data-service:observe'];
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ permissionCodes: mockPermissionCodes }),
}));
jest.mock('@/services/data-service', () => ({
  listRecentDataServiceLogs: jest.fn(),
}));

jest.mock('@/components/YakButton', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    __esModule: true,
    default: ({ children, onClick, loading }: {
      children: ReactNode;
      onClick?: () => void;
      loading?: boolean;
    }) => ReactRuntime.createElement('button', {
      onClick,
      disabled: loading,
    }, children),
  };
});

jest.mock('@/components/security', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    SecurityQueryTable: ({ dataSource, loading }: {
      dataSource: DataServiceCallLog[];
      loading: boolean;
    }) => ReactRuntime.createElement('div', {
      'data-testid': 'log-table',
      'data-loading': String(loading),
    }, dataSource.map(row => ReactRuntime.createElement(
      'span', { key: row.id }, row.serviceName,
    ))),
  };
});

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  return {
    Input: ({ value, onChange, placeholder }: {
      value: string;
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
      placeholder: string;
    }) => element('input', {
      value,
      onChange,
      placeholder,
      'data-testid': 'log-filter',
    }),
    Tag: ({ children }: { children: ReactNode }) => element('span', null, children),
    Tooltip: ({ children }: { children: ReactNode }) => element('span', null, children),
    Alert: ({ message, description, action }: { message: ReactNode; description: ReactNode; action: ReactNode }) =>
      element('div', { role: 'alert' }, message, description, action),
    message: { error: jest.fn() },
  };
});

const record = (id: number, serviceName: string): DataServiceCallLog => ({
  id,
  apiId: id,
  serviceName,
  servicePath: '/api/' + id,
  callerType: 'CONSOLE',
  success: true,
  durationMs: 10,
  rowCount: 2,
});

describe('Data Service logs modern API migration', () => {
  const listLogs = jest.mocked(listRecentDataServiceLogs);

  beforeEach(() => {
    mockProjectId = 42;
    mockPermissionCodes = ['data-service:observe'];
    listLogs.mockReset();
    jest.mocked(message.error).mockClear();
  });

  it('renders the unwrapped recent-call rows using the existing log table', async () => {
    listLogs.mockResolvedValue([record(1, 'Orders API'), record(2, 'Revenue API')]);

    render(<DataServiceLogsPage />);

    await waitFor(() => expect(screen.getByText('Orders API')).toBeInTheDocument());
    expect(screen.getByText('Revenue API')).toBeInTheDocument();
    expect(screen.getByTestId('log-table')).toHaveAttribute('data-loading', 'false');
    expect(listLogs).toHaveBeenCalledTimes(1);
    expect(message.error).not.toHaveBeenCalled();
  });

  it('preserves the client-side keyword filter after loading data-only rows', async () => {
    listLogs.mockResolvedValue([record(1, 'Orders API'), record(2, 'Revenue API')]);
    render(<DataServiceLogsPage />);
    await waitFor(() => expect(screen.getByText('Orders API')).toBeInTheDocument());

    fireEvent.change(screen.getByTestId('log-filter'), { target: { value: 'revenue' } });

    expect(screen.queryByText('Orders API')).not.toBeInTheDocument();
    expect(screen.getByText('Revenue API')).toBeInTheDocument();
    expect(listLogs).toHaveBeenCalledTimes(1);
  });

  it('keeps manual refresh without switching back to envelope responses', async () => {
    listLogs.mockResolvedValueOnce([record(1, 'Orders API')])
      .mockResolvedValueOnce([record(3, 'Analytics API')]);
    render(<DataServiceLogsPage />);
    await waitFor(() => expect(screen.getByText('Orders API')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '刷新' }));

    await waitFor(() => expect(screen.getByText('Analytics API')).toBeInTheDocument());
    expect(screen.queryByText('Orders API')).not.toBeInTheDocument();
    expect(listLogs).toHaveBeenCalledTimes(2);
  });

  it('preserves an empty result and loading reset without showing a toast', async () => {
    listLogs.mockResolvedValue([]);
    render(<DataServiceLogsPage />);

    await waitFor(() => expect(listLogs).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId('log-table')).toHaveAttribute(
      'data-loading', 'false',
    ));
    expect(screen.getByTestId('log-table')).toBeEmptyDOMElement();
    expect(message.error).not.toHaveBeenCalled();
  });

  it('clears rows on a failed refresh, shows forbidden rather than empty and retries successfully', async () => {
    listLogs.mockResolvedValueOnce([record(1, 'old-project-call')])
      .mockRejectedValueOnce({ response: { status: 403 }, message: 'forbidden' })
      .mockResolvedValueOnce([record(2, 'new-current-call')]);
    render(<DataServiceLogsPage />);
    await screen.findByText('old-project-call');
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('无权读取调用记录'));
    expect(screen.queryByText('old-project-call')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('new-current-call');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('does not render a prior Project response after scope switches', async () => {
    let finish!: (value: DataServiceCallLog[]) => void;
    const pending = new Promise<DataServiceCallLog[]>(resolve => { finish = resolve; });
    listLogs.mockReturnValueOnce(pending)
      .mockResolvedValueOnce([record(3, 'current-project-call')]);
    const view = render(<DataServiceLogsPage />);
    mockProjectId = 43;
    view.rerender(<DataServiceLogsPage />);
    await screen.findByText('current-project-call');
    await act(async () => { finish([record(4, 'old-project-call')]); await pending; });
    expect(screen.queryByText('old-project-call')).not.toBeInTheDocument();
  });

  it('retains the existing error toast and loading reset on failed reads', async () => {
    listLogs.mockRejectedValue(new Error('调用记录加载失败: forbidden'));
    render(<DataServiceLogsPage />);

    await waitFor(() => expect(message.error).toHaveBeenCalledWith(
      '调用记录加载失败: forbidden',
    ));
    expect(screen.getByTestId('log-table')).toHaveAttribute('data-loading', 'false');
    expect(listLogs).toHaveBeenCalledTimes(1);
  });
});
