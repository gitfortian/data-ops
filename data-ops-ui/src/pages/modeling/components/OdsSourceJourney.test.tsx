import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { commitOdsManualImport, getModelingStructure } from '@/services/modeling/api';
import { listModelingMappings } from '@/services/modeling/mapping';
import { previewModelingImportColumns } from '@/services/modeling/import';
import OdsSourceJourney from './OdsSourceJourney';

let mockQuery = new URLSearchParams({
  sourceJourney: 'ods', datasourceId: '10', database: 'trade_db',
  schema: 'public', table: 'orders',
});
let mockProjectId = 42;
const mockPush = jest.fn();
const mockCandidates = jest.fn();
const mockSources = jest.fn();
const mockGetModel = jest.fn();
const mockCreateModel = jest.fn();

jest.mock('@umijs/max', () => ({
  history: { push: (...args: unknown[]) => mockPush(...args), replace: jest.fn() },
  useSearchParams: () => [mockQuery, jest.fn()],
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true,
  default: () => ({
    can: (permission: string) => ['modeling:read', 'modeling:create', 'modeling:update'].includes(permission),
  }),
}));
jest.mock('@/services/data-source/api', () => ({
  listAllDataSources: (...args: unknown[]) => mockSources(...args),
}));
jest.mock('@/services/metadata/api', () => ({
  getEntityDetail: jest.fn(),
}));
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: (...args: unknown[]) => mockGetModel(...args),
  createModelingModel: (...args: unknown[]) => mockCreateModel(...args),
  getModelingStructure: jest.fn(), commitOdsManualImport: jest.fn(),
}));
jest.mock('@/services/modeling/mapping', () => ({ listModelingMappings: jest.fn() }));
jest.mock('@/services/modeling/import', () => ({
  getOdsSourceCandidates: (...args: unknown[]) => mockCandidates(...args),
  previewModelingImportColumns: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 42;
  mockQuery = new URLSearchParams({
    sourceJourney: 'ods', datasourceId: '10',
    database: 'trade_db', schema: 'public', table: 'orders',
  });
  mockSources.mockResolvedValue({ bizData: [{ id: 10, dbType: 'MYSQL' }] });
  mockCandidates.mockResolvedValue({
    state: 'READY', reason: 'found', datasourceId: 10, database: 'trade_db',
    schema: 'public', table: 'orders',
    candidates: [{ modelId: 501, code: 'ods_orders', name: '订单 ODS',
      status: 'DRAFT', sourceDatabase: 'trade_db', sourceSchema: 'public',
      sourceTable: 'orders' }],
  });
});

it('opens an existing precise ODS model without creating or mutating it; Asset write is not required', async () => {
  render(<OdsSourceJourney />);
  expect(await screen.findByRole('button', { name: '打开已保存 ODS' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '打开已保存 ODS' }));
  expect(mockPush).toHaveBeenCalledWith('/modeling/models/501');
  expect(mockCreateModel).not.toHaveBeenCalled();
  expect(mockCandidates).toHaveBeenCalledWith({
    datasourceId: 10, database: 'trade_db', schema: 'public', table: 'orders',
  });
});

it('does not treat unverified or unowned source as an empty ODS candidate list', async () => {
  mockCandidates.mockResolvedValueOnce({
    state: 'UNVERIFIED', reason: 'legacy schema unknown',
    datasourceId: 10, database: 'trade_db', schema: 'public', table: 'orders',
    candidates: [],
  });
  render(<OdsSourceJourney />);
  expect(await screen.findByText('不能确认来源身份')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
  expect(mockCreateModel).not.toHaveBeenCalled();
});

it('rejects missing project source evidence instead of offering a new model', async () => {
  mockSources.mockResolvedValueOnce({ bizData: [{ id: 99, dbType: 'MYSQL' }] });
  render(<OdsSourceJourney />);
  await waitFor(() =>
    expect(screen.getByText(/当前项目无法核验来源数据源/)).toBeInTheDocument());
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
});

it('returns to the exact live source table rather than forcing Asset catalog after direct source entry', async () => {
  mockQuery = new URLSearchParams({
    sourceJourney: 'ods', origin: 'live-source', datasourceId: '10',
    database: 'trade_db', schema: 'public', table: 'orders',
  });
  render(<OdsSourceJourney />);
  const returnButton = await screen.findByRole('button', { name: '返回数据源表结构' });
  fireEvent.click(returnButton);
  const expected = new URLSearchParams({
    sourceId: '10', database: 'trade_db', schema: 'public', table: 'orders',
  });
  // Query parameter ordering is not an identity contract.
  expect(mockPush).toHaveBeenCalledTimes(1);
  const returnedUrl = mockPush.mock.calls[0][0] as string;
  expect(returnedUrl.split('?')[0]).toBe('/data-source');
  expect(Object.fromEntries(new URLSearchParams(returnedUrl.split('?')[1]).entries()))
    .toEqual(Object.fromEntries(expected.entries()));
  expect(mockCandidates).toHaveBeenCalledWith({
    datasourceId: 10, database: 'trade_db', schema: 'public', table: 'orders',
  });
});

it('does not trust an arbitrary return URL; legacy Asset navigation still has its original destination', async () => {
  mockQuery.set('returnTo', 'https://attacker.example');
  render(<OdsSourceJourney />);
  const button = await screen.findByRole('button', { name: '返回来源物理表' });
  fireEvent.click(button);
  expect(mockPush).toHaveBeenCalledWith(expect.stringContaining('/data-asset/catalog?'));
  expect(mockPush).not.toHaveBeenCalledWith('https://attacker.example');
});

it('passes live source context to an existing ODS model detail for verified return', async () => {
  mockQuery = new URLSearchParams({
    sourceJourney: 'ods', origin: 'live-source', datasourceId: '10',
    database: 'trade_db', schema: 'public', table: 'orders',
  });
  render(<OdsSourceJourney />);
  fireEvent.click(await screen.findByRole('button', { name: '打开已保存 ODS' }));
  expect(mockPush).toHaveBeenCalledWith(
    `/modeling/models/501?${new URLSearchParams({
      origin: 'live-source', datasourceId: '10',
      database: 'trade_db', table: 'orders', schema: 'public',
    })}`,
  );
  expect(mockCreateModel).not.toHaveBeenCalled();
});

it('delivers the visible complete J1 journey: exact source → live columns → draft → atomic save → return', async () => {
  mockQuery = new URLSearchParams({
    sourceJourney: 'ods', origin: 'live-source', datasourceId: '10',
    database: 'trade_db', schema: 'public', table: 'orders',
  });
  mockCandidates.mockResolvedValueOnce({
    state: 'READY', reason: 'no matching ODS yet',
    datasourceId: 10, database: 'trade_db', schema: 'public', table: 'orders', candidates: [],
  });
  jest.mocked(previewModelingImportColumns).mockResolvedValueOnce([
    { name: 'order_id', typeName: 'BIGINT', primaryKey: true, nullable: false, technical: false },
    { name: 'order_amount', typeName: 'DECIMAL', size: 18, scale: 2, nullable: false, technical: false },
  ]);
  mockCreateModel.mockResolvedValueOnce({ id: 777, code: 'ods_trade_db_public_orders', name: 'ODS orders',
    layerCode: 'ODS', status: 'DRAFT', sourceDatasourceId: null });
  mockGetModel.mockResolvedValueOnce({ id: 777, code: 'ods_trade_db_public_orders', name: 'ODS orders',
    layerCode: 'ODS', status: 'DRAFT', sourceDatasourceId: null });
  jest.mocked(getModelingStructure).mockResolvedValueOnce({
    definition: 'a-recoverable-definition', columns: [],
  } as Awaited<ReturnType<typeof getModelingStructure>>);
  jest.mocked(commitOdsManualImport).mockResolvedValueOnce({
    status: 'SAVED_AND_VERIFIED', modelId: 777, confirmedMappingCount: 2,
  } as Awaited<ReturnType<typeof commitOdsManualImport>>);
  mockGetModel.mockResolvedValueOnce({
    id: 777, layerCode: 'ODS', status: 'DRAFT',
    sourceDatasourceId: 10, sourceDatabase: 'trade_db', sourceSchema: 'public', sourceTable: 'orders',
  });
  jest.mocked(getModelingStructure).mockResolvedValueOnce({
    definition: 'persisted-definition', columns: [
      { columnName: 'order_id', dataType: 'BIGINT' },
      { columnName: 'order_amount', dataType: 'DECIMAL' },
    ],
  } as Awaited<ReturnType<typeof getModelingStructure>>);
  jest.mocked(listModelingMappings).mockResolvedValueOnce([
    { targetColumn: 'order_id', mapped: true, sourceDatasourceId: 10, sourceDatabase: 'trade_db',
      sourceSchema: 'public', sourceTable: 'orders', sourceColumn: 'order_id' },
    { targetColumn: 'order_amount', mapped: true, sourceDatasourceId: 10, sourceDatabase: 'trade_db',
      sourceSchema: 'public', sourceTable: 'orders', sourceColumn: 'order_amount' },
  ]);

  render(<OdsSourceJourney />);
  expect(screen.getByTestId('ods-source-workbench')).toBeInTheDocument();
  expect(await screen.findByText('no matching ODS yet')).toBeInTheDocument();
  const create = screen.getByRole('button', { name: '保存可恢复的空 ODS 草稿' });
  expect(create).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: '读取当前物理列并核对' }));
  expect(await screen.findByLabelText('ODS 字段 order_id')).toHaveValue('order_id');
  expect(screen.getByLabelText('ODS 字段 order_amount')).toHaveValue('order_amount');
  expect(screen.getByText('源主键')).toBeInTheDocument();
  expect(create).toBeEnabled();
  fireEvent.click(create);
  await screen.findByText(/已保存空草稿 #777/);
  expect(mockCreateModel).toHaveBeenCalledWith(expect.objectContaining({
    layerCode: 'ODS', dialect: 'MYSQL',
  }));
  fireEvent.click(screen.getByRole('button', { name: '确认并原子保存结构与映射' }));
  await screen.findByText('ODS 已成功保存');
  expect(commitOdsManualImport).toHaveBeenCalledWith(777, expect.objectContaining({
    sourceDatasourceId: 10, sourceDatabase: 'trade_db', sourceSchema: 'public', sourceTable: 'orders',
    expectedSourceColumns: expect.arrayContaining([expect.objectContaining({ name: 'order_id' })]),
    selections: [{ targetColumn: 'order_id', sourceColumn: 'order_id' },
      { targetColumn: 'order_amount', sourceColumn: 'order_amount' }],
  }));
  fireEvent.click(screen.getByRole('button', { name: '保存并返回来源' }));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith(expect.stringContaining('/data-source?')));
  const returned = new URL(String(mockPush.mock.calls.at(-1)?.[0]), 'http://localhost');
  expect(returned.searchParams.get('sourceId')).toBe('10');
  expect(returned.searchParams.get('database')).toBe('trade_db');
  expect(returned.searchParams.get('schema')).toBe('public');
  expect(returned.searchParams.get('table')).toBe('orders');
});

