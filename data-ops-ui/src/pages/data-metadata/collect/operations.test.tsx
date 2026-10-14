import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import type { CollectJobRecord } from '@/services/metadata/types';
import { listAllDataSources } from '@/services/data-source/api';
import { dryRunCollectJob, runCollectJob, pageCollectJobs } from '@/services/metadata/api';
import MetadataCollectPage from './index';
let mockCan = jest.fn(() => true);
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useSearchParams: () => [new URLSearchParams(), jest.fn()],
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ can: mockCan, permissionCodes: [] }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));
jest.mock('@/services/data-source/api', () => ({ listAllDataSources: jest.fn() }));
jest.mock('@/services/metadata/api', () => ({
  pageCollectJobs: jest.fn(),
  getCollectJob: jest.fn(),
  dryRunCollectJob: jest.fn(),
  runCollectJob: jest.fn(),
  deleteCollectJob: jest.fn(),
  changeCollectJobEnabled: jest.fn(),
}));
jest.mock('./components/CollectJobDrawer', () => ({
  __esModule: true,
  default: ({ open, onSaved }: {
    open: boolean;
    onSaved: (job: CollectJobRecord) => void;
  }) => open ? (
    <button type="button" onClick={() => onSaved({ ...mockJob, dryRunPassed: false })}>模拟保存来源任务</button>
  ) : null,
}));
jest.mock('./components/RunHistoryDrawer', () => () => null);
jest.mock(
  './components/RunResultModal',
  () =>
    ({ run, title }: { run: unknown; title: string }) =>
      run ? <div role="dialog">{title}</div> : null,
);
const mockJob = {
  id: 71,
  jobCode: 'orders',
  jobName: '订单采集',
  providerType: 'HARVESTED' as const,
  dataSourceId: 18,
  databaseName: 'trade_db',
  schemaName: 'public',
  enabled: true,
  dryRunPassed: true,
  cronExpression: '0 0 3 * * ?',
};
beforeEach(() => {
  jest.clearAllMocks();
  mockCan.mockReturnValue(true);
  jest
    .mocked(listAllDataSources)
    .mockResolvedValue({ bizData: [{ id: 18, name: '订单库' }], pagination: { pageNo: 1, pageSize: 10, total: 1 } });
  jest.mocked(pageCollectJobs).mockResolvedValue({ records: [mockJob], total: 1, pages: 1, pageNo: 1, pageSize: 10 });
  jest.mocked(dryRunCollectJob).mockResolvedValue({ runId: 1, jobId: 71, dryRun: true, status: 'SUCCESS' });
  jest.mocked(runCollectJob).mockResolvedValue({ runId: 2, jobId: 71, dryRun: false, status: 'SUCCESS' });
});
it('shows a concise task surface and runs preview without starting collection', async () => {
  render(<MetadataCollectPage />);
  await screen.findByText('订单采集');
  expect(screen.getByText('每天 03:00')).toBeInTheDocument();
  expect(screen.queryByText('保存范围')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '采集预览' }));
  await screen.findByRole('dialog');
  expect(dryRunCollectJob).toHaveBeenCalledWith(71);
  expect(runCollectJob).not.toHaveBeenCalled();
  expect(document.body.textContent).not.toMatch(/dry-run|执行器|冻结/);
});
it('starts collection only after confirmation of the saved task scope', async () => {
  const confirm = jest.spyOn(Modal, 'confirm').mockReturnValue({ destroy: jest.fn(), update: jest.fn() });
  render(<MetadataCollectPage />);
  await screen.findByText('订单采集');
  fireEvent.click(screen.getByRole('button', { name: '立即采集' }));
  expect(runCollectJob).not.toHaveBeenCalled();
  const options = confirm.mock.calls[0][0];
  expect(options.title).toBe('开始采集？');
  const scopeView = render(<>{options.content}</>);
  expect(scopeView.getByText('trade_db')).toBeInTheDocument();
  expect(scopeView.getByText('订单库')).toBeInTheDocument();
  await act(async () => {
    await options.onOk?.();
  });
  await waitFor(() => expect(runCollectJob).toHaveBeenCalledWith(71));
  confirm.mockRestore();
});
it('keeps execution unavailable without creation permission', async () => {
  mockCan.mockReturnValue(false);
  render(<MetadataCollectPage />);
  await screen.findByText('订单采集');
  expect(screen.getByRole('button', { name: '立即采集' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '采集预览' })).toBeDisabled();
});

it('continues from a real saved Job receipt through preview and confirmed collection', async () => {
  const confirm = jest.spyOn(Modal, 'confirm').mockReturnValue({ destroy: jest.fn(), update: jest.fn() });
  render(<MetadataCollectPage />);
  fireEvent.click(screen.getByRole('button', { name: '新建采集任务' }));
  fireEvent.click(screen.getByText('模拟保存来源任务'));
  expect(await screen.findByText('任务已保存')).toBeInTheDocument();
  expect(dryRunCollectJob).not.toHaveBeenCalled();
  expect(runCollectJob).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('预演采集范围'));
  expect(await screen.findByText('已收到通过的预览回执')).toBeInTheDocument();
  expect(dryRunCollectJob).toHaveBeenCalledWith(71);
  expect(runCollectJob).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('正式采集'));
  expect(runCollectJob).not.toHaveBeenCalled();
  const options = confirm.mock.calls.at(-1)?.[0];
  expect(options?.title).toBe('开始采集？');
  await act(async () => { await options?.onOk?.(); });
  await screen.findByText('已收到成功采集回执');
  expect(runCollectJob).toHaveBeenCalledWith(71);
  confirm.mockRestore();
});

it('fails closed after a preview failure and directs users to run history', async () => {
  jest.mocked(dryRunCollectJob).mockResolvedValueOnce({
    runId: 3, jobId: 71, dryRun: true, status: 'FAILED', errorMessage: 'no privilege',
  });
  render(<MetadataCollectPage />);
  fireEvent.click(screen.getByRole('button', { name: '新建采集任务' }));
  fireEvent.click(screen.getByText('模拟保存来源任务'));
  fireEvent.click(screen.getByText('预演采集范围'));
  expect(await screen.findByText('运行结果待核对')).toBeInTheDocument();
  expect(screen.getByText(/核对运行历史/)).toBeInTheDocument();
  expect(runCollectJob).not.toHaveBeenCalled();
});

it('rejects a mismatched backend Run receipt instead of presenting another task as success', async () => {
  const warning = jest.spyOn(Modal, 'warning').mockReturnValue({ destroy: jest.fn(), update: jest.fn() });
  jest.mocked(dryRunCollectJob).mockResolvedValueOnce({
    runId: 333, jobId: 999, dryRun: true, status: 'SUCCESS',
  });
  render(<MetadataCollectPage />);
  fireEvent.click(screen.getByRole('button', { name: '新建采集任务' }));
  fireEvent.click(screen.getByText('模拟保存来源任务'));
  fireEvent.click(screen.getByText('预演采集范围'));
  expect(await screen.findByText('运行结果待核对')).toBeInTheDocument();
  expect(screen.queryByText('已收到通过的预览回执')).not.toBeInTheDocument();
  expect(runCollectJob).not.toHaveBeenCalled();
  expect(warning).toHaveBeenCalledWith(expect.objectContaining({ title: '暂未收到执行结果' }));
  warning.mockRestore();
});
