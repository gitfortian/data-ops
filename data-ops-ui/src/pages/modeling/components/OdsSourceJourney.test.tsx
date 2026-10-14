import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  expect(mockPush).toHaveBeenCalledWith(`/data-source?${expected.toString()}`);
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
