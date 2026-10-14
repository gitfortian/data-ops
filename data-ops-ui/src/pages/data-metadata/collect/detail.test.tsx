import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import { getCollectJob, dryRunCollectJob, runCollectJob } from '@/services/metadata/api';
import Detail from './detail';
const mockCan = jest.fn(() => true);
let mockSourceQuery = 'dataSourceId=18';
const mockPush = jest.fn();
const mockParams = jest.fn(() => ({ id: '71' }));
jest.mock('@umijs/max', () => ({
  useParams: () => mockParams(),
  useSearchParams: () => [new URLSearchParams(mockSourceQuery)],
  history: { push: (...args: unknown[]) => mockPush(...args) },
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: mockCan, permissionCodes: ['data-metadata:read'] }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));
jest.mock('@/services/metadata/api', () => ({ getCollectJob: jest.fn(), dryRunCollectJob: jest.fn(), runCollectJob: jest.fn() }));
jest.mock('./components/CollectedJobTables', () => ({ sourceId, job }: { sourceId: string; job: { id: number } }) => (
  <div>
    来源{sourceId}任务{job.id}关联表
  </div>
));
jest.mock('./components/RunHistoryDrawer', () => () => <div>运行历史抽屉</div>);
jest.mock('./components/RunResultModal', () => ({ run }: { run: { runId: number } | null }) => run ? <div>收到运行回执 {run.runId}</div> : null);
beforeEach(() => {
  jest.clearAllMocks();
  mockCan.mockReturnValue(true);
  mockParams.mockReturnValue({ id: '71' });
  mockSourceQuery = 'dataSourceId=18';
  jest.mocked(dryRunCollectJob).mockResolvedValue({ jobId: 71, runId: 801, dryRun: true, status: 'SUCCESS' });
  jest.mocked(runCollectJob).mockResolvedValue({ jobId: 71, runId: 802, dryRun: false, status: 'SUCCESS' });
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
  await screen.findByText('订单采集', { selector: 'h1' });
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

it('runs preview against the persisted task and never starts formal collection from preview', async () => {
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  fireEvent.click(screen.getByText('预演采集范围'));
  await screen.findByText('收到运行回执 801');
  expect(dryRunCollectJob).toHaveBeenCalledWith(71);
  expect(runCollectJob).not.toHaveBeenCalled();
  expect(screen.getByText('已收到通过的预览回执')).toBeInTheDocument();
});

it('requires explicit formal run confirmation and reads the saved scope', async () => {
  const confirm = jest.spyOn(Modal, 'confirm').mockReturnValue({ destroy: jest.fn(), update: jest.fn() });
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  fireEvent.click(screen.getByText('直接采集'));
  expect(runCollectJob).not.toHaveBeenCalled();
  const config = confirm.mock.calls[0][0];
  expect(config.title).toBe('确认正式采集已保存的任务范围？');
  expect(config.okText).toBe('确认并采集');
  await act(async () => { await config.onOk?.(); });
  await waitFor(() => expect(runCollectJob).toHaveBeenCalledWith(71));
  await screen.findByText('已收到成功采集回执');
  confirm.mockRestore();
});

it('offers a precise source return only when the persisted job covers the exact origin', async () => {
  mockSourceQuery = 'dataSourceId=18&database=trade_db&schema=public&table=trade_order';
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  fireEvent.click(screen.getAllByText('返回原来源表')[0]);
  expect(mockPush).toHaveBeenCalledTimes(1);
  const target = String(mockPush.mock.calls[0][0]);
  expect(target.split('?')[0]).toBe('/data-source');
  expect(new URLSearchParams(target.split('?')[1]).get('table')).toBe('trade_order');
  expect(new URLSearchParams(target.split('?')[1]).get('schema')).toBe('public');
});

it('never restores a table when the task is from another Schema', async () => {
  mockSourceQuery = 'dataSourceId=18&database=trade_db&schema=audit&table=trade_order';
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  expect(screen.queryByText('返回原来源表')).not.toBeInTheDocument();
});

it('disables both collection actions for read-only roles', async () => {
  mockCan.mockImplementation((permission: string) => permission === 'data-metadata:read');
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  expect(screen.getByText('预演采集范围').closest('button')).toBeDisabled();
  expect(screen.getByText('直接采集').closest('button')).toBeDisabled();
  expect(dryRunCollectJob).not.toHaveBeenCalled();
});

it('handles a mismatched preview receipt as unknown and sends user to actual history', async () => {
  jest.mocked(dryRunCollectJob).mockResolvedValueOnce({ jobId: 99, runId: 899, dryRun: true, status: 'SUCCESS' });
  const warning = jest.spyOn(Modal, 'warning').mockReturnValue({ destroy: jest.fn(), update: jest.fn() });
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  fireEvent.click(screen.getByText('预演采集范围'));
  await screen.findByText('运行结果待核对');
  expect(screen.queryByText('已收到通过的预览回执')).not.toBeInTheDocument();
  expect(warning).toHaveBeenCalledWith(expect.objectContaining({ title: '暂未收到可信的采集执行结果' }));
  const config = warning.mock.calls[0][0];
  await act(async () => { await config.onOk?.(); });
  expect(screen.getByText('运行历史抽屉')).toBeInTheDocument();
  warning.mockRestore();
});

it('re-opens the guided panel after dismissing it', async () => {
  render(<Detail />);
  await screen.findByText('订单采集', { selector: 'h1' });
  fireEvent.click(screen.getByText('收起引导'));
  expect(screen.queryByText('预演采集范围')).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('继续采集任务步骤'));
  expect(screen.getByText('预演采集范围')).toBeInTheDocument();
});
