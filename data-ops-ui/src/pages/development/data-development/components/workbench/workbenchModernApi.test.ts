import {
  cancelDevelopmentTaskExecution,
  getActiveDevelopmentTaskExecution,
  getDevelopmentTaskDraft,
  getDevelopmentTaskExecution,
  retryDevelopmentTaskExecution,
} from '@/services/data-development';
import {
  cancelWorkbenchExecution,
  loadWorkbenchActiveExecution,
  loadWorkbenchDraft,
  readWorkbenchExecution,
  retryWorkbenchExecution,
} from './workbenchModernApi';

jest.mock('@/services/data-development', () => ({
  cancelDevelopmentTaskExecution: jest.fn(),
  getActiveDevelopmentTaskExecution: jest.fn(),
  getDevelopmentTaskDraft: jest.fn(),
  getDevelopmentTaskExecution: jest.fn(),
  retryDevelopmentTaskExecution: jest.fn(),
}));

const draft = {
  nodeId: 'node-1', draftRevision: 7,
  definition: { taskType: 'SQL', content: 'select 42', configJson: '{}' },
};
const detail = {
  id: 'execution-1', nodeId: 'node-1', status: 'RUNNING',
  runtimeExecutionId: 'engine-12', output: { stdout: 'working' },
};
const submission = {
  id: 'execution-2', nodeId: 'node-1', status: 'PENDING',
};

describe('Workbench modern data-only boundary', () => {
  const getDraft = jest.mocked(getDevelopmentTaskDraft);
  const getActive = jest.mocked(getActiveDevelopmentTaskExecution);
  const getDetail = jest.mocked(getDevelopmentTaskExecution);
  const cancel = jest.mocked(cancelDevelopmentTaskExecution);
  const retry = jest.mocked(retryDevelopmentTaskExecution);

  beforeEach(() => {
    getDraft.mockReset();
    getActive.mockReset();
    getDetail.mockReset();
    cancel.mockReset();
    retry.mockReset();
  });

  it('loads a validated data-only draft without nesting a legacy response.data envelope', async () => {
    getDraft.mockResolvedValue(draft as never);
    await expect(loadWorkbenchDraft('node-1', '草稿读取失败')).resolves.toEqual(draft);
    expect(getDraft).toHaveBeenCalledWith('node-1');
    expect(getDraft).toHaveBeenCalledTimes(1);
  });

  it('does not hydrate a missing draft or hide it as an empty editor definition', async () => {
    getDraft.mockResolvedValue(null as never);
    await expect(loadWorkbenchDraft('node-1', '草稿读取失败'))
      .rejects.toThrow('草稿读取失败');
  });

  it('propagates draft read permission and network failures to the existing editor warning', async () => {
    getDraft.mockRejectedValue(new Error('403 draft denied'));
    await expect(loadWorkbenchDraft('node-1', '草稿读取失败'))
      .rejects.toThrow('403 draft denied');
  });

  it('reattaches an actual active execution with its durable runtime identity', async () => {
    getActive.mockResolvedValue(detail as never);
    await expect(loadWorkbenchActiveExecution('node-1', '执行查询失败'))
      .resolves.toEqual(detail);
    expect(getActive).toHaveBeenCalledWith('node-1');
  });

  it('accepts null as a legitimate absence of an active execution', async () => {
    getActive.mockResolvedValue(null);
    await expect(loadWorkbenchActiveExecution('node-1', '执行查询失败'))
      .resolves.toBeNull();
  });

  it('rejects undefined active execution responses without treating them as successful null', async () => {
    getActive.mockResolvedValue(undefined as never);
    await expect(loadWorkbenchActiveExecution('node-1', '执行查询失败'))
      .rejects.toThrow('执行查询失败');
  });

  it('propagates active-query errors for best-effort reattachment handling', async () => {
    getActive.mockRejectedValue(new Error('backend unavailable'));
    await expect(loadWorkbenchActiveExecution('node-1', '执行查询失败'))
      .rejects.toThrow('backend unavailable');
  });

  it('reads durable execution detail directly for polling and terminal snapshots', async () => {
    getDetail.mockResolvedValue(detail as never);
    await expect(readWorkbenchExecution('execution-1', '详情读取失败'))
      .resolves.toEqual(detail);
    expect(getDetail).toHaveBeenCalledWith('execution-1');
  });

  it('rejects missing persisted execution details rather than making fake success rows', async () => {
    getDetail.mockResolvedValue(undefined as never);
    await expect(readWorkbenchExecution('execution-1', '详情读取失败'))
      .rejects.toThrow('详情读取失败');
  });

  it('keeps status/transport failures available to the existing polling retry corridor', async () => {
    getDetail.mockRejectedValue(new Error('service outage'));
    await expect(readWorkbenchExecution('execution-1', '详情读取失败'))
      .rejects.toThrow('service outage');
  });

  it('returns a true data-only cancel acknowledgement and persisted execution status', async () => {
    const result = { ...detail, status: 'CANCELLED' };
    cancel.mockResolvedValue(result as never);
    await expect(cancelWorkbenchExecution('execution-1', '取消失败'))
      .resolves.toEqual(result);
    expect(cancel).toHaveBeenCalledWith('execution-1');
  });

  it('rejects a cancel response without a real server acknowledgement', async () => {
    cancel.mockResolvedValue(null as never);
    await expect(cancelWorkbenchExecution('execution-1', '取消失败'))
      .rejects.toThrow('取消失败');
  });

  it('preserves cancel business failures without claiming successful cancellation', async () => {
    cancel.mockRejectedValue(new Error('execution already finished'));
    await expect(cancelWorkbenchExecution('execution-1', '取消失败'))
      .rejects.toThrow('execution already finished');
  });

  it('returns a server-issued retry submission with a distinct durable execution ID', async () => {
    retry.mockResolvedValue(submission as never);
    await expect(retryWorkbenchExecution('execution-1', '重试失败'))
      .resolves.toEqual(submission);
    expect(retry).toHaveBeenCalledWith('execution-1');
  });

  it('rejects retry success when the backend returns no durable execution ID', async () => {
    retry.mockResolvedValue({ status: 'PENDING' } as never);
    await expect(retryWorkbenchExecution('execution-1', '重试失败'))
      .rejects.toThrow('重试失败');
    retry.mockResolvedValue(null as never);
    await expect(retryWorkbenchExecution('execution-1', '重试失败'))
      .rejects.toThrow('重试失败');
  });

  it('propagates runtime retry denial instead of producing a fabricated submission', async () => {
    retry.mockRejectedValue(new Error('runtime not attached'));
    await expect(retryWorkbenchExecution('execution-1', '重试失败'))
      .rejects.toThrow('runtime not attached');
  });
});
