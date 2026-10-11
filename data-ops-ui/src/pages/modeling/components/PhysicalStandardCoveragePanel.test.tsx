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
    dataSource: { columnId: number; columnName: string; status: string }[];
    columns: { key?: string; render?: (_: unknown, row: {
      columnId: number; columnName: string; status: string;
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
  missingCount: 1, invalidCount: 0, reviewCount: 0,
  columns: [
    { columnId: 1, columnName: 'order_id', dataType: 'BIGINT',
      stdFieldId: 11, standardName: '订单标识',
      status: 'ENABLED_NOW', explanation: '当前可用' },
    { columnId: 2, columnName: 'process_time', dataType: 'TIMESTAMP',
      stdFieldId: null, status: 'MISSING_STANDARD', explanation: '尚未批准技术列例外' },
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
});
