import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import {
  inspectPublishedSqlRevision, listDevelopmentTaskExecutions, listDevelopmentTaskRevisions,
} from '@/services/data-development';
import DevelopmentTaskDeliveryEvidencePanel from './DevelopmentTaskDeliveryEvidencePanel';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/services/data-development', () => ({
  listDevelopmentTaskExecutions: jest.fn(),
  listDevelopmentTaskRevisions: jest.fn(),
  inspectPublishedSqlRevision: jest.fn(),
}));
const revision = { id: '99', nodeId: '12', revisionNo: 4,
  sourceDraftRevision: 3, checksum: 'b'.repeat(64) };
const latest = { id: '100', nodeId: '12', taskType: 'SQL',
  status: 'SUCCESS', startTime: '2026-10-11T08:00:00' };
beforeEach(() => {
  jest.clearAllMocks();
  (listDevelopmentTaskRevisions as jest.Mock).mockResolvedValue([revision]);
  (inspectPublishedSqlRevision as jest.Mock).mockResolvedValue({
    nodeId: '12', taskRevisionId: '99', taskRevisionNo: 4,
    publishedFromDraftRevision: 3, currentDraftRevision: 4,
    immutableDefinitionSha256: 'b'.repeat(64),
    currentDraftMatchesPublishedDefinition: false,
    status: 'PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED',
    authorizedForFrozenExecution: false,
  });
  (listDevelopmentTaskExecutions as jest.Mock).mockResolvedValue({
    pageNo: 1, pageSize: 100, total: 1, records: [latest],
  });
});
it('labels published task revision and exact-node success run as separate non-binding receipts', async () => {
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText('DataDev 已发布修订 1 个')).toBeInTheDocument();
  expect(screen.getByText('运行 #100')).toBeInTheDocument();
  expect(screen.getByText('SUCCESS')).toBeInTheDocument();
  expect(screen.getByText(/SUCCESS 只表示该次运行成功/)).toBeInTheDocument();
  expect(screen.getByText(/无法据此证明数据已落仓/)).toBeInTheDocument();
  expect(listDevelopmentTaskRevisions).toHaveBeenCalledWith('12');
  expect(listDevelopmentTaskExecutions).toHaveBeenCalledWith({
    pageNo: 1, pageSize: 100, taskType: 'SQL',
  });
});
it('no matching record in limited global SQL page remains an unknown history, never no run', async () => {
  (listDevelopmentTaskExecutions as jest.Mock).mockResolvedValue({
    pageNo: 1, pageSize: 100, total: 300,
    records: [{ ...latest, id: '200', nodeId: '123' }],
  });
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText('当前查询范围没有观察到该节点的运行回执')).toBeInTheDocument();
  expect(screen.getByText(/不是“从未运行”/)).toBeInTheDocument();
  expect(screen.getByText(/历史结果不完整/)).toBeInTheDocument();
});
it('reports revisions and runtime outages independently, never an empty success', async () => {
  (listDevelopmentTaskRevisions as jest.Mock).mockResolvedValue([]);
  (listDevelopmentTaskExecutions as jest.Mock).mockRejectedValue(new Error('运行日志读取权限撤销'));
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText('DataDev 此 SQL 节点尚无可核对的已发布任务修订')).toBeInTheDocument();
  expect(screen.getByText('运行日志读取权限撤销')).toBeInTheDocument();
  expect(screen.queryByText('SUCCESS')).not.toBeInTheDocument();
});
it('rejects foreign revision identity but may still display independently owned execution', async () => {
  (listDevelopmentTaskRevisions as jest.Mock).mockResolvedValue([
    { ...revision, nodeId: '89' },
  ]);
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText('任务已发布版本证据不可用')).toBeInTheDocument();
  expect(screen.getByText(/修订的节点/)).toBeInTheDocument();
  expect(screen.getByText('运行 #100')).toBeInTheDocument();
});
it('discards the previous project and node late result, then re-reads new scope', async () => {
  let resolveOld: (value: unknown) => void = () => {};
  (listDevelopmentTaskRevisions as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }))
    .mockResolvedValue([{ ...revision, nodeId: '19', revisionNo: 5 }]);
  (listDevelopmentTaskExecutions as jest.Mock)
    .mockResolvedValueOnce({ pageNo: 1, pageSize: 100, total: 1, records: [latest] })
    .mockResolvedValue({ pageNo: 1, pageSize: 100, total: 0, records: [] });
  const view = render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  await waitFor(() => expect(listDevelopmentTaskRevisions).toHaveBeenCalledTimes(1));
  view.rerender(<DevelopmentTaskDeliveryEvidencePanel nodeId="19" projectId="8" />);
  await waitFor(() => expect(listDevelopmentTaskRevisions).toHaveBeenCalledTimes(2));
  await act(async () => resolveOld([revision]));
  expect(await screen.findByText('最新修订 V5')).toBeInTheDocument();
  expect(screen.queryByText('最新修订 V4')).not.toBeInTheDocument();
  expect(screen.queryByText('运行 #100')).not.toBeInTheDocument();
});
it('manual refresh only re-reads DataDev evidence and opens official history, never executes SQL', async () => {
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  await screen.findByText('运行 #100');
  fireEvent.click(screen.getByRole('button', { name: '重读任务与运行证据' }));
  await waitFor(() => expect(listDevelopmentTaskRevisions).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole('button', { name: '打开完整运行记录' }));
  expect(history.push).toHaveBeenCalledWith('/data-development/executions');
});

it('shows an owner-verified frozen SQL revision and explicitly calls out current editor drift', async () => {
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText(/TaskRevision #99 · 冻结定义校验通过/)).toBeInTheDocument();
  expect(screen.getByText(/当前编辑器已与冻结修订不同/)).toBeInTheDocument();
  expect(screen.getByText(/未取得 DWD ModelVersion 绑定或冻结执行授权/)).toBeInTheDocument();
  expect(inspectPublishedSqlRevision).toHaveBeenCalledWith('12', 4);
});

it('rejects a forged TaskRevision checksum instead of displaying verified execution readiness', async () => {
  (inspectPublishedSqlRevision as jest.Mock).mockResolvedValue({
    nodeId: '12', taskRevisionId: '99', taskRevisionNo: 4,
    publishedFromDraftRevision: 3, currentDraftRevision: 4,
    immutableDefinitionSha256: 'c'.repeat(64),
    currentDraftMatchesPublishedDefinition: true,
    status: 'PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED',
    authorizedForFrozenExecution: false,
  });
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText('已发布 SQL 修订不可核验')).toBeInTheDocument();
  expect(screen.queryByText(/TaskRevision #99 · 冻结定义校验通过/)).not.toBeInTheDocument();
});

it('rejects a read-only revision report that claims execution authorization', async () => {
  (inspectPublishedSqlRevision as jest.Mock).mockResolvedValue({
    nodeId: '12', taskRevisionId: '99', taskRevisionNo: 4,
    publishedFromDraftRevision: 3, currentDraftRevision: 4,
    immutableDefinitionSha256: 'b'.repeat(64),
    currentDraftMatchesPublishedDefinition: true,
    status: 'PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED',
    authorizedForFrozenExecution: true,
  });
  render(<DevelopmentTaskDeliveryEvidencePanel nodeId="12" projectId="7" />);
  expect(await screen.findByText('已发布 SQL 修订不可核验')).toBeInTheDocument();
});
