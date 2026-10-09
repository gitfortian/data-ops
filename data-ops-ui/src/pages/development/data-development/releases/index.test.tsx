import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { message } from 'antd';
import {
  activateDevelopmentReleaseRevision,
  getDevelopmentRelease,
  listDevelopmentReleases,
  offlineDevelopmentRelease,
  onlineDevelopmentRelease,
  type DevelopmentReleaseDetail,
  type DevelopmentReleasePage,
  type DevelopmentReleaseSummary,
  type DevelopmentTaskRevision,
  type DevelopmentTaskRevisionSummary,
} from '@/services/data-development';
import { WorkspaceLoadFailureState } from '../components/WorkspaceStateFeedback';
import ReleaseCenterPage from './index';

jest.mock('@/services/data-development', () => ({
  activateDevelopmentReleaseRevision: jest.fn(),
  getDevelopmentRelease: jest.fn(),
  listDevelopmentReleases: jest.fn(),
  offlineDevelopmentRelease: jest.fn(),
  onlineDevelopmentRelease: jest.fn(),
}));

const mockIntl = {
  locale: 'zh-CN',
  formatMessage: ({ id }: { id: string }) => id,
};
let mockCanRelease = true;
jest.mock('@umijs/max', () => ({
  useIntl: () => mockIntl,
  useAccess: () => ({ hasPermission: () => mockCanRelease }),
  history: { push: jest.fn() },
}));

jest.mock('@/components/YakButton', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return { __esModule: true, default: ({
    children, onClick,
  }: { children: React.ReactNode; onClick?: () => void }) =>
    ReactRuntime.createElement('button', { onClick }, children) };
});

jest.mock('@/components/ReadableTable', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const e = ReactRuntime.createElement;
  return {
    __esModule: true,
    default: ({ dataSource, columns, loading }: {
      dataSource: Array<Record<string, unknown>>;
      columns: Array<{ key?: string; dataIndex?: string;
        render?: (value: unknown, record: Record<string, unknown>) => React.ReactNode }>;
      loading: boolean;
    }) => e('div', {
      'data-testid': 'release-list',
      'data-loading': String(loading),
    }, dataSource.map((row) => e('div', { key: String(row.assetId) },
      columns.filter(col => col.dataIndex === 'taskName' || col.key === 'action')
        .map((col, index) => e('div', { key: index },
          col.render ? col.render(col.dataIndex ? row[col.dataIndex] : undefined, row) : null,
        )),
    ))),
  };
});

jest.mock('@/components/ui', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const e = ReactRuntime.createElement;
  return { YakFilterSwitch: ({ options, onChange }: {
    options: Array<{ value: string }>;
    onChange: (value: string) => void;
  }) => e('div', null, options.map((option) => e('button', {
    key: option.value, onClick: () => onChange(option.value),
  }, 'filter-' + option.value))) };
});

jest.mock('../components/WorkspaceStateFeedback', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return { WorkspaceLoadFailureState: jest.fn(({ onRetry }: {
    onRetry: () => void;
  }) => ReactRuntime.createElement('button', {
    onClick: onRetry,
  }, '重试发布列表')) };
});

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const e = ReactRuntime.createElement;
  const Empty = Object.assign(({ description }: {
    description?: React.ReactNode;
  }) => e('div', null, description), { PRESENTED_IMAGE_SIMPLE: 'simple' });
  const Descriptions = Object.assign(
    ({ children }: { children: React.ReactNode }) => e('dl', null, children),
    { Item: ({ label, children }: {
      label: React.ReactNode; children: React.ReactNode;
    }) => e('div', null, e('dt', null, label), e('dd', null, children)) },
  );
  return {
    ConfigProvider: ({ children }: { children: React.ReactNode }) => e('div', null, children),
    Button: ({ children, onClick, loading }: {
      children: React.ReactNode; onClick?: () => void; loading?: boolean;
    }) => e('button', { onClick, disabled: loading }, children),
    Descriptions,
    Drawer: ({ open, children, extra }: {
      open: boolean; children: React.ReactNode; extra: React.ReactNode;
    }) => open ? e('div', { 'data-testid': 'release-detail' }, extra, children) : null,
    Empty,
    Input: ({ value, onChange, onPressEnter }: {
      value: string;
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
      onPressEnter?: () => void;
    }) => e('input', {
      'data-testid': 'release-search',
      value, onChange,
      onKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') onPressEnter?.();
      },
    }),
    Pagination: ({ current, total, pageSize, onChange }: {
      current: number; total: number; pageSize: number;
      onChange: (page: number, size: number) => void;
    }) => e('button', {
      'data-testid': 'release-next',
      'data-total': String(total),
      onClick: () => onChange(current + 1, pageSize),
    }, '下一页'),
    Popconfirm: ({ children, onConfirm }: {
      children: React.ReactNode; onConfirm: () => void;
    }) => e('span', { onClick: () => { void onConfirm(); } }, children),
    Select: ({ placeholder, options, onChange }: {
      placeholder: string;
      options?: Array<{ value: string; label: string }>;
      onChange: (value: string | undefined) => void;
    }) => e('select', {
      'aria-label': placeholder,
      onChange: (event: React.ChangeEvent<HTMLSelectElement>) =>
        onChange(event.target.value || undefined),
    }, [e('option', { key: '', value: '' }, '全部'),
      ...(options || []).map(opt => e('option', {
        key: opt.value, value: opt.value,
      }, opt.label))]),
    Spin: () => e('span', { 'data-testid': 'release-loading' }, '加载中'),
    Table: ({ dataSource, columns }: {
      dataSource: Array<Record<string, unknown>>;
      columns: Array<{ title?: string; dataIndex?: string;
        render?: (value: unknown, record: Record<string, unknown>) => React.ReactNode }>;
    }) => e('div', { 'data-testid': 'release-history' },
      dataSource.map(row => e('div', { key: String(row.id) },
        columns.filter(col => col.dataIndex === 'revisionNo'
          || col.title === 'pages.dataDevelopment.common.action').map((col, i) =>
          e('div', { key: i },
            col.render ? col.render(col.dataIndex ? row[col.dataIndex] : undefined, row) : null,
          )),
      ))),
    Tooltip: ({ children }: { children: React.ReactNode }) => e('span', null, children),
    message: { error: jest.fn(), success: jest.fn() },
  };
});

