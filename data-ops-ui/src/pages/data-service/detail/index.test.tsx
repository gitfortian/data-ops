import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import {
  getDataService, listDataServiceDataSources, getDataServiceRuntime,
  listDataServiceKeys, listDataServiceLogs, type DataServiceApi,
} from '@/services/data-service';
import DataServiceDetailPage from './index';

let mockProjectId = 42;
let mockRouteId = '7';
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ permissionCodes: ['data-service:observe', 'data-service:runtime'] }),
}));
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useParams: () => ({ id: mockRouteId }),
  useSearchParams: () => [new URLSearchParams()],
  useAccess: () => ({ hasPermission: (code: string) =>
    ['data-service:observe', 'data-service:runtime', 'data-service:access'].includes(code) }),
}));
jest.mock('@/services/data-service', () => ({
  DATA_SERVICE_NODE_SOURCE: 'DATA_DEVELOPMENT_DATA_SERVICE',
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE: 'DATA_DEVELOPMENT_RELEASE',
  DATA_SERVICE_PROVIDER_SOURCE_LABELS: {},
  getDataService: jest.fn(),
  listDataServiceDataSources: jest.fn(),
  getDataServiceRuntime: jest.fn(),
  listDataServiceKeys: jest.fn(),
  listDataServiceLogs: jest.fn(),
  getDataServiceInvocationEvidence: jest.fn(),
}));
jest.mock('@/services/consumption', () => ({
  consumptionProductPath: () => '/consumer/data-service/7',
}));
jest.mock('@/components/YakTab', () => {
  const R = require('react') as typeof import('react');
  return { __esModule: true, default: () => R.createElement('div', null, 'tabs') };
});
jest.mock('../components/DataServiceAccessControlPanel', () => () => null);
jest.mock('../components/DataServiceApiCallPanel', () => () => null);
jest.mock('antd', () => {
  const R = require('react') as typeof import('react');
  const element = R.createElement;
  const Wrap = ({ children }: { children?: ReactNode }) => element('div', null, children);
  const Button = ({ children, onClick }: { children?: ReactNode; onClick?: () => void }) =>
    element('button', { onClick }, children);
  const Empty = Object.assign(
    ({ description, children }: { description?: ReactNode; children?: ReactNode }) =>
      element('div', null, description, children),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  const Descriptions = Object.assign(Wrap, { Item: Wrap });
  return {
    Alert: ({ message, description, action }: {
      message: ReactNode; description: ReactNode; action?: ReactNode,
    }) => element('div', { role: 'alert' }, message, description, action),
    Button, ConfigProvider: Wrap, Descriptions, Empty, Spin: () => element('div', null, 'loading'),
    Table: Wrap, Tooltip: Wrap, message: { error: jest.fn() },
  };
});
const api = (id: number, name: string): DataServiceApi => ({
  id, name, path: '/api/' + id, runtimePath: '/runtime/' + id,
  dataSourceId: 3, sql: 'select 1', parameterNames: [],
  maxRows: 100, timeoutSeconds: 30, enabled: true, authMode: 'API_KEY',
});
const pending = () => {
  let resolve!: (v: DataServiceApi) => void;
  const promise = new Promise<DataServiceApi>(yes => { resolve = yes; });
  return { promise, resolve };
};

describe('Data Service detail source state and Project safety', () => {
  const get = jest.mocked(getDataService);
  const sources = jest.mocked(listDataServiceDataSources);
  const runtime = jest.mocked(getDataServiceRuntime);
  const keys = jest.mocked(listDataServiceKeys);
  const logs = jest.mocked(listDataServiceLogs);

  beforeEach(() => {
    mockProjectId = 42;
    mockRouteId = '7';
    get.mockReset().mockResolvedValue(api(7, 'Orders API'));
    sources.mockReset().mockResolvedValue([{ value: '3', label: 'Main DB' }]);
    runtime.mockReset().mockResolvedValue({
      totalCalls: 12, successRate: 1, averageDurationMs: 5, p95DurationMs: 6,
    } as never);
    keys.mockReset().mockResolvedValue([]);
    logs.mockReset().mockResolvedValue([]);
  });

  it('does not treat 403 as absent and retries the owning API without stale rows', async () => {
    get.mockRejectedValueOnce({ response: { status: 403 } })
      .mockResolvedValueOnce(api(7, 'Recovered API'));
    render(<DataServiceDetailPage />);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('无权读取该 API'));
    expect(screen.queryByText('Orders API')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('Recovered API');
    expect(screen.queryByText('无权读取该 API')).not.toBeInTheDocument();
  });

  it('treats a 404 as absent OR intentionally inaccessible, not proof of absence', async () => {
    get.mockRejectedValue({ response: { status: 404 } });
    render(<DataServiceDetailPage />);
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('API 不存在或当前项目无权查看'));
    expect(screen.queryByText('Orders API')).not.toBeInTheDocument();
  });

  it('keeps a valid service available when source names, runtime and keys are down', async () => {
    sources.mockRejectedValue(new Error('source down'));
    runtime.mockRejectedValue(new Error('runtime down'));
    keys.mockRejectedValue(new Error('keys down'));
    render(<DataServiceDetailPage />);
    await screen.findByText('Orders API');
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('部分 API 详情来源不可用'));
    expect(screen.queryByText('API 不存在或当前项目无权查看')).not.toBeInTheDocument();
    expect(screen.getByText('API Keys').parentElement).toHaveTextContent('—');
  });

  it('drops the previous Project response even after a delayed success arrives', async () => {
    const old = pending();
    get.mockReset().mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(api(7, 'New Project API'));
    const view = render(<DataServiceDetailPage />);
    mockProjectId = 43;
    view.rerender(<DataServiceDetailPage />);
    await screen.findByText('New Project API');
    await act(async () => { old.resolve(api(7, 'Old Project API')); await old.promise; });
    expect(screen.queryByText('Old Project API')).not.toBeInTheDocument();
  });
});
