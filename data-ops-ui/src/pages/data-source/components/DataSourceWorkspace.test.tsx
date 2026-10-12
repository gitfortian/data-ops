import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import { pageCollectJobs, searchMetadata, pageCollectRuns } from '@/services/metadata/api';
import { listDataSourceDatabases, listDataSourceSchemas } from '@/services/data-source/api';
import { findCoveringCollectionJobs } from '@/services/metadata/collectionHandoff';
import type { CollectionSourceScope } from '@/services/metadata/collectionHandoff';
import type { CollectJobRecord } from '@/services/metadata/types';
import DataSourceWorkspace from './DataSourceWorkspace';

const mockHasPermission = jest.fn((_permission: string) => true);
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useAccess: () => ({ hasPermission: mockHasPermission }),
  useIntl: () => ({ formatMessage: ({ id }: { id: string }) => id }),
}));
jest.mock('@/components/data-source/icons/DatabaseIcons', () => () => <span />);
jest.mock('@/services/metadata/api', () => ({
  pageCollectJobs: jest.fn(),
  searchMetadata: jest.fn(),
  pageCollectRuns: jest.fn(),
}));
jest.mock('@/services/data-source/api', () => ({
  listDataSourceDatabases: jest.fn(),
  listDataSourceSchemas: jest.fn(),
}));
jest.mock('@/services/metadata/collectionHandoff', () => ({
  ...jest.requireActual('@/services/metadata/collectionHandoff'),
  findCoveringCollectionJobs: jest.fn(),
}));
jest.mock('@/pages/data-metadata/collect/components/RunHistoryDrawer', () => ({ job }: { job: CollectJobRecord }) => (
  <div>任务 {job.id} 的运行历史</div>
));
jest.mock(
  './LiveSchemaExplorer',
  () =>
    ({ canRead, onDefineTask }: { canRead: boolean; onDefineTask?: (scope: CollectionSourceScope) => void }) => (
      <div>
        {canRead ? '源库结构探查' : '无读取权限'}
        {onDefineTask ? (
          <button
            type="button"
            onClick={() =>
              onDefineTask({ dataSourceId: '18', databaseName: 'trade_db', schemaName: 'public', tableName: 'orders' })
            }
          >
            配置探查范围采集
          </button>
        ) : null}
      </div>
    ),
);

const job: CollectJobRecord = {
  id: 71,
  jobCode: 'collect-trade',
  jobName: '交易库每日采集',
  providerType: 'HARVESTED',
  dataSourceId: 18,
  databaseName: 'trade_db',
  enabled: true,
  dryRunPassed: true,
};
const props = {
  record: { id: 18, name: '交易库', dbType: 'MYSQL', connStatus: 'CONNECTED' },
  permissions: { canCreate: true, canUpdate: true, canDelete: true, canTest: true, canReadSqlExecutions: true },
  testingId: '',
  onClose: jest.fn(),
  onTestConnection: jest.fn(),
  onEdit: jest.fn(),
};
const mockCovering = jest.mocked(findCoveringCollectionJobs);
beforeEach(() => {
  jest.clearAllMocks();
  mockHasPermission.mockReturnValue(true);
  jest.mocked(listDataSourceDatabases).mockResolvedValue(['trade_db']);
  jest.mocked(listDataSourceSchemas).mockResolvedValue(['public']);
  jest.mocked(pageCollectRuns).mockResolvedValue({ records: [], total: 0, pages: 0, pageNo: 1, pageSize: 1 });
  jest.mocked(pageCollectJobs).mockResolvedValue({ records: [job], total: 1, pages: 1, pageNo: 1, pageSize: 8 });
  mockCovering.mockResolvedValue([]);
});

it('has only immediate exploration and unified collection information tabs', () => {
  render(<DataSourceWorkspace {...props} />);
  expect(screen.getByRole('tab', { name: '即时探查' })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByRole('tab', { name: '采集信息' })).toBeInTheDocument();
  expect(screen.queryByRole('tab', { name: '已采集结构' })).not.toBeInTheDocument();
  expect(screen.queryByRole('tab', { name: '采集任务' })).not.toBeInTheDocument();
  expect(pageCollectJobs).not.toHaveBeenCalled();
  expect(searchMetadata).not.toHaveBeenCalled();
});

it('shows saved source-scoped tasks in collection information and opens the original definition page', async () => {
  render(<DataSourceWorkspace {...props} />);
  fireEvent.click(screen.getByRole('tab', { name: '采集信息' }));
  await screen.findByText(job.jobName);
  expect(pageCollectJobs).toHaveBeenCalledWith(
    expect.objectContaining({ dataSourceId: 18, providerType: 'HARVESTED' }),
  );
  fireEvent.click(screen.getByRole('button', { name: '查看' }));
  expect(history.push).toHaveBeenCalledWith('/data-metadata/collect/71?dataSourceId=18');
});

it('reuses an existing covering task and passes source context without editing it in this workspace', async () => {
  mockCovering.mockResolvedValueOnce([job]);
  render(<DataSourceWorkspace {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '配置探查范围采集' }));
  await waitFor(() => expect(history.push).toHaveBeenCalled());
  const params = new URL(String(jest.mocked(history.push).mock.calls[0][0]), 'http://localhost').searchParams;
  expect(params.get('jobId')).toBe('71');
  expect(params.get('databaseName')).toBe('trade_db');
  expect(params.get('sourceTableName')).toBe('orders');
});

it('opens a new definition only after no covering task is found', async () => {
  render(<DataSourceWorkspace {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '配置探查范围采集' }));
  await waitFor(() => expect(history.push).toHaveBeenCalled());
  const params = new URL(String(jest.mocked(history.push).mock.calls[0][0]), 'http://localhost').searchParams;
  expect(params.get('action')).toBe('define');
  expect(params.has('jobId')).toBe(false);
  expect(params.get('schemaName')).toBe('public');
});

it('lets the user choose when multiple existing tasks cover the source range', async () => {
  mockCovering.mockResolvedValueOnce([job, { ...job, id: 72, jobName: '交易库按需采集' }]);
  render(<DataSourceWorkspace {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '配置探查范围采集' }));
  await screen.findByText('选择已有采集任务');
  expect(history.push).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /交易库按需采集/ }));
  expect(history.push).toHaveBeenCalledWith(expect.stringContaining('jobId=72'));
});

it('does not open task creation after a failed coverage read', async () => {
  mockCovering.mockRejectedValueOnce(new Error('unavailable'));
  render(<DataSourceWorkspace {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '配置探查范围采集' }));
  await screen.findByText('未能完整核对已有任务，请到采集任务页面核验后再定义，避免重复创建');
  expect(history.push).not.toHaveBeenCalled();
});

it('retains source exploration and connection management without metadata permission', () => {
  mockHasPermission.mockImplementation((permission) => permission === 'resource:data-source:read');
  render(<DataSourceWorkspace {...props} />);
  expect(screen.getByText('源库结构探查')).toBeInTheDocument();
  expect(screen.queryByRole('tab', { name: '采集信息' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '检测连接' }));
  expect(props.onTestConnection).toHaveBeenCalledWith(props.record);
});

it('ignores task resolution after the source workspace unmounts', async () => {
  let resolve!: (jobs: CollectJobRecord[]) => void;
  mockCovering.mockReturnValueOnce(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { unmount } = render(<DataSourceWorkspace {...props} />);
  fireEvent.click(screen.getByRole('button', { name: '配置探查范围采集' }));
  unmount();
  await act(async () => resolve([job]));
  expect(history.push).not.toHaveBeenCalled();
});
