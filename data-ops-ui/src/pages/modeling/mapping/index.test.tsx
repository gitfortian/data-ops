import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MappingPage from './index';
import { getModelingMappingContext, listModelingMappings, setModelingMapping,
  previewOdsMappingReconcile, confirmOdsMappingReconcile } from '@/services/modeling/mapping';
import { getModelingModel } from '@/services/modeling/api';

let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ canAll: () => true }) }));
jest.mock('@umijs/max', () => ({ useParams: () => ({ id: '7' }), history: { push: jest.fn() } }));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Table: ({ dataSource, columns }: { dataSource: { targetColumn: string }[]; columns: { key?: string; render?: (v: unknown, row: unknown) => React.ReactNode }[] }) =>
    <div>{dataSource.map(row => <div key={row.targetColumn}>{row.targetColumn}{columns.find(c => c.key === 'actions')?.render?.(undefined, row)}{columns.find(c => c.key === 'sourceChoice')?.render?.(undefined, row)}</div>)}</div> };
});
jest.mock('@/components/ui' , () => ({ YakButton: ({ children, onClick }: React.ButtonHTMLAttributes<HTMLButtonElement>) => <button onClick={onClick}>{children}</button>, YakEmpty: () => <div /> }));
jest.mock('@/components/ai/ModelMappingPanel', () => ({ __esModule: true, default: ({ definition, onApply }: { definition: string; onApply: (column: string) => void }) => <button onClick={() => onApply('buyer_id')}>AI 带入 {definition}</button> }));
jest.mock('@/services/data-source/api', () => ({ listDataSources: jest.fn().mockResolvedValue({ bizData: [{ id: 9, name: '测试源' }] }) }));
jest.mock('@/services/modeling/api', () => ({
  getModelingStructure: jest.fn().mockResolvedValue({ modelName: '用户', modelCode: 'users' }),
  getModelingModel: jest.fn().mockResolvedValue({
    id: 7, code: 'users', name: '用户', layerCode: 'DWD', status: 'DRAFT',
  }),
}));
jest.mock('@/services/modeling/import', () => ({ listModelingImportTables: jest.fn().mockResolvedValue([{ database: 'db', name: 'users' }]), previewModelingImportColumns: jest.fn().mockResolvedValue([{ name: 'old', typeName: 'BIGINT' }, { name: 'buyer_id', typeName: 'BIGINT' }]) }));
jest.mock('@/services/modeling/mapping', () => ({ listModelingMappings: jest.fn(), getModelingMappingContext: jest.fn(), setModelingMapping: jest.fn(), clearAllModelingMappings: jest.fn(), clearModelingMapping: jest.fn(), validateModelingExpression: jest.fn(),
  previewOdsMappingReconcile: jest.fn(), confirmOdsMappingReconcile: jest.fn() }));
const saved = { targetColumn: 'user_id', dataType: 'BIGINT', mapped: true, sourceDatasourceId: 9, sourceDatabase: 'db', sourceTable: 'users', sourceColumn: 'old', transformExpr: 'CAST(old AS BIGINT)' };
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  (listModelingMappings as jest.Mock).mockResolvedValue([saved]);
  (getModelingMappingContext as jest.Mock).mockResolvedValue({ definition: 'baseline', mapping: saved });
  (setModelingMapping as jest.Mock).mockResolvedValue(true);
});
it('adopts into the original form, clears an old expression, then manually saves with the loaded baseline and reloads', async () => {
  render(<MappingPage />);
  fireEvent.click(await screen.findByText('编辑'));
  fireEvent.click(await screen.findByText('AI 带入 baseline'));
  expect(setModelingMapping).not.toHaveBeenCalled();
  expect(screen.getByPlaceholderText('如 CAST(amount AS DECIMAL(18,2))')).toHaveValue('');
  fireEvent.click(screen.getByText(/^保\s*存$/).closest('button')!);
  await waitFor(() => expect(setModelingMapping).toHaveBeenCalledWith('7', 'user_id', expect.objectContaining({ sourceColumn: 'buyer_id', transformExpr: undefined }), 'baseline'));
  await waitFor(() => expect(listModelingMappings).toHaveBeenCalledTimes(2));
});
it('ignores a late edit baseline after the project changes', async () => {
  let resolve: (value: unknown) => void = () => {};
  (getModelingMappingContext as jest.Mock).mockImplementation(() => new Promise(r => { resolve = r; }));
  const view = render(<MappingPage />); fireEvent.click(await screen.findByText('编辑'));
  mockProjectId = 2; view.rerender(<MappingPage />);
  await act(async () => resolve({ definition: 'stale', mapping: saved }));
  expect(screen.queryByText('AI 带入 stale')).not.toBeInTheDocument();
  expect(setModelingMapping).not.toHaveBeenCalled();
});

it('previews ODS source evidence and allows deliberate batch selection without implicit persistence', async () => {
  (getModelingModel as jest.Mock).mockResolvedValueOnce({
    id: 7, code: 'ods_users', name: '用户 ODS',
    layerCode: 'ODS', status: 'DRAFT', sourceDatasourceId: 9,
  });
  (previewOdsMappingReconcile as jest.Mock).mockResolvedValue({
    modelId: 7, sourceDatasourceId: 9, sourceDatabase: 'db', sourceTable: 'users',
    definition: 'a'.repeat(64), sourceColumns: ['user_id'],
    mappedCount: 0, unmappedCount: 1, technicalCount: 1,
    conflictCount: 0, orphanCount: 0,
    rows: [
      { targetColumn: 'user_id', dataType: 'BIGINT', status: 'UNMAPPED',
        suggestedSourceColumn: 'user_id' },
      { targetColumn: 'process_time', dataType: 'DATETIME', status: 'TECHNICAL' },
    ],
  });
  (confirmOdsMappingReconcile as jest.Mock).mockResolvedValue({
    modelId: 7, status: 'SAVED_AND_VERIFIED', applied: 1,
    mappedCount: 1, confirmedTargets: ['user_id'],
  });
  render(<MappingPage />);
  fireEvent.click(await screen.findByRole('button', { name: 'ODS 来源映射批量核验/修复' }));
  expect(await screen.findByText(/正式绑定来源/)).toBeInTheDocument();
  expect(confirmOdsMappingReconcile).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: /选择全部安全同名候选/ }));
  expect(screen.getByText(/本次已选择 1/)).toBeInTheDocument();
  expect(confirmOdsMappingReconcile).not.toHaveBeenCalled();
});