const release = (status: DevelopmentReleaseSummary['status'] = 'ONLINE'):
  DevelopmentReleaseSummary => ({
  assetId: 'asset-1', nodeId: 'node-1', taskName: 'Published SQL',
  taskType: 'SQL', status, currentRevisionId: 'rev-1',
  currentRevisionNo: 1, latestRevisionNo: 2, hasNewerRevision: true,
  checksum: 'abcdef123456', 
});

const revision = (revisionNo: number): DevelopmentTaskRevisionSummary => ({
  id: 'rev-' + revisionNo,
  nodeId: 'node-1',
  revisionNo,
  sourceDraftRevision: 4,
  checksum: 'abc123',
});

const releaseDetail = (status: DevelopmentReleaseSummary['status'] = 'ONLINE'):
  DevelopmentReleaseDetail => ({
  release: release(status),
  currentRevision: {
    ...revision(1),
    definition: {
      taskType: 'SQL', schemaVersion: 1,
      content: 'SELECT approved', configJson: '{"readOnly":true}',
    },
  } as DevelopmentTaskRevision,
  revisions: [revision(1), revision(2)],
});

const page = (records: DevelopmentReleaseSummary[]): DevelopmentReleasePage => ({
  records,
  total: records.length,
  pageNo: 1, pageSize: 20, onlineCount: records.filter(x => x.status === 'ONLINE').length,
  offlineCount: records.filter(x => x.status === 'OFFLINE').length,
  disabledCount: records.filter(x => x.status === 'DISABLED').length,
});

