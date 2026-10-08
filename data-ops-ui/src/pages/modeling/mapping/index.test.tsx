import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MappingPage from './index';
import { getModelingMappingContext, listModelingMappings, setModelingMapping } from '@/services/modeling/mapping';

let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ canAll: () => true }) }));
jest.mock('@umijs/max', () => ({ useParams: () => ({ id: '7' }), history: { push: jest.fn() } }));
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Table: ({ dataSource, columns }: { dataSource: { targetColumn: string }[]; columns: { key?: string; render?: (v: unknown, row: unknown) => React.ReactNode }[] }) =>
    <div>{dataSource.map(row => <div key={row.targetColumn}>{row.targetColumn}{columns.find(c => c.key === 'actions')?.render?.(undefined, row)}</div>)}</div> };
});
jest.mock('@/components/ui' , () => ({ YakButton: ({ children, onClick }: React.ButtonHTMLAttributes<HTMLButtonElement>) => <button onClick={onClick}>{children}</button>, YakEmpty: () => <div /> }));
jest.mock('@/components/ai/ModelMappingPanel', () => ({ __esModule: true, default: ({ definition, onApply }: { definition: string; onApply: (column: string) => void }) => <button onClick={() => onApply('buyer_id')}>AI 带入 {definition}</button> }));
jest.mock('@/services/data-source/api', () => ({ listDataSources: jest.fn().mockResolvedValue({ bizData: [{ id: 9, name: '测试源' }] }) }));
jest.mock('@/services/modeling/api', () => ({ getModelingStructure: jest.fn().mockResolvedValue({ modelName: '用户', modelCode: 'users' }) }));
jest.mock('@/services/modeling/import', () => ({ listModelingImportTables: jest.fn().mockResolvedValue([{ database: 'db', name: 'users' }]), previewModelingImportColumns: jest.fn().mockResolvedValue([{ name: 'old', typeName: 'BIGINT' }, { name: 'buyer_id', typeName: 'BIGINT' }]) }));
jest.mock('@/services/modeling/mapping', () => ({ listModelingMappings: jest.fn(), getModelingMappingContext: jest.fn(), setModelingMapping: jest.fn(), clearAllModelingMappings: jest.fn(), clearModelingMapping: jest.fn(), validateModelingExpression: jest.fn() }));
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
