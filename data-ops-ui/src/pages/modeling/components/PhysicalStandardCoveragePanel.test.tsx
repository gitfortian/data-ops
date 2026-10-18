import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PhysicalStandardCoveragePanel from './PhysicalStandardCoveragePanel';
import { inspectPhysicalStandardCoverage } from '@/services/modeling/api';

let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/services/modeling/api', () => ({
  inspectPhysicalStandardCoverage: jest.fn(),
}));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Table: ({ dataSource, columns }: {
    dataSource: { columnId: number; columnName: string; status: string;
      standardReferences?: { referenceKind: string; referenceKey?: string | null;
        standardId?: number | null; status: string; version?: number | null;
        explanation: string }[] }[];
    columns: { key?: string; dataIndex?: string; render?: (_: unknown, row: {
      columnId: number; columnName: string; status: string;
      standardReferences?: { referenceKind: string; referenceKey?: string | null;
        standardId?: number | null; status: string; version?: number | null;
        explanation: string }[];
    }) => React.ReactNode }[];
  }) => <div>{dataSource.map(row => <div key={row.columnId}>
    {row.columnName} : {row.status}
    {columns.find(col => col.key === 'action')?.render?.(undefined, row)}
  </div>)}</div> };
});

const report = {
  modelId: 50, modelCode: 'dwd_orders', layerCode: 'DWD',
  modelStatus: 'DRAFT', processId: 22, processName: '下单',
  status: 'NEEDS_REVIEW', structureSha256: 'a'.repeat(64),
  evidenceSha256: 'b'.repeat(64), columnCount: 2, enabledCount: 1,
  missingCount: 1, invalidCount: 0, reviewCount: 0, standardReferenceIssueCount: 1,
  columns: [
    { columnId: 1, columnName: 'order_id', dataType: 'BIGINT',
      stdFieldId: 11, standardName: '订单标识',
      status: 'ENABLED_NOW', explanation: '当前可用',
      standardReferences: [
        { referenceKind: 'TYPE', referenceKey: '101', standardId: 101, status: 'ENABLED_NOW',
          version: 3, contentSha256: 'c'.repeat(64), explanation: '类型标准当前启用' },
        { referenceKind: 'UNIT', referenceKey: '102', standardId: 102, status: 'DISABLED',
          version: 2, contentSha256: 'd'.repeat(64), explanation: '单位标准已停用' },
      ] },
    { columnId: 2, columnName: 'process_time', dataType: 'TIMESTAMP',
      stdFieldId: null, status: 'MISSING_STANDARD', explanation: '尚未批准技术列例外',
      standardReferences: [] },
  ],
  findings: ['未关联正式标准：process_time'],
  limitations: ['不改变现行发布资格'],
};

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 1;
});

it('shows saved DWD standard gaps without writing or treating technical columns as exempt', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue(report);
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('未关联正式标准：process_time')).toBeInTheDocument();
  expect(screen.getByText(/process_time : MISSING_STANDARD/)).toBeInTheDocument();
  expect(screen.getByText('不改变现行发布资格')).toBeInTheDocument();
  expect(inspectPhysicalStandardCoverage).toHaveBeenCalledWith(50);
});


it('shows current referenced standard versions and disabled references as read-only evidence', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue(report);
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('TYPE v3 · ENABLED_NOW')).toBeInTheDocument();
  expect(screen.getByText('UNIT v2 · DISABLED')).toBeInTheDocument();
  expect(screen.getByText('引用标准异常 1')).toBeInTheDocument();
  expect(screen.getByText('不改变现行发布资格')).toBeInTheDocument();
});

it('does not surface late report after selected project changes', async () => {
  let finish: (value: unknown) => void = () => {};
  (inspectPhysicalStandardCoverage as jest.Mock)
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValueOnce({ ...report, findings: ['新项目需复核标准字段'] });
  const view = render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  mockProjectId = 2;
  view.rerender(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  await waitFor(() => expect(inspectPhysicalStandardCoverage).toHaveBeenCalledTimes(2));
  await act(async () => finish(report));
  expect(await screen.findByText('新项目需复核标准字段')).toBeInTheDocument();
  expect(screen.queryByText('未关联正式标准：process_time')).not.toBeInTheDocument();
});

it('only allows explicit navigation for governance gaps; never writes standards from report', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue(report);
  const onLocateColumn = jest.fn();
  render(<PhysicalStandardCoveragePanel modelId={50} open
    onClose={jest.fn()} onLocateColumn={onLocateColumn} />);
  const buttons = await screen.findAllByRole('button', { name: '定位当前物理列' });
  expect(buttons).toHaveLength(1);
  fireEvent.click(buttons[0]);
  expect(onLocateColumn).toHaveBeenCalledTimes(1);
  expect(onLocateColumn.mock.calls[0][0]).toMatchObject({
    columnId: 2, columnName: 'process_time', status: 'MISSING_STANDARD',
  });
  expect(onLocateColumn.mock.calls[0][1]).toMatchObject({ modelId: 50, evidenceSha256: report.evidenceSha256 });
});

