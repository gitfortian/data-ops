import { fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import { listDataSourceDatabases, listDataSourceSchemas } from '@/services/data-source/api';
import { pageCollectJobs, pageCollectRuns } from '@/services/metadata/api';
import type { CollectJobRecord } from '@/services/metadata/types';
import CollectionInfo from './CollectionInfo';
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/services/data-source/api', () => ({
  listDataSourceDatabases: jest.fn(),
  listDataSourceSchemas: jest.fn(),
}));
jest.mock('@/services/metadata/api', () => ({ pageCollectJobs: jest.fn(), pageCollectRuns: jest.fn() }));
const job: CollectJobRecord = {
  id: 71,
  jobCode: 'trade',
  jobName: '交易库结构采集',
  providerType: 'HARVESTED',
  dataSourceId: 18,
  databaseName: 'trade_db',
  enabled: false,
  dryRunPassed: true,
};
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(listDataSourceDatabases).mockResolvedValue(['new_db', 'trade_db']);
  jest.mocked(listDataSourceSchemas).mockResolvedValue(['public']);
  jest.mocked(pageCollectJobs).mockResolvedValue({ records: [job], total: 1, pages: 1, pageNo: 1, pageSize: 100 });
  jest.mocked(pageCollectRuns).mockResolvedValue({ records: [], total: 0, pages: 0, pageNo: 1, pageSize: 1 });
});
it('shows all libraries, ordered scope columns, view and prefilled creation', async () => {
  render(<CollectionInfo sourceId="18" canCreate />);
  await screen.findByText(job.jobName);
  expect(screen.getAllByRole('columnheader').map((el) => el.textContent)).toEqual([
    '库名',
    'Schema',
    '采集任务',
    '状态',
    '最近一次运行状态',
    '最近一次采集时间',
    '操作',
  ]);
  expect(screen.getByText('new_db')).toBeInTheDocument();
  expect(screen.getByText('未配置任务')).toBeInTheDocument();
  expect(screen.getAllByRole('row')[1]).toHaveTextContent(job.jobName);
  expect(screen.getByText('未启用')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '查看' }));
  expect(history.push).toHaveBeenLastCalledWith('/data-metadata/collect/71?dataSourceId=18');
  fireEvent.click(screen.getByRole('button', { name: '新建任务' }));
  expect(history.push).toHaveBeenLastCalledWith(
    '/data-metadata/collect?dataSourceId=18&action=define&databaseName=new_db&schemaName=public',
  );
  expect(screen.queryByText('打开采集任务页面')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '采集任务管理' }));
  expect(history.push).toHaveBeenLastCalledWith('/data-metadata/collect?dataSourceId=18');
});
it('uses latest formal run once per task across schemas', async () => {
  jest.mocked(listDataSourceSchemas).mockResolvedValue(['public', 'sales']);
  jest.mocked(pageCollectRuns).mockResolvedValue({
    records: [
      {
        runId: 90,
        jobId: 71,
        status: 'SUCCESS',
        dryRun: false,
        triggerType: 'MANUAL',
        startedAt: '2026-10-12T15:39:24',
        finishedAt: '2026-10-12T15:40:00',
      },
    ],
    total: 1,
    pages: 1,
    pageNo: 1,
    pageSize: 1,
  });
  render(<CollectionInfo sourceId="18" canCreate />);
  await screen.findAllByText('成功');
  expect(screen.getAllByText('2026-10-12 15:40:00')).toHaveLength(2);
  expect(pageCollectRuns).toHaveBeenCalledTimes(1);
  expect(pageCollectRuns).toHaveBeenCalledWith({ jobId: 71, pageNo: 1, pageSize: 1, dryRun: false });
});
it('does not infer absent tasks when discovery fails', async () => {
  jest.mocked(listDataSourceSchemas).mockRejectedValue(new Error('unavailable'));
  render(<CollectionInfo sourceId="18" canCreate />);
  await screen.findAllByText('未能核验');
  expect(screen.queryByRole('button', { name: '新建任务' })).not.toBeInTheDocument();
});
it('filters relations locally and respects create permissions', async () => {
  render(<CollectionInfo sourceId="18" canCreate={false} />);
  await screen.findByText(job.jobName);
  expect(screen.queryByRole('button', { name: '新建任务' })).not.toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'new_db' } });
  expect(screen.queryByText(job.jobName)).not.toBeInTheDocument();
  expect(screen.getByText('new_db')).toBeInTheDocument();
  expect(pageCollectJobs).toHaveBeenCalledTimes(1);
});
it('rejects dry run evidence', async () => {
  jest.mocked(pageCollectRuns).mockResolvedValue({
    records: [{ runId: 90, jobId: 71, status: 'SUCCESS', dryRun: true }],
    total: 1,
    pages: 1,
    pageNo: 1,
    pageSize: 1,
  });
  render(<CollectionInfo sourceId="18" canCreate />);
  await screen.findByText('暂不可读');
  expect(screen.queryByText('成功')).not.toBeInTheDocument();
});