it('shows one native ODS content workspace instead of prototype rails and English decoration', async () => {
  render(<OdsSourceJourney />);
  expect(screen.queryByRole('navigation', { name: 'ODS 建模进度' })).not.toBeInTheDocument();
  expect(screen.getByRole('region', { name: '当前来源对象' })).toBeInTheDocument();
  expect(screen.getByRole('main', { name: 'ODS 字段与来源设计' })).toBeInTheDocument();
  expect(screen.queryByRole('complementary', { name: '下一步操作' })).not.toBeInTheDocument();
  await screen.findByRole('button', { name: '打开已保存 ODS' });
  expect(screen.getByText('已有同源 ODS')).toBeInTheDocument();
  expect(mockCreateModel).not.toHaveBeenCalled();
});


it('treats a timed-out commit as saved only after exact owner readback', async () => {
  mockCandidates.mockResolvedValueOnce({
    state: 'READY', reason: 'no ODS', datasourceId: 10, database: 'trade_db',
    schema: 'public', table: 'orders', candidates: [],
  });
  jest.mocked(previewModelingImportColumns).mockResolvedValueOnce([
    { name: 'order_id', typeName: 'BIGINT', primaryKey: true, nullable: false, technical: false },
  ]);
  mockCreateModel.mockResolvedValueOnce({
    id: 888, code: 'ods_orders', name: 'ODS orders', layerCode: 'ODS',
    status: 'DRAFT', sourceDatasourceId: null,
  });
  mockGetModel.mockResolvedValueOnce({
    id: 888, layerCode: 'ODS', status: 'DRAFT', sourceDatasourceId: null,
  }).mockResolvedValueOnce({
    id: 888, layerCode: 'ODS', status: 'DRAFT',
    sourceDatasourceId: 10, sourceDatabase: 'trade_db', sourceSchema: 'public', sourceTable: 'orders',
  });
  jest.mocked(getModelingStructure).mockResolvedValueOnce({
    definition: 'before-commit', columns: [],
  } as Awaited<ReturnType<typeof getModelingStructure>>).mockResolvedValueOnce({
    definition: 'after-commit', columns: [{ columnName: 'order_id', dataType: 'BIGINT' }],
  } as Awaited<ReturnType<typeof getModelingStructure>>);
  jest.mocked(commitOdsManualImport).mockRejectedValueOnce(new Error('HTTP timeout'));
  jest.mocked(listModelingMappings).mockResolvedValueOnce([{
    targetColumn: 'order_id', mapped: true, sourceDatasourceId: 10,
    sourceDatabase: 'trade_db', sourceSchema: 'public', sourceTable: 'orders',
    sourceColumn: 'order_id',
  }]);

  render(<OdsSourceJourney />);
  await screen.findByText('no ODS');
  fireEvent.click(screen.getByRole('button', { name: '读取当前物理列并核对' }));
  await screen.findByLabelText('ODS 字段 order_id');
  fireEvent.click(screen.getByRole('button', { name: '保存可恢复的空 ODS 草稿' }));
  await screen.findByText(/已保存空草稿 #888/);
  fireEvent.click(screen.getByRole('button', { name: '确认并原子保存结构与映射' }));
  await screen.findByText('ODS 已成功保存');
  expect(commitOdsManualImport).toHaveBeenCalledTimes(1);
  expect(listModelingMappings).toHaveBeenCalledWith(888);
});


it('reopening a source-bound draftId shows the exact existing ODS instead of an error or empty draft', async () => {
  mockQuery.set('draftId', '501');
  mockGetModel.mockResolvedValueOnce({
    id: 501, layerCode: 'ODS', status: 'DRAFT',
    sourceDatasourceId: 10, sourceDatabase: 'trade_db',
    sourceSchema: 'public', sourceTable: 'orders',
  });
  render(<OdsSourceJourney />);
  const open = await screen.findByRole('button', { name: '打开已保存 ODS' });
  expect(screen.queryByText(/已保存 ODS 与当前来源或精确候选不一致/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
  fireEvent.click(open);
  expect(mockPush).toHaveBeenCalledWith('/modeling/models/501');
  expect(mockCreateModel).not.toHaveBeenCalled();
  expect(commitOdsManualImport).not.toHaveBeenCalled();
});

it('rejects a resumed ODS bound to another schema without offering any create or commit action', async () => {
  mockQuery.set('draftId', '501');
  mockGetModel.mockResolvedValueOnce({
    id: 501, layerCode: 'ODS', status: 'DRAFT',
    sourceDatasourceId: 10, sourceDatabase: 'trade_db',
    sourceSchema: 'archive', sourceTable: 'orders',
  });
  render(<OdsSourceJourney />);
  await screen.findByText(/已保存 ODS 与当前来源或精确候选不一致/);
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
  expect(mockCreateModel).not.toHaveBeenCalled();
});

it('does not mistake a different server candidate for the persisted draftId on resume', async () => {
  mockQuery.set('draftId', '502');
  mockGetModel.mockResolvedValueOnce({
    id: 502, layerCode: 'ODS', status: 'DRAFT',
    sourceDatasourceId: 10, sourceDatabase: 'trade_db',
    sourceSchema: 'public', sourceTable: 'orders',
  });
  // Default candidate list is only 501, not 502.
  render(<OdsSourceJourney />);
  await screen.findByText(/已保存 ODS 与当前来源或精确候选不一致/);
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
  expect(mockCreateModel).not.toHaveBeenCalled();
});
