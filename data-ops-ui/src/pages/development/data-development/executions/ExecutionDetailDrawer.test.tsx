import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import {
  cancelDevelopmentTaskExecution,
  getDevelopmentTaskExecution,
  retryDevelopmentTaskExecution,
  type DevelopmentTaskExecutionDetail,
  type DevelopmentTaskExecutionSummary,
} from '@/services/data-development';
import { Modal, message } from 'antd';
import ExecutionDetailDrawer from './ExecutionDetailDrawer';

jest.mock('@/services/data-development', () => ({
  getDevelopmentTaskExecution: jest.fn(),
  cancelDevelopmentTaskExecution: jest.fn(),
  retryDevelopmentTaskExecution: jest.fn(),
}));

const mockIntl = {
  locale: 'zh-CN',
  formatMessage: ({ id }: { id: string }) => id,
};
let mockCanExecute = true;

jest.mock('@umijs/max', () => ({
  useIntl: () => mockIntl,
  useAccess: () => ({ hasPermission: () => mockCanExecute }),
  history: { push: jest.fn() },
}));

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const el = ReactRuntime.createElement;
  const Descriptions = Object.assign(
    ({ children }: { children: React.ReactNode }) => el('dl', null, children),
    { Item: ({ label, children }: { label: React.ReactNode; children: React.ReactNode }) =>
      el('div', null, el('dt', null, label), el('dd', null, children)) },
  );
  const Empty = Object.assign(
    ({ description }: { description: React.ReactNode }) => el('div', null, description),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  return {
    Button: ({ children, onClick, loading, disabled }: {
      children: React.ReactNode;
      onClick?: () => void;
      loading?: boolean;
      disabled?: boolean;
    }) => el('button', { onClick, disabled: loading || disabled }, children),
    Descriptions,
    Drawer: ({ open, extra, children }: {
      open: boolean; extra: React.ReactNode; children: React.ReactNode;
    }) => open ? el('div', { 'data-testid': 'detail-drawer' }, extra, children) : null,
    Empty,
    Modal: { confirm: jest.fn() },
    Spin: () => el('span', { 'data-testid': 'detail-spinner' }, '加载中'),
    Table: () => el('div', { 'data-testid': 'sql-result-table' }),
    Tooltip: ({ children }: { children: React.ReactNode }) => el('span', null, children),
    message: { error: jest.fn(), success: jest.fn() },
  };
});

const record = (id: string, status: DevelopmentTaskExecutionSummary['status'] = 'SUCCESS'):
  DevelopmentTaskExecutionSummary => ({
  id, nodeId: 'node-1', taskName: 'SQL Orders', taskType: 'SQL',
  schemaVersion: 1, triggerType: 'MANUAL', status,
});

const detail = (id: string, status: DevelopmentTaskExecutionDetail['status'] = 'SUCCESS',
  retryOfExecutionId?: string): DevelopmentTaskExecutionDetail => ({
  ...record(id, status),
  retryOfExecutionId,
  content: 'SELECT 42 AS answer',
  configJson: '{"source":"orders"}',
  output: { stdout: 'finished' },
  runtimeExecutionId: 'runtime-' + id,
});

