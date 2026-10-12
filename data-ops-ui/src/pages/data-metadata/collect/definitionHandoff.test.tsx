import { render, screen, waitFor } from '@testing-library/react';
import { listAllDataSources } from '@/services/data-source/api';
import { getCollectJob, pageCollectJobs } from '@/services/metadata/api';
import type { CollectJobInitialScope } from './components/CollectJobDrawer';
import type { CollectJobRecord } from '@/services/metadata/types';
import MetadataCollectPage from './index';

let mockParams = new URLSearchParams();
const mockSetParams = jest.fn();
const mockCan = jest.fn((_permission: string) => true);
jest.mock('@umijs/max', () => ({ useSearchParams: () => [mockParams, mockSetParams] }));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: mockCan, permissionCodes: [] }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));
jest.mock('@/services/data-source/api', () => ({ listAllDataSources: jest.fn() }));
jest.mock('@/services/metadata/api', () => ({
  getCollectJob: jest.fn(),
  pageCollectJobs: jest.fn(),
  changeCollectJobEnabled: jest.fn(),
  deleteCollectJob: jest.fn(),
  dryRunCollectJob: jest.fn(),
  runCollectJob: jest.fn(),
}));
jest.mock('@/components/ui', () => ({
  YakButton: ({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) => (
    <button type="button" onClick={onClick}>
      {children}
    </button>
  ),
  YakEmpty: () => <span>空列表</span>,
}));
jest.mock(
  './components/CollectJobDrawer',
  () =>
    ({
      open,
      editing,
      initialDataSourceId,
      initialScope,
    }: {
      open: boolean;
      editing: CollectJobRecord | null;
      initialDataSourceId?: number;
      initialScope?: CollectJobInitialScope;
    }) =>
      open ? (
        <div role="dialog" aria-label="任务定义">
          <span data-testid="definition-values">{JSON.stringify({ editing, initialDataSourceId, initialScope })}</span>
        </div>
      ) : null,
);
jest.mock('./components/RunHistoryDrawer', () => () => null);
jest.mock('./components/RunResultModal', () => () => null);
const saved: CollectJobRecord = {
  id: 71,
  jobCode: 'trade',
  jobName: '已保存任务',
  providerType: 'HARVESTED',
  dataSourceId: 18,
  databaseName: 'original_db',
  schemaName: 'original_schema',
  tablePattern: 'original_%',
  cronExpression: '0 0 2 * * ?',
  enabled: true,
  dryRunPassed: true,
};
beforeEach(() => {
  jest.clearAllMocks();
  mockCan.mockReturnValue(true);
  jest
    .mocked(listAllDataSources)
    .mockResolvedValue({ bizData: [{ id: 18, name: '交易库' }], pagination: { pageNo: 1, pageSize: 10, total: 1 } });
  jest.mocked(pageCollectJobs).mockResolvedValue({ records: [], total: 0, pages: 0, pageNo: 1, pageSize: 10 });
  jest.mocked(getCollectJob).mockResolvedValue(saved);
});

it('opens a new task definition after source verification with library-level context', async () => {
  mockParams = new URLSearchParams(
    'dataSourceId=18&action=define&databaseName=trade_db&schemaName=public&sourceTableName=orders',
  );
  render(<MetadataCollectPage />);
  await screen.findByRole('dialog', { name: '任务定义' });
  expect(JSON.parse(screen.getByTestId('definition-values').textContent || '{}')).toEqual({
    editing: null,
    initialDataSourceId: 18,
    initialScope: {
      databaseName: 'trade_db',
      schemaName: 'public',
      sourceTableName: 'orders',
      jobName: 'trade_db结构采集',
    },
  });
  const params = mockSetParams.mock.calls[0][0] as URLSearchParams;
  expect(params.get('dataSourceId')).toBe('18');
  expect(params.has('action')).toBe(false);
  expect(getCollectJob).not.toHaveBeenCalled();
});

it('reopens an existing task from owner storage without replacing its range or cron with navigation hints', async () => {
  mockParams = new URLSearchParams(
    'dataSourceId=18&action=define&jobId=71&databaseName=new_db&schemaName=public&sourceTableName=orders',
  );
  render(<MetadataCollectPage />);
  await screen.findByRole('dialog', { name: '任务定义' });
  const values = JSON.parse(screen.getByTestId('definition-values').textContent || '{}');
  expect(getCollectJob).toHaveBeenCalledWith(71);
  expect(values.editing).toEqual(saved);
  expect(values.initialScope).toBeUndefined();
});

it('rejects a task that belongs to a different source', async () => {
  mockParams = new URLSearchParams('dataSourceId=18&action=define&jobId=71');
  jest.mocked(getCollectJob).mockResolvedValueOnce({ ...saved, dataSourceId: 99 });
  render(<MetadataCollectPage />);
  await screen.findByText('任务不属于当前核验的数据源，不能打开定义');
  expect(screen.queryByRole('dialog', { name: '任务定义' })).not.toBeInTheDocument();
});

it('does not open a definition when the source is absent from the current project', async () => {
  mockParams = new URLSearchParams('dataSourceId=99&action=define&jobId=71');
  render(<MetadataCollectPage />);
  await waitFor(() => expect(mockSetParams).toHaveBeenCalled());
  expect(getCollectJob).not.toHaveBeenCalled();
  expect(screen.queryByRole('dialog', { name: '任务定义' })).not.toBeInTheDocument();
});

it('checks creation permission before opening a prefilled new-task definition', async () => {
  mockParams = new URLSearchParams('dataSourceId=18&action=define&databaseName=trade_db');
  mockCan.mockImplementation((permission) => permission !== 'data-metadata:create');
  render(<MetadataCollectPage />);
  await screen.findByText('当前账号没有创建采集任务的权限');
  expect(screen.queryByRole('dialog', { name: '任务定义' })).not.toBeInTheDocument();
});
