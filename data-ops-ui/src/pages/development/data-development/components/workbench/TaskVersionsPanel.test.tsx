import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import { message } from 'antd';
import {
  getDevelopmentTaskRevision,
  listDevelopmentTaskRevisions,
  type DevelopmentTaskRevision,
  type DevelopmentTaskRevisionSummary,
} from '@/services/data-development';
import {
  getDevelopmentLineageEvidence,
  type DevelopmentLineageEvidence,
} from '../../governanceEvidence';
import type { DevelopmentNode } from '../../types';
import TaskVersionsPanel from './TaskVersionsPanel';

jest.mock('@/services/data-development', () => ({
  listDevelopmentTaskRevisions: jest.fn(),
  getDevelopmentTaskRevision: jest.fn(),
}));

jest.mock('../../governanceEvidence', () => ({
  ...jest.requireActual('../../governanceEvidence'),
  getDevelopmentLineageEvidence: jest.fn(),
}));

jest.mock('@umijs/max', () => ({
  useIntl: () => ({
    locale: 'zh-CN',
    formatMessage: ({ id }: { id: string }) => id,
  }),
  history: { push: jest.fn() },
}));

jest.mock('antd', () => {
  const ReactRuntime = require('react') as typeof import('react');
  return {
    Spin: () => ReactRuntime.createElement('span', {
      'data-testid': 'revision-spinner',
    }, '加载中'),
    Button: ({ children, onClick }: {
      children: React.ReactNode;
      onClick?: () => void;
    }) => ReactRuntime.createElement('button', { onClick }, children),
    message: { error: jest.fn() },
  };
});

const node = (id: string): DevelopmentNode => ({
  id, name: 'SQL 节点', type: 'SQL', configured: true,
});

const revision = (nodeId: string, revisionNo: number): DevelopmentTaskRevisionSummary => ({
  id: nodeId + '-v' + revisionNo,
  nodeId,
  revisionNo,
  sourceDraftRevision: 7,
  checksum: 'abcdef12345678901234',
});

const detail = (nodeId: string, revisionNo: number): DevelopmentTaskRevision => ({
  ...revision(nodeId, revisionNo),
  definition: {
    taskType: 'SQL',
    schemaVersion: 1,
    content: 'SELECT 42 AS answer',
    configJson: '{}',
  },
});

const lineage = (nodeId: string, revisionNo: number): DevelopmentLineageEvidence => ({
  nodeId,
  revisionId: nodeId + '-v' + revisionNo,
  revisionNo,
  status: 'SUCCEEDED',
  lineageAssetKey: 'sql:orders',
  attempts: 1,
});