describe('Data Development execution detail and modern commands', () => {
  const get = jest.mocked(getDevelopmentTaskExecution);
  const cancel = jest.mocked(cancelDevelopmentTaskExecution);
  const retry = jest.mocked(retryDevelopmentTaskExecution);
  const confirm = jest.mocked(Modal.confirm);
  const toast = jest.mocked(message.error);
  const success = jest.mocked(message.success);

  beforeEach(() => {
    jest.useRealTimers();
    mockCanExecute = true;
    get.mockReset();
    cancel.mockReset();
    retry.mockReset();
    confirm.mockReset();
    toast.mockReset();
    success.mockReset();
    get.mockImplementation(async id => detail(id));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('renders the unwrapped persisted detail, runtime identity and frozen definition snapshot', async () => {
    render(<ExecutionDetailDrawer open record={record('run-1')} onClose={jest.fn()} />);
    expect(await screen.findByText('SQL Orders')).toBeInTheDocument();
    expect(screen.getByText('runtime-run-1')).toBeInTheDocument();
    expect(screen.getByText('SELECT 42 AS answer')).toBeInTheDocument();
    expect(screen.getByText('{"source":"orders"}')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('run-1');
    expect(toast).not.toHaveBeenCalled();
  });

  it('preserves empty detail rejection and the localized blocking-load error', async () => {
    get.mockResolvedValue(null as never);
    render(<ExecutionDetailDrawer open record={record('run-1')} onClose={jest.fn()} />);
    await waitFor(() => expect(toast).toHaveBeenCalledWith(
      'pages.dataDevelopment.execution.detailFailed',
    ));
    expect(screen.getByText('pages.dataDevelopment.execution.detailEmpty')).toBeInTheDocument();
  });

  it('keeps transport/business failures distinguishable from successful empty detail', async () => {
    get.mockRejectedValue(new Error('Project access denied'));
    render(<ExecutionDetailDrawer open record={record('run-1')} onClose={jest.fn()} />);
    await waitFor(() => expect(toast).toHaveBeenCalledWith(
      'pages.dataDevelopment.execution.detailFailed',
    ));
    expect(get).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('SQL Orders')).not.toBeInTheDocument();
  });

  it('traces the persisted retry ancestry through data-only reads and stops at the source', async () => {
    get.mockImplementation(async id => id === 'run-3'
      ? detail(id, 'FAILED', 'run-2')
      : id === 'run-2'
        ? detail(id, 'FAILED', 'run-1')
        : detail(id));
    render(<ExecutionDetailDrawer open record={record('run-3', 'FAILED')}
      onClose={jest.fn()} />);
    expect(await screen.findByRole('button', {
      name: '#run-1 · pages.dataDevelopment.execution.success',
    })).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('run-1');
    expect(screen.getByRole('button', {
      name: '#run-2 · pages.dataDevelopment.execution.failed',
    })).toBeInTheDocument();
    expect(screen.getByRole('button', {
      name: '#run-3 · pages.dataDevelopment.execution.failed',
    })).toBeInTheDocument();
    expect(get).toHaveBeenCalledTimes(3);
  });

  it('stops retry-chain traversal at a duplicate durable execution ID', async () => {
    get.mockResolvedValue(detail('run-1', 'FAILED', 'run-1'));
    render(<ExecutionDetailDrawer open record={record('run-1', 'FAILED')}
      onClose={jest.fn()} />);
    await screen.findByText('SQL Orders');
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('uses the modern cancel result and updates the visible execution', async () => {
    get.mockResolvedValue(detail('run-1', 'RUNNING'));
    cancel.mockResolvedValue(detail('run-1', 'CANCELLED'));
    const changed = jest.fn();
    render(<ExecutionDetailDrawer open record={record('run-1', 'RUNNING')}
      onClose={jest.fn()} onChanged={changed} />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.execution.cancel',
    }));
    expect(confirm).toHaveBeenCalledTimes(1);
    const onOk = confirm.mock.calls[0][0].onOk as () => Promise<void>;
    await act(async () => { await onOk(); });
    expect(cancel).toHaveBeenCalledWith('run-1');
    expect(await screen.findByText('pages.dataDevelopment.execution.state.cancelled'))
      .toBeInTheDocument();
    expect(success).toHaveBeenCalledWith(
      'pages.dataDevelopment.execution.cancelledSuccess',
    );
    expect(changed).toHaveBeenCalledTimes(1);
  });

  it('uses the modern retry result then reads the newly submitted execution', async () => {
    get.mockImplementation(async id => detail(id, 'FAILED'));
    retry.mockResolvedValue({ id: 'run-2', nodeId: 'node-1',
      taskType: 'SQL', status: 'PENDING' });
    const changed = jest.fn();
    render(<ExecutionDetailDrawer open record={record('run-1', 'FAILED')}
      onClose={jest.fn()} onChanged={changed} />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.execution.retry',
    }));
    const onOk = confirm.mock.calls[0][0].onOk as () => Promise<void>;
    await act(async () => { await onOk(); });
    expect(retry).toHaveBeenCalledWith('run-1');
    expect(get).toHaveBeenCalledWith('run-2');
    expect(await screen.findByText('runtime-run-2')).toBeInTheDocument();
    expect(success).toHaveBeenCalledWith(
      'pages.dataDevelopment.execution.retriedSuccess',
    );
    expect(changed).toHaveBeenCalledTimes(1);
  });

  it('still rejects a retry acknowledgement lacking a durable execution ID', async () => {
    get.mockResolvedValue(detail('run-1', 'FAILED'));
    retry.mockResolvedValue(null as never);
    render(<ExecutionDetailDrawer open record={record('run-1', 'FAILED')}
      onClose={jest.fn()} />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.execution.retry',
    }));
    const onOk = confirm.mock.calls[0][0].onOk as () => Promise<void>;
    await act(async () => { await onOk(); });
    expect(toast).toHaveBeenCalledWith('retry submission unavailable');
    expect(get).not.toHaveBeenCalledWith('run-2');
    expect(success).not.toHaveBeenCalled();
  });

  it('rejects a cancelled execution command with no durable result instead of reporting success', async () => {
    get.mockResolvedValue(detail('run-1', 'RUNNING'));
    cancel.mockResolvedValue(null as never);
    render(<ExecutionDetailDrawer open record={record('run-1', 'RUNNING')}
      onClose={jest.fn()} />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.execution.cancel',
    }));
    const onOk = confirm.mock.calls[0][0].onOk as () => Promise<void>;
    await act(async () => { await onOk(); });
    expect(toast).toHaveBeenCalledWith('cancel result unavailable');
    expect(success).not.toHaveBeenCalled();
  });

  it('shows backend command errors without constructing a synthetic successful execution', async () => {
    get.mockResolvedValue(detail('run-1', 'FAILED'));
    retry.mockRejectedValue(new Error('retry denied'));
    render(<ExecutionDetailDrawer open record={record('run-1', 'FAILED')}
      onClose={jest.fn()} />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.execution.retry',
    }));
    const onOk = confirm.mock.calls[0][0].onOk as () => Promise<void>;
    await act(async () => { await onOk(); });
    expect(toast).toHaveBeenCalledWith('retry denied');
    expect(success).not.toHaveBeenCalled();
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('does not expose cancel or retry actions without execute permission', async () => {
    mockCanExecute = false;
    get.mockResolvedValue(detail('run-1', 'RUNNING'));
    render(<ExecutionDetailDrawer open record={record('run-1', 'RUNNING')}
      onClose={jest.fn()} />);
    expect(await screen.findByText('SQL Orders')).toBeInTheDocument();
    expect(screen.queryByRole('button', {
      name: 'pages.dataDevelopment.execution.cancel',
    })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {
      name: 'pages.dataDevelopment.execution.retry',
    })).not.toBeInTheDocument();
    expect(cancel).not.toHaveBeenCalled();
    expect(retry).not.toHaveBeenCalled();
  });

  it('keeps closed drawers from requesting detail and resets their displayed state', async () => {
    const { rerender } = render(<ExecutionDetailDrawer open={false}
      record={record('run-1')} onClose={jest.fn()} />);
    expect(get).not.toHaveBeenCalled();
    rerender(<ExecutionDetailDrawer open record={record('run-1')} onClose={jest.fn()} />);
    expect(await screen.findByText('SQL Orders')).toBeInTheDocument();
    rerender(<ExecutionDetailDrawer open={false} record={record('run-1')}
      onClose={jest.fn()} />);
    expect(screen.queryByTestId('detail-drawer')).not.toBeInTheDocument();
  });
});
