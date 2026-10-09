import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { listDataServiceConsumers, listDataServiceAccessOverview, type DataServiceConsumer } from '@/services/data-service';
import DataServiceAccessPage from './index';

let mockProjectId = 42;
let mockPermissions = ['data-service:access'];
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ permissionCodes: mockPermissions }),
}));
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useSearchParams: () => [new URLSearchParams(), jest.fn()],
}));
jest.mock('@/services/data-service', () => ({
  listDataServiceConsumers: jest.fn(),
  listDataServiceAccessOverview: jest.fn(),
  getDataServiceConsumer: jest.fn(),
  createDataServiceConsumer: jest.fn(),
  deleteDataServiceConsumer: jest.fn(),
  updateDataServiceConsumer: jest.fn(),
}));
jest.mock('./ConsumerManagementPanel', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    __esModule: true,
    default: ({ consumer, apiOverviewAvailable }: { consumer: DataServiceConsumer; apiOverviewAvailable: boolean }) =>
      ReactRuntime.createElement('div', { 'data-testid': 'managed-consumer' },
        consumer.name, apiOverviewAvailable ? 'API scope verified' : 'API scope unreadable'),
  };
});
jest.mock('@/components/ui', () => {
  const R = require('react') as typeof import('react');
  return {
    YakButton: ({ children, onClick, disabled }: any) =>
      R.createElement('button', { onClick, disabled }, children),
    YakEmpty: ({ title }: any) => R.createElement('span', null, title),
  };
});
jest.mock('antd', () => {
  const R = require('react') as typeof import('react');
  const node = R.createElement;
  const Form = Object.assign(
    ({ children }: any) => node('div', null, children),
    { useForm: () => [{ resetFields: jest.fn(), setFieldsValue: jest.fn(),
                        submit: jest.fn() }],
      Item: ({ children }: any) => node('div', null, children) },
  );
  const Input = Object.assign(({ value, onChange, placeholder }: any) =>
    node('input', { value, onChange, placeholder }),
    { TextArea: ({ value, onChange }: any) => node('textarea', { value, onChange }) });
  const Table = ({ dataSource = [], columns = [], locale }: any) => {
    const action = columns.find((col: any) => col.key === 'action');
    return node('div', { 'data-testid': 'consumer-list' },
      dataSource.length ? dataSource.map((record: any) =>
        node('div', { key: record.id }, record.name, action?.render?.(undefined, record))) : locale?.emptyText);
  };
  return {
    Alert: ({ message, description, action }: any) =>
      node('div', { role: 'alert' }, message, description, action),
    Drawer: ({ open, children }: any) => open ? node('div', { 'data-testid': 'consumer-drawer' }, children) : null,
    Form, Input, InputNumber: Input,
    Modal: Object.assign(({ open, children }: any) => open ? node('div', null, children) : null,
      { confirm: jest.fn() }),
    Select: () => null,
    Switch: () => null,
    Table,
    message: { error: jest.fn(), success: jest.fn() },
  };
});

const consumer = (id: number, name: string): DataServiceConsumer => ({
  id, name, enabled: true, description: '',
  accessScope: 'ALL', apiIds: [], apiCount: 3,
  keyCount: 1, activeKeyCount: 1, ipAccessMode: 'NONE', ipRuleCount: 0,
  defaultRateLimitPerMinute: 60,
});
const pending = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
};
describe('Data Service consumer management Project/read truth', () => {
  const list = jest.mocked(listDataServiceConsumers);
  const apis = jest.mocked(listDataServiceAccessOverview);
  beforeEach(() => {
    mockProjectId = 42;
    mockPermissions = ['data-service:access'];
    list.mockReset().mockResolvedValue([consumer(7, 'Current Consumer')]);
    apis.mockReset().mockResolvedValue([]);
  });

  it('keeps owned consumers visible but disables API authorization if the optional catalog fails', async () => {
    apis.mockRejectedValue(new Error('API overview unavailable'));
    render(<DataServiceAccessPage />);
    await screen.findByText('Current Consumer');
    expect(screen.getByRole('alert')).toHaveTextContent('API 授权清单暂不可用');
    fireEvent.click(screen.getByRole('button', { name: '管理' }));
    expect(screen.getByTestId('managed-consumer')).toHaveTextContent('API scope unreadable');
  });

  it('clears selected consumer and rows before 403, keeps it separate from real empty and retries', async () => {
    list.mockResolvedValueOnce([consumer(7, 'Old Consumer')])
      .mockRejectedValueOnce({ response: { status: 403 } })
      .mockResolvedValueOnce([consumer(8, 'Recovered Consumer')]);
    render(<DataServiceAccessPage />);
    await screen.findByText('Old Consumer');
    fireEvent.click(screen.getByRole('button', { name: '管理' }));
    expect(screen.getByTestId('managed-consumer')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('无权读取当前 Project 的调用方'));
    expect(screen.queryByText('Old Consumer')).not.toBeInTheDocument();
    expect(screen.queryByTestId('managed-consumer')).not.toBeInTheDocument();
    expect(screen.queryByText('暂无调用方')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新建调用方' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await screen.findByText('Recovered Consumer');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('never accepts an old list response after a newer refresh succeeds', async () => {
    const slow = pending<DataServiceConsumer[]>();
    list.mockReset().mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce([consumer(8, 'New Consumer')]);
    render(<DataServiceAccessPage />);
    fireEvent.click(screen.getByRole('button', { name: '刷新' }));
    await screen.findByText('New Consumer');
    await act(async () => { slow.resolve([consumer(7, 'Old Consumer')]); await slow.promise; });
    expect(screen.queryByText('Old Consumer')).not.toBeInTheDocument();
    expect(screen.getByText('New Consumer')).toBeInTheDocument();
  });

  it('remounts across Project boundaries so old successful reads are not rendered', async () => {
    const slow = pending<DataServiceConsumer[]>();
    list.mockReset().mockReturnValueOnce(slow.promise)
      .mockResolvedValueOnce([consumer(43, 'Other Project')]);
    const view = render(<DataServiceAccessPage />);
    mockProjectId = 43;
    view.rerender(<DataServiceAccessPage />);
    await screen.findByText('Other Project');
    await act(async () => { slow.resolve([consumer(42, 'Old Project')]); await slow.promise; });
    expect(screen.queryByText('Old Project')).not.toBeInTheDocument();
  });
});
