import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import OdsPhysicalDeliveryPanel from './OdsPhysicalDeliveryPanel';
import { inspectOdsPhysicalTarget, listOdsTargetObservations, recordOdsTargetObservation } from '@/services/modeling/api';

let mockProjectId = 4;
jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({ canAll: () => true }),
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/services/modeling/api', () => ({
  inspectOdsPhysicalTarget: jest.fn(),
  listOdsTargetObservations: jest.fn(),
  recordOdsTargetObservation: jest.fn(),
}));
jest.mock('@/services/data-source/api', () => ({
  listAllDataSources: jest.fn().mockResolvedValue({
    bizData: [{ id: 20, name: 'Warehouse DEV', dbType: 'MYSQL' }],
  }),
}));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    Select: ({ options, value, onChange }: {
      options?: { value: number; label: string }[];
      value?: number;
      onChange: (value: number) => void;
    }) => <select aria-label="目标数据源" value={value || ''}
      onChange={event => onChange(Number(event.target.value))}>
      <option value="">请选择</option>
      {options?.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>,
  };
});

const model = {
  id: 50, layerCode: 'ODS', latestVersionNo: 2,
  sourceDatasourceId: 10, sourceTable: 'trade_order_detail',
  sourceDatabase: 'trade_db', sourceSchema: 'public',
};

const report = {
  modelId: 50, versionNo: 2, versionId: 90, targetDatasourceId: 20,
  targetDatabase: 'warehouse', targetSchema: 'ods', targetTable: 'ods_order_detail',
  state: 'COLUMN_MATCH_NEEDS_DDL_REVIEW',
  frozenColumns: 2, observedColumns: 2, differences: [], observedCatalogSha256: 'a'.repeat(64),
  limitations: ['仅核验当前 Catalog，不是部署回执'],
};

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 4;
  jest.mocked(inspectOdsPhysicalTarget).mockResolvedValue(report as never);
  jest.mocked(listOdsTargetObservations).mockResolvedValue([]);
});

async function chooseTarget() {
  fireEvent.change(await screen.findByLabelText('目标数据源'), { target: { value: '20' } });
  fireEvent.change(screen.getByPlaceholderText('精确数据库名'), { target: { value: 'warehouse' } });
  fireEvent.change(screen.getByPlaceholderText('如 public'), { target: { value: 'ods' } });
  fireEvent.click(screen.getByRole('button', { name: '读取目标库并核验' }));
}

it('requires real published catalog readback before offering a manual sync handoff', async () => {
  render(<OdsPhysicalDeliveryPanel open model={model} onClose={jest.fn()} />);
  expect(screen.getByText(/建模不执行 DDL，也不自动创建或运行同步任务/)).toBeInTheDocument();
  await chooseTarget();
  await screen.findByText(/目标 Catalog 物理列匹配/);
  expect(inspectOdsPhysicalTarget).toHaveBeenCalledWith(50, 2, 20, 'warehouse', 'ods');
  expect(screen.getAllByText(/不是部署回执/).length).toBeGreaterThanOrEqual(2);
  expect(history.push).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: '前往离线同步配置（需人工建任务）' }))
    .toBeDisabled();
  const saved = {
    id: 701, modelId: 50, versionNo: 2, modelVersionId: 90,
    targetDatasourceId: 20, targetDatabase: 'warehouse', targetSchema: 'ods',
    targetTable: 'ods_order_detail', observedCatalogSha256: 'a'.repeat(64),
    frozenVersionChecksum: 'b'.repeat(64), observedColumnCount: 2,
    observedBy: 'operator', observedAt: '2026-10-15T00:00:00',
    status: 'RECENT_CATALOG_OBSERVATION' as const, limitations: [],
  };
  jest.mocked(recordOdsTargetObservation).mockResolvedValueOnce(saved);
  jest.mocked(listOdsTargetObservations).mockResolvedValueOnce([saved]);
  fireEvent.click(screen.getByRole('button', { name: '保存此次目标列观察记录' }));
  await screen.findByText('701');
  fireEvent.click(screen.getByRole('button', { name: '前往离线同步配置（需人工建任务）' }));
  expect(history.push).toHaveBeenCalledWith(expect.stringContaining('/sync/batch-link-up'));
});

it('never forwards a failed target diff as confirmed deployment', async () => {
  jest.mocked(inspectOdsPhysicalTarget).mockResolvedValueOnce({
    ...report, state: 'STRUCTURE_DIFFERS',
    differences: [{ column: 'order_id', issue: 'MISSING',
      expectedType: 'BIGINT', observedType: null }],
  } as never);
  render(<OdsPhysicalDeliveryPanel open model={model} onClose={jest.fn()} />);
  await chooseTarget();
  await screen.findByText(/字段与冻结版本存在差异/);
  expect(screen.getByRole('button', { name: '前往离线同步配置（需人工建任务）' }))
    .toBeDisabled();
  expect(history.push).not.toHaveBeenCalled();
});

it('requires published version and never inspects a draft model', async () => {
  render(<OdsPhysicalDeliveryPanel open model={{ ...model, latestVersionNo: 0 }} onClose={jest.fn()} />);
  expect(screen.getByText(/请先保存并发布 ODS 版本/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '读取目标库并核验' })).toBeDisabled();
  expect(inspectOdsPhysicalTarget).not.toHaveBeenCalled();
});

it('records only after fresh server validation and verifies an immutable owner history readback', async () => {
  const saved = {
    id: 701, modelId: 50, versionNo: 2, modelVersionId: 90,
    targetDatasourceId: 20, targetDatabase: 'warehouse', targetSchema: 'ods',
    targetTable: 'ods_order_detail', observedCatalogSha256: 'a'.repeat(64),
    frozenVersionChecksum: 'b'.repeat(64), observedColumnCount: 2,
    observedBy: 'operator', observedAt: '2026-10-15T00:00:00',
    status: 'RECENT_CATALOG_OBSERVATION' as const, limitations: [],
  };
  jest.mocked(recordOdsTargetObservation).mockResolvedValueOnce(saved);
  jest.mocked(listOdsTargetObservations).mockResolvedValueOnce([])
    .mockResolvedValueOnce([saved]);
  render(<OdsPhysicalDeliveryPanel open model={model} onClose={jest.fn()} />);
  await chooseTarget();
  await screen.findByText(/目标 Catalog 物理列匹配/);
  fireEvent.click(screen.getByRole('button', { name: '保存此次目标列观察记录' }));
  await screen.findByText('701');
  expect(recordOdsTargetObservation).toHaveBeenCalledWith(50, 2, 20, 'warehouse', 'ods');
  expect(listOdsTargetObservations).toHaveBeenCalledTimes(2);
  expect(screen.getByText(/近期列观察（非部署）/)).toBeInTheDocument();
});

it('never writes an observation when target is mismatched', async () => {
  jest.mocked(inspectOdsPhysicalTarget).mockResolvedValueOnce({
    ...report, state: 'STRUCTURE_DIFFERS',
  } as never);
  render(<OdsPhysicalDeliveryPanel open model={model} onClose={jest.fn()} />);
  await chooseTarget();
  await screen.findByText(/目标字段与冻结版本存在差异/);
  expect(screen.getByRole('button', { name: '保存此次目标列观察记录' }))
    .toBeDisabled();
  expect(recordOdsTargetObservation).not.toHaveBeenCalled();
});