it('explicit recheck replaces stale evidence and compares pre-repair to current counts', async () => {
  const fresh = { ...report, evidenceSha256: 'c'.repeat(64),
    missingCount: 0, enabledCount: 2, findings: ['已重新读取当前标准状态'] };
  (inspectPhysicalStandardCoverage as jest.Mock)
    .mockResolvedValueOnce(report).mockResolvedValueOnce(fresh);
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()}
    baseline={{ modelId: 50, evidenceSha256: report.evidenceSha256,
      missingCount: 1, invalidCount: 0, reviewCount: 0 }} />);
  expect(await screen.findByText('已重新核验：证据尚未变化')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取当前治理证据' }));
  expect(await screen.findByText('已重新读取当前标准状态')).toBeInTheDocument();
  expect(screen.getByText('已重新核验：本次证据与修正前不同')).toBeInTheDocument();
  expect(screen.getByText(/缺少标准 1→0/)).toBeInTheDocument();
  expect(screen.queryByText('未关联正式标准：process_time')).not.toBeInTheDocument();
  expect(inspectPhysicalStandardCoverage).toHaveBeenCalledTimes(2);
});

it('rejects a stale wrong-model receipt rather than displaying old successful findings', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue({ ...report, modelId: 999 });
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('本次治理报告不可用')).toBeInTheDocument();
  expect(screen.queryByText('未关联正式标准：process_time')).not.toBeInTheDocument();
});

it('does not interpret an owner read failure as empty or compliant standard evidence', async () => {
  (inspectPhysicalStandardCoverage as jest.Mock).mockRejectedValue(new Error('标准权限已撤销'));
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('标准权限已撤销')).toBeInTheDocument();
  expect(screen.queryByText('当前模型所有字段已关联可用标准')).not.toBeInTheDocument();
});


it('labels DWS evidence as read-only and does not imply grain or MetricVersionRef passed', async () => {
  const dws = {
    ...report, layerCode: 'DWS', processId: null, processName: null,
    status: 'COVERED_NOW', findings: [],
    limitations: ['DWS 粒度/上游版本/MetricVersionRef 尚未核验'],
  };
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue(dws);
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('DWS 当前字段标准覆盖与治理缺口（只读）')).toBeInTheDocument();
  expect(screen.getByText('分层专项合同：需单独核验')).toBeInTheDocument();
  expect(screen.getByText(/不核验 DWS 粒度\/上游版本\/MetricVersionRef/)).toBeInTheDocument();
  expect(screen.getByText('DWS 粒度/上游版本/MetricVersionRef 尚未核验')).toBeInTheDocument();
});

it('labels ADS evidence as read-only and does not imply consumer contract passed', async () => {
  const ads = {
    ...report, layerCode: 'ADS', processId: null, processName: null,
    status: 'COVERED_NOW', findings: [],
    limitations: ['ADS 消费合同/脱敏/消费者授权/Usage Evidence 尚未核验'],
  };
  (inspectPhysicalStandardCoverage as jest.Mock).mockResolvedValue(ads);
  render(<PhysicalStandardCoveragePanel modelId={50} open onClose={jest.fn()} />);
  expect(await screen.findByText('ADS 当前字段标准覆盖与治理缺口（只读）')).toBeInTheDocument();
  expect(screen.getByText('分层专项合同：需单独核验')).toBeInTheDocument();
  expect(screen.getByText(/不核验 ADS 消费合同\/脱敏\/消费者授权/)).toBeInTheDocument();
  expect(screen.getByText('ADS 消费合同/脱敏/消费者授权/Usage Evidence 尚未核验')).toBeInTheDocument();
});
