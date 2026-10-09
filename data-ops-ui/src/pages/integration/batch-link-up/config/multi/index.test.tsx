import { act, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { message } from 'antd';
import { listAllDataSources } from '@/services/data-source';
import { getOfflineSyncEditDetail } from '@/services/batch-link-up';
import MultiBatchLinkUpDetailPage from './index';

jest.mock('@/services/data-source', () => ({
  listAllDataSources: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
  useParams: () => ({ id: 'task-42' }),
  useLocation: () => ({ search: '' }),
  history: { push: jest.fn(), replace: jest.fn() },
}));

jest.mock('@/services/batch-link-up', () => ({
  getOfflineSyncEditDetail: jest.fn(),
  saveOfflineSyncMultiGuide: jest.fn(),
}));

jest.mock('../../detail/model', () => ({
  normalizeEditDetail: () => ({ mode: 'GUIDE_MULTI' }),
  buildSavePayload: jest.fn(),
}));

jest.mock('../../detail/hooks/useSmoothWheelScroll', () => ({
  useSmoothWheelScroll: jest.fn(),
}));

jest.mock('../../detail/form-schema/validateEditorConnectorForms', () => ({
  __esModule: true,
  default: jest.fn(),
}));

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  const Empty = Object.assign(
    ({ children, description }: { children: ReactNode; description?: ReactNode }) =>
      element('div', null, description, children),
    { PRESENTED_IMAGE_SIMPLE: 'simple' },
  );
  return {
    ConfigProvider: ({ children }: { children: ReactNode }) => element('div', null, children),
    Empty,
    Button: ({ children }: { children: ReactNode }) => element('button', null, children),
    Spin: () => element('div', { 'data-testid': 'task-spinner' }),
    message: { error: jest.fn(), warning: jest.fn(), success: jest.fn() },
  };
});

jest.mock('./components/MultiTableSyncTaskEditor', () => {
  const ReactRuntime = require('react') as typeof import('react');
  const element = ReactRuntime.createElement;
  return {
    __esModule: true,
    default: ({
      dataSources,
      dataSourceLoading,
    }: {
      dataSources: Array<{ id?: string | number; name?: string }>;
      dataSourceLoading: boolean;
    }) => element('div', {
      'data-testid': 'multi-data-sources',
      'data-loading': String(dataSourceLoading),
    }, dataSources.map((source) =>
      element('span', { key: String(source.id) }, source.name),
    )),
  };
});

describe('Multi-table sync editor Data Source legacy migration', () => {
  const listSources = jest.mocked(listAllDataSources);
  const loadTask = jest.mocked(getOfflineSyncEditDetail);

  beforeEach(() => {
    listSources.mockReset();
    loadTask.mockReset();
    jest.mocked(message.error).mockClear();
    loadTask.mockResolvedValue({ mode: 'GUIDE_MULTI' } as never);
  });

  it('loads modern unwrapped bizData into the multi-table editor', async () => {
    listSources.mockResolvedValue({
      bizData: [
        { id: 11, name: 'warehouse' },
        { id: 22, name: 'analytics' },
      ],
      pagination: { pageNo: 1, pageSize: 2, total: 2 },
    });
    render(<MultiBatchLinkUpDetailPage />);

    await waitFor(() => expect(screen.getByText('warehouse')).toBeInTheDocument());
    expect(screen.getByText('analytics')).toBeInTheDocument();
    expect(listSources).toHaveBeenCalledTimes(1);
    expect(loadTask).toHaveBeenCalledWith('task-42');
    expect(screen.getByTestId('multi-data-sources')).toHaveAttribute(
      'data-loading', 'false',
    );
    expect(message.error).not.toHaveBeenCalled();
  });

  it('keeps the data source loading indicator while its request is pending', async () => {
    let finish!: (value: { bizData: Array<{ id: number; name: string }>; pagination: {
      pageNo: number; pageSize: number; total: number;
    } }) => void;
    const pending = new Promise<{
      bizData: Array<{ id: number; name: string }>;
      pagination: { pageNo: number; pageSize: number; total: number };
    }>((resolve) => { finish = resolve; });
    listSources.mockReturnValue(pending);
    render(<MultiBatchLinkUpDetailPage />);

    await waitFor(() => expect(screen.getByTestId('multi-data-sources')).toHaveAttribute(
      'data-loading', 'true',
    ));
    await act(async () => {
      finish({
        bizData: [{ id: 31, name: 'loaded' }],
        pagination: { pageNo: 1, pageSize: 1, total: 1 },
      });
      await pending;
    });
    await waitFor(() => expect(screen.getByText('loaded')).toBeInTheDocument());
    expect(screen.getByTestId('multi-data-sources')).toHaveAttribute(
      'data-loading', 'false',
    );
  });

  it('clears the data source list after a failed modern API read and preserves message', async () => {
    listSources.mockRejectedValue(new Error('获取数据源失败: permission denied'));
    render(<MultiBatchLinkUpDetailPage />);

    await waitFor(() => expect(message.error).toHaveBeenCalledWith(
      '获取数据源失败: permission denied',
    ));
    expect(screen.getByTestId('multi-data-sources')).toHaveAttribute(
      'data-loading', 'false',
    );
    expect(listSources).toHaveBeenCalledTimes(1);
  });

  it('retains valid empty lists without showing an error toast', async () => {
    listSources.mockResolvedValue({
      bizData: [],
      pagination: { pageNo: 1, pageSize: 0, total: 0 },
    });
    render(<MultiBatchLinkUpDetailPage />);

    await waitFor(() => expect(screen.getByTestId('multi-data-sources')).toHaveAttribute(
      'data-loading', 'false',
    ));
    expect(screen.getByTestId('multi-data-sources')).toBeEmptyDOMElement();
    expect(message.error).not.toHaveBeenCalled();
  });

  it('surfaces canonical task detail rejection without presenting an empty success', async () => {
    listSources.mockResolvedValue({
      bizData: [],
      pagination: { pageNo: 1, pageSize: 0, total: 0 },
    });
    loadTask.mockRejectedValue(new Error('任务详情不存在'));
    render(<MultiBatchLinkUpDetailPage />);

    await waitFor(() => expect(message.error).toHaveBeenCalledWith('任务详情不存在'));
    expect(screen.getByText('未找到多表同步任务')).toBeInTheDocument();
    expect(listSources).toHaveBeenCalledTimes(1);
  });
});