describe('Data Development Release Center modern API migration', () => {
  const list = jest.mocked(listDevelopmentReleases);
  const getDetail = jest.mocked(getDevelopmentRelease);
  const offline = jest.mocked(offlineDevelopmentRelease);
  const online = jest.mocked(onlineDevelopmentRelease);
  const activate = jest.mocked(activateDevelopmentReleaseRevision);
  const error = jest.mocked(message.error);
  const success = jest.mocked(message.success);

  beforeEach(() => {
    mockCanRelease = true;
    list.mockReset();
    getDetail.mockReset();
    offline.mockReset();
    online.mockReset();
    activate.mockReset();
    error.mockReset();
    success.mockReset();
    jest.mocked(WorkspaceLoadFailureState).mockClear();
    list.mockResolvedValue(page([release()]));
    getDetail.mockResolvedValue(releaseDetail());
    offline.mockResolvedValue(release('OFFLINE'));
    online.mockResolvedValue(release('ONLINE'));
    activate.mockResolvedValue(release('ONLINE'));
  });

  it('renders unwrapped releases with server totals and unchanged list query', async () => {
    render(<ReleaseCenterPage />);
    expect(await screen.findByRole('button', { name: 'Published SQL' })).toBeInTheDocument();
    expect(screen.getByTestId('release-next')).toHaveAttribute('data-total', '1');
    expect(list).toHaveBeenCalledWith(expect.objectContaining({
      pageNo: 1, pageSize: 20, status: 'ALL',
    }));
    expect(error).not.toHaveBeenCalled();
  });

  it('preserves search keyword trimming, status filtering and server pagination', async () => {
    render(<ReleaseCenterPage />);
    await screen.findByRole('button', { name: 'Published SQL' });
    fireEvent.change(screen.getByTestId('release-search'), { target: { value: '  SQL  ' } });
    fireEvent.keyDown(screen.getByTestId('release-search'), { key: 'Enter' });
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: 'SQL', pageNo: 1,
    })));
    fireEvent.click(screen.getByRole('button', { name: 'filter-OFFLINE' }));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      status: 'OFFLINE', pageNo: 1,
    })));
    fireEvent.click(screen.getByTestId('release-next'));
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({
      status: 'OFFLINE', pageNo: 2,
    })));
  });

  it('retains successful empty release lists without fabricating a failure', async () => {
    list.mockResolvedValue(page([]));
    render(<ReleaseCenterPage />);
    await waitFor(() => expect(screen.getByTestId('release-list'))
      .toHaveAttribute('data-loading', 'false'));
    expect(screen.getByTestId('release-list')).toBeEmptyDOMElement();
    expect(screen.getByTestId('release-next')).toHaveAttribute('data-total', '0');
    expect(WorkspaceLoadFailureState).not.toHaveBeenCalled();
  });

  it('preserves load failure and the user retry path', async () => {
    list.mockRejectedValueOnce(new Error('Project list forbidden'))
      .mockResolvedValueOnce(page([release()]));
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', { name: '重试发布列表' }));
    expect(await screen.findByRole('button', { name: 'Published SQL' })).toBeInTheDocument();
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('opens the published revision snapshot from the unwrapped detail API', async () => {
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Published SQL' }));
    expect(await screen.findByText('SELECT approved')).toBeInTheDocument();
    expect(screen.getByText('{"readOnly":true}')).toBeInTheDocument();
    expect(getDetail).toHaveBeenCalledWith('asset-1');
    expect(screen.getByTestId('release-history')).toBeInTheDocument();
  });

  it('keeps the explicit offline confirmation and refreshes only after durable success', async () => {
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.release.offline',
    }));
    await waitFor(() => expect(offline).toHaveBeenCalledWith('asset-1'));
    await waitFor(() => expect(success).toHaveBeenCalledWith(
      'pages.dataDevelopment.release.offlineSuccess',
    ));
    expect(list).toHaveBeenCalledTimes(2);
  });

  it('keeps online confirmation and does not alter the activation revision', async () => {
    list.mockResolvedValue(page([release('OFFLINE')]));
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.release.confirmOnline',
    }));
    await waitFor(() => expect(online).toHaveBeenCalledWith('asset-1'));
    expect(success).toHaveBeenCalledWith('pages.dataDevelopment.release.reonlineSuccess');
    expect(activate).not.toHaveBeenCalled();
  });

  it('activates the explicitly selected revision only after opening release detail', async () => {
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Published SQL' }));
    expect(await screen.findByText('SELECT approved')).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.release.switchVersion',
    }));
    await waitFor(() => expect(activate).toHaveBeenCalledWith('asset-1', 2));
    await waitFor(() => expect(success).toHaveBeenCalledWith(
      'pages.dataDevelopment.release.switched',
    ));
    expect(getDetail).toHaveBeenCalledTimes(2);
  });

  it('blocks success toasts and refresh when the modern release command returns no result', async () => {
    offline.mockResolvedValue(null as never);
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', {
      name: 'pages.dataDevelopment.release.offline',
    }));
    await waitFor(() => expect(error).toHaveBeenCalledWith(
      'pages.dataDevelopment.release.offlineFailed',
    ));
    expect(success).not.toHaveBeenCalled();
    expect(list).toHaveBeenCalledTimes(1);
  });

  it('preserves read-only permission gating for release mutations', async () => {
    mockCanRelease = false;
    render(<ReleaseCenterPage />);
    await screen.findByRole('button', { name: 'Published SQL' });
    expect(screen.getByText('pages.dataDevelopment.releaseExperience.permissionHint'))
      .toBeInTheDocument();
    expect(screen.queryByRole('button', {
      name: 'pages.dataDevelopment.release.offline',
    })).not.toBeInTheDocument();
    expect(offline).not.toHaveBeenCalled();
    expect(online).not.toHaveBeenCalled();
    expect(activate).not.toHaveBeenCalled();
  });

  it('keeps detail source unavailable explicitly retryable rather than inventing a revision', async () => {
    getDetail.mockRejectedValue(new Error('Revision history unavailable'));
    render(<ReleaseCenterPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Published SQL' }));
    await waitFor(() => expect(getDetail).toHaveBeenCalledWith('asset-1'));
    expect(await screen.findByRole('button', { name: '重试发布列表' }))
      .toBeInTheDocument();
    expect(screen.queryByText('SELECT approved')).not.toBeInTheDocument();
  });
});