describe('TaskVersionsPanel Data Development API migration', () => {
  const list = jest.mocked(listDevelopmentTaskRevisions);
  const getRevision = jest.mocked(getDevelopmentTaskRevision);
  const getLineage = jest.mocked(getDevelopmentLineageEvidence);
  const toast = jest.mocked(message.error);
  const navigate = jest.mocked(history.push);

  beforeEach(() => {
    list.mockReset();
    getRevision.mockReset();
    getLineage.mockReset();
    toast.mockReset();
    navigate.mockReset();
    list.mockResolvedValue([revision('sql-1', 2), revision('sql-1', 1)]);
    getRevision.mockResolvedValue(detail('sql-1', 2));
    getLineage.mockResolvedValue({ code: 200, data: lineage('sql-1', 2) });
  });

  it('renders data-only revision rows and preserves latest marker and checksum', async () => {
    render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    expect(await screen.findByText('v2')).toBeInTheDocument();
    expect(screen.getByText('v1')).toBeInTheDocument();
    expect(screen.getByText('abcdef1234')).toBeInTheDocument();
    expect(screen.getByText('pages.dataDevelopment.versions.latest')).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith('sql-1');
    expect(toast).not.toHaveBeenCalled();
  });

  it('shows the existing empty-state messages for an empty successful list', async () => {
    list.mockResolvedValue([]);
    render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    expect(await screen.findByText('pages.dataDevelopment.versions.empty')).toBeInTheDocument();
    expect(screen.getByText('pages.dataDevelopment.versions.emptyHint')).toBeInTheDocument();
    expect(getRevision).not.toHaveBeenCalled();
  });

  it('keeps the server failure toast and leaves the version list empty', async () => {
    list.mockRejectedValue(new Error('没有版本读取权限'));
    render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    await waitFor(() => expect(toast).toHaveBeenCalledWith('没有版本读取权限'));
    expect(await screen.findByText('pages.dataDevelopment.versions.empty')).toBeInTheDocument();
  });

  it('loads the selected detail directly and keeps the separate Lineage envelope contract', async () => {
    render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    fireEvent.click(await screen.findByText('v2'));

    await waitFor(() => expect(getRevision).toHaveBeenCalledWith('sql-1', 2));
    expect(await screen.findByText('SELECT 42 AS answer')).toBeInTheDocument();
    expect(screen.getByText('Draft #7')).toBeInTheDocument();
    expect(getLineage).toHaveBeenCalledWith('sql-1', 2);
    expect(await screen.findByText('Lineage Evidence 已就绪')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '打开 Lineage' }));
    expect(navigate).toHaveBeenCalledWith(
      '/data-analysis/lineage?assetKey=sql%3Aorders&returnTo=%2Fdata-development%3FnodeId%3Dsql-1',
    );
  });

  it('keeps revision detail error feedback without mislabeling a failure as a version', async () => {
    getRevision.mockRejectedValue(new Error('历史版本已清理'));
    render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    fireEvent.click(await screen.findByText('v2'));

    await waitFor(() => expect(toast).toHaveBeenCalledWith('历史版本已清理'));
    expect(screen.queryByText('SELECT 42 AS answer')).not.toBeInTheDocument();
    expect(getLineage).not.toHaveBeenCalled();
  });

  it('preserves explicit Lineage unavailable when the separate envelope rejects a read', async () => {
    getLineage.mockResolvedValue({
      code: 500, message: '血缘证据来源不可用',
    } as never);
    render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    fireEvent.click(await screen.findByText('v2'));

    expect(await screen.findByText('Lineage Evidence 不可用')).toBeInTheDocument();
    expect(screen.getByText('血缘证据来源不可用')).toBeInTheDocument();
    expect(screen.getByText('读取失败不能解释为“没有血缘”。')).toBeInTheDocument();
    expect(screen.getByText('SELECT 42 AS answer')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '打开 Lineage' })).not.toBeInTheDocument();
  });

  it('ignores a stale revision-list response after the node selection changes', async () => {
    let finish!: (rows: DevelopmentTaskRevisionSummary[]) => void;
    const pending = new Promise<DevelopmentTaskRevisionSummary[]>(resolve => {
      finish = resolve;
    });
    list.mockImplementation(id => id === 'sql-1'
      ? pending : Promise.resolve([revision('sql-2', 5)]));
    const { rerender } = render(<TaskVersionsPanel node={node('sql-1')} refreshKey={0} />);
    rerender(<TaskVersionsPanel node={node('sql-2')} refreshKey={0} />);

    expect(await screen.findByText('v5')).toBeInTheDocument();
    await act(async () => {
      finish([revision('sql-1', 2)]);
      await pending;
    });
    expect(screen.getByText('v5')).toBeInTheDocument();
    expect(screen.queryByText('v2')).not.toBeInTheDocument();
  });

  it('refetches versions on the requested refreshKey without changing data shape', async () => {
    list.mockResolvedValueOnce([revision('sql-1', 2)])
      .mockResolvedValueOnce([revision('sql-1', 3)]);
    const { rerender } = render(
      <TaskVersionsPanel node={node('sql-1')} refreshKey={0} />,
    );
    expect(await screen.findByText('v2')).toBeInTheDocument();
    rerender(<TaskVersionsPanel node={node('sql-1')} refreshKey={1} />);
    expect(await screen.findByText('v3')).toBeInTheDocument();
    expect(screen.queryByText('v2')).not.toBeInTheDocument();
    expect(list).toHaveBeenCalledTimes(2);
  });
});
