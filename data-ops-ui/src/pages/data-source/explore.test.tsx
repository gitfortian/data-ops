import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ScopedExplorePage from './explore';

const mockPush = jest.fn();
const mockReplace = jest.fn();
const mockList = jest.fn();
const mockGet = jest.fn();
const mockSourceExplorer = jest.fn();
let mockProjectId = 1;
let mockQuery = new URLSearchParams();
let mockRead = true;

jest.mock('@umijs/max', () => ({
  history: { push: (...args: unknown[]) => mockPush(...args),
    replace: (...args: unknown[]) => mockReplace(...args) },
  useSearchParams: () => [mockQuery],
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({
    can: (permission: string) => mockRead && ['resource:data-source:read', 'modeling:read'].includes(permission),
    permissionCodes: mockRead ? ['resource:data-source:read', 'modeling:read'] : [],
  }),
}));
jest.mock('@/services/data-source/api', () => ({
  listAllDataSources: (...args: unknown[]) => mockList(...args),
  getDataSource: (...args: unknown[]) => mockGet(...args),
}));
jest.mock('@/components/data-source/LiveSchemaExplorer', () => ({
  __esModule: true,
  default: (props: { sourceId: string; initialTable?: { table: string }; onOpenOds?: (scope: unknown) => void }) => {
    mockSourceExplorer(props);
    return <div data-testid="real-explorer">
      <span>当前数据源 {props.sourceId}</span><span>{props.initialTable?.table || '未选表'}</span>
      <button onClick={() => props.onOpenOds?.({
        dataSourceId: Number(props.sourceId), database: 'trade_db', schema: 'public', table: 'orders',
      })}>打开或创建 ODS</button>
    </div>;
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 1;
  mockRead = true;
  mockQuery = new URLSearchParams();
  mockList.mockResolvedValue({ bizData: [
    { id: 10, name: '订单库', dbType: 'MYSQL' }, { id: 11, name: '产品库', dbType: 'MYSQL' },
  ] });
  mockGet.mockResolvedValue({ id: 10, name: '订单库', dbType: 'MYSQL' });
});

it('opens an exact authorized return-table and carries the same source into ODS', async () => {
  mockQuery = new URLSearchParams({
    sourceId: '10', database: 'trade_db', schema: 'public', table: 'orders',
  });
  render(<ScopedExplorePage />);
  expect(await screen.findByTestId('real-explorer')).toBeInTheDocument();
  expect(screen.getByText('orders')).toBeInTheDocument();
  expect(mockGet).toHaveBeenCalledWith(10);
  fireEvent.click(screen.getByRole('button', { name: '打开或创建 ODS' }));
  expect(mockPush).toHaveBeenCalledWith(expect.stringContaining(
    'sourceJourney=ods&origin=live-source&datasourceId=10',
  ));
});

it('rejects a source outside the current authorized project list before reading details', async () => {
  mockQuery = new URLSearchParams({ sourceId: '99' });
  render(<ScopedExplorePage />);
  expect(await screen.findByText(/当前项目无法访问此数据源/)).toBeInTheDocument();
  expect(mockGet).not.toHaveBeenCalled();
  expect(screen.queryByTestId('real-explorer')).not.toBeInTheDocument();
});

it('does not recover malformed partial source coordinates', async () => {
  mockQuery = new URLSearchParams({ sourceId: '10', database: 'trade_db' });
  render(<ScopedExplorePage />);
  expect(await screen.findByText(/来源地址不完整或不合法/)).toBeInTheDocument();
  expect(mockGet).not.toHaveBeenCalled();
});

it('keeps the explorer blocked when source-read permission is revoked', async () => {
  mockQuery = new URLSearchParams({ sourceId: '10' });
  mockRead = false;
  render(<ScopedExplorePage />);
  expect(await screen.findByText(/没有当前项目的数据源读取权限/)).toBeInTheDocument();
  await waitFor(() => expect(mockList).not.toHaveBeenCalled());
  expect(screen.queryByTestId('real-explorer')).not.toBeInTheDocument();
});
