import { render, screen } from '@testing-library/react';
import { getCollectJob } from '@/services/metadata/api';
import Detail from './detail';
const mockCan = jest.fn(() => true);
const mockParams = jest.fn(() => ({ id: '71' }));
jest.mock('@umijs/max', () => ({
  useParams: () => mockParams(),
  useSearchParams: () => [new URLSearchParams('dataSourceId=18')],
  history: { push: jest.fn() },
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: mockCan, permissionCodes: ['data-metadata:read'] }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));
jest.mock('@/services/metadata/api', () => ({ getCollectJob: jest.fn() }));
jest.mock('./components/CollectedJobTables', () => ({ sourceId, job }: { sourceId: string; job: { id: number } }) => (
  <div>
    来源{sourceId}任务{job.id}关联表
  </div>
));
jest.mock('./components/RunHistoryDrawer', () => () => <div>运行历史抽屉</div>);
beforeEach(() => {
  jest.clearAllMocks();
  mockCan.mockReturnValue(true);
  mockParams.mockReturnValue({ id: '71' });
  jest
    .mocked(getCollectJob)
    .mockResolvedValue({
      id: 71,
      jobCode: 'orders',
      jobName: '订单采集',
      providerType: 'HARVESTED',
      dataSourceId: 18,
      databaseName: 'trade_db',
      schemaName: 'public',
      enabled: true,
      dryRunPassed: true,
    });
});
it('loads exact task and its persisted scope', async () => {
  render(<Detail />);
  await screen.findByText('订单采集');
  expect(getCollectJob).toHaveBeenCalledWith(71);
  expect(screen.getByText('trade_db')).toBeInTheDocument();
  expect(screen.getByText('来源18任务71关联表')).toBeInTheDocument();
});
it('rejects a mismatched source', async () => {
  jest
    .mocked(getCollectJob)
    .mockResolvedValue({
      id: 71,
      jobCode: 'other',
      jobName: '不应展示',
      providerType: 'HARVESTED',
      dataSourceId: 19,
      enabled: true,
      dryRunPassed: true,
    });
  render(<Detail />);
  await screen.findByText('未能读取任务详情');
  expect(screen.queryByText('不应展示')).not.toBeInTheDocument();
});
it('does not fetch without permission', async () => {
  mockCan.mockReturnValue(false);
  render(<Detail />);
  await screen.findByText('未能读取任务详情');
  expect(getCollectJob).not.toHaveBeenCalled();
});
