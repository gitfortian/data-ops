import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { getDataServiceOverview, type DataServiceOverview, type DataServiceOverviewRange } from '@/services/data-service';
import DataServiceOverviewPage from './index';

let mockProjectId = 42;
let mockPermissionCodes = ['data-service:observe'];
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ permissionCodes: mockPermissionCodes }),
}));
jest.mock('@/services/data-service', () => ({
  getDataServiceOverview: jest.fn(),
}));
jest.mock('@/components/YakButton', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    __esModule: true,
    default: ({ children, onClick }: { children: ReactNode; onClick?: () => void }) =>
      ReactRuntime.createElement('button', { onClick }, children),
  };
});
jest.mock('echarts-for-react', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return { __esModule: true, default: () => ReactRuntime.createElement('div', null, 'chart') };
});
jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const make = ReactRuntime.createElement;
  const Empty = Object.assign(
    ({ description }: { description?: ReactNode }) => make('div', null, description),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  return {
    Alert: ({ message, description, action }: { message: ReactNode; description: ReactNode; action: ReactNode }) =>
      make('div', { role: 'alert' }, message, description, action),
    Button: ({ children, onClick }: { children: ReactNode; onClick?: () => void }) =>
      make('button', { onClick }, children),
    Tooltip: ({ children }: { children: ReactNode }) => make('span', null, children),
    Table: ({ dataSource }: { dataSource: unknown[] }) =>
      make('div', { 'data-testid': 'overview-failure-table' }, String(dataSource.length)),
    Empty,
    message: { error: jest.fn() },
  };
});

const makeOverview = (range: DataServiceOverviewRange, calls: number): DataServiceOverview => ({
  range, startTime: '', endTime: '',
  apiTotal: 2, runningApis: 2, stoppedApis: 0,
  totalCalls: calls, successCalls: calls, failureCalls: 0,
  successRate: 100, averageDurationMs: 12, totalRows: 200,
  trend: [], hotApis: [], recentFailures: [],
});
const defer = () => {
  let resolve!: (value: DataServiceOverview) => void;
  const promise = new Promise<DataServiceOverview>(yes => { resolve = yes; });
  return { promise, resolve };
};
const callsCell = () => screen.getByText('调用次数').parentElement;

describe('Data Service overview real read state', () => {
  const request = jest.mocked(getDataServiceOverview);
  beforeEach(() => {
    mockProjectId = 42;
    mockPermissionCodes = ['data-service:observe'];
    request.mockReset();
  });

  it('does not present zero calls as a real metric on 403 and restores after retry', async () => {
    request.mockRejectedValueOnce({ response: { status: 403 }, message: 'forbidden' })
      .mockResolvedValueOnce(makeOverview('24h', 27));
    render(<DataServiceOverviewPage />);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('无权读取数据服务运行概览'));
    expect(screen.getByRole('alert')).toHaveTextContent('统计来源不可用');
    expect(screen.getByText('调用次数').closest('[aria-hidden="true"]')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(callsCell()).toHaveTextContent('27'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByText('调用次数').closest('[aria-hidden="true"]')).toBeNull();
  });

  it('keeps the 7-day response when a slower 24h request finishes afterwards', async () => {
    const slow = defer();
    request.mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce(makeOverview('7d', 73));
    render(<DataServiceOverviewPage />);
    fireEvent.click(screen.getByRole('button', { name: '7 天' }));
    await waitFor(() => expect(callsCell()).toHaveTextContent('73'));
    await act(async () => { slow.resolve(makeOverview('24h', 99)); await slow.promise; });
    expect(callsCell()).toHaveTextContent('73');
  });

  it('clears prior Project statistics and blocks its delayed response', async () => {
    const old = defer();
    request.mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(makeOverview('24h', 43));
    const view = render(<DataServiceOverviewPage />);
    mockProjectId = 43;
    view.rerender(<DataServiceOverviewPage />);
    await waitFor(() => expect(callsCell()).toHaveTextContent('43'));
    await act(async () => { old.resolve(makeOverview('24h', 900)); await old.promise; });
    expect(callsCell()).toHaveTextContent('43');
  });
});
