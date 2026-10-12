import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import OdsSourceJourney from './OdsSourceJourney';

let query = new URLSearchParams({
  sourceJourney: 'ods', datasourceId: '10', database: 'trade_db',
  schema: 'public', table: 'orders',
});
let projectId = 42;
const push = jest.fn();
const candidates = jest.fn();
const sources = jest.fn();
const getModel = jest.fn();
const createModel = jest.fn();

jest.mock('@umijs/max', () => ({
  history: { push: (...args: unknown[]) => push(...args), replace: jest.fn() },
  useSearchParams: () => [query, jest.fn()],
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: projectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true,
  default: () => ({
    can: (permission: string) => ['modeling:read', 'modeling:create', 'modeling:update'].includes(permission),
  }),
}));
jest.mock('@/services/data-source/api', () => ({
  listAllDataSources: (...args: unknown[]) => sources(...args),
}));
jest.mock('@/services/metadata/api', () => ({
  getEntityDetail: jest.fn(),
}));
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: (...args: unknown[]) => getModel(...args),
  createModelingModel: (...args: unknown[]) => createModel(...args),
  getModelingStructure: jest.fn(), commitOdsManualImport: jest.fn(),
}));
jest.mock('@/services/modeling/import', () => ({
  getOdsSourceCandidates: (...args: unknown[]) => candidates(...args),
  previewModelingImportColumns: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  projectId = 42;
  query = new URLSearchParams({
    sourceJourney: 'ods', datasourceId: '10',
    database: 'trade_db', schema: 'public', table: 'orders',
  });
  sources.mockResolvedValue({ bizData: [{ id: 10, dbType: 'MYSQL' }] });
  candidates.mockResolvedValue({
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
  expect(push).toHaveBeenCalledWith('/modeling/models/501');
  expect(createModel).not.toHaveBeenCalled();
  expect(candidates).toHaveBeenCalledWith({
    datasourceId: 10, database: 'trade_db', schema: 'public', table: 'orders',
  });
});

it('does not treat unverified or unowned source as an empty ODS candidate list', async () => {
  candidates.mockResolvedValueOnce({
    state: 'UNVERIFIED', reason: 'legacy schema unknown',
    datasourceId: 10, database: 'trade_db', schema: 'public', table: 'orders',
    candidates: [],
  });
  render(<OdsSourceJourney />);
  expect(await screen.findByText('不能确认来源身份')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
  expect(createModel).not.toHaveBeenCalled();
});

it('rejects missing project source evidence instead of offering a new model', async () => {
  sources.mockResolvedValueOnce({ bizData: [{ id: 99, dbType: 'MYSQL' }] });
  render(<OdsSourceJourney />);
  await waitFor(() =>
    expect(screen.getByText(/当前项目无法核验来源数据源/)).toBeInTheDocument());
  expect(screen.queryByRole('button', { name: '保存可恢复的空 ODS 草稿' })).not.toBeInTheDocument();
});
