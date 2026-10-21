import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { Modal, message } from 'antd';
import MappingPage from './index';
import { getModelingMappingContext, listModelingMappings, setModelingMapping,
  previewOdsMappingReconcile, confirmOdsMappingReconcile, inspectOdsSourceEvidence } from '@/services/modeling/mapping';
import { getModelingModel, getModelingStructure } from '@/services/modeling/api';
import { previewModelingImportColumns } from '@/services/modeling/import';

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
  previewOdsMappingReconcile: jest.fn(), confirmOdsMappingReconcile: jest.fn(),
  inspectOdsSourceEvidence: jest.fn() }));
const saved = { targetColumn: 'user_id', dataType: 'BIGINT', mapped: true, sourceDatasourceId: 9, sourceDatabase: 'db', sourceTable: 'users', sourceColumn: 'old', transformExpr: 'CAST(old AS BIGINT)' };
beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 1;
  (getModelingModel as jest.Mock).mockResolvedValue({ id: 7, code: 'users', name: '用户', layerCode: 'DWD', status: 'DRAFT' });
  (listModelingMappings as jest.Mock).mockResolvedValue([saved]);
  (getModelingMappingContext as jest.Mock).mockResolvedValue({ definition: 'baseline', mapping: saved });
  (setModelingMapping as jest.Mock).mockResolvedValue(true);
});
afterEach(() => { Modal.destroyAll(); message.destroy(); });
it('adopts into the original form, clears an old expression, then manually saves with the loaded baseline and reloads', async () => {
  render(<MappingPage />);
  fireEvent.click(await screen.findByText('编辑'));
  fireEvent.click(await screen.findByText('AI 辅助选字段'));
  fireEvent.click(await screen.findByText('AI 带入 baseline'));
  expect(setModelingMapping).not.toHaveBeenCalled();
  expect(screen.getByPlaceholderText('留空则直接使用源字段')).toHaveValue('');
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
  (getModelingModel as jest.Mock).mockResolvedValue({
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
  fireEvent.click((await screen.findByText('ODS 来源映射批量核验/修复')).closest('button')!);
  expect(await screen.findByText(/正式绑定来源/)).toBeInTheDocument();
  expect(confirmOdsMappingReconcile).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText(/选择全部安全同名候选/).closest('button')!);
  expect(screen.getByText(/本次已选择 1/)).toBeInTheDocument();
  expect(confirmOdsMappingReconcile).not.toHaveBeenCalled();
});

it('published ODS source inspection shows a read-only report, not a repair action', async () => {
  (getModelingModel as jest.Mock).mockResolvedValue({
    id: 7, code: 'ods_users', name: '用户 ODS',
    layerCode: 'ODS', status: 'PUBLISHED', sourceDatasourceId: 9,
  });
  (inspectOdsSourceEvidence as jest.Mock).mockResolvedValue({
    modelId: 7, modelStatus: 'PUBLISHED', sourceDatasourceId: 9,
    sourceDatabase: 'db', sourceTable: 'users',
    status: 'NEEDS_REVIEW', evidenceSha256: 'a'.repeat(64),
    targetCount: 1, sourceCount: 1, mappingCount: 1,
    blockers: ['真实源列无法匹配：old'],
    fields: [{ targetColumn: 'user_id', targetType: 'BIGINT', sourceColumn: 'old',
      sourceType: null, status: 'MISSING_SOURCE', reason: '最新源表不存在该列' }],
  });
  render(<MappingPage />);
  fireEvent.click((await screen.findByText('核验当前 ODS 来源证据（只读）')).closest('button')!);
  expect(await screen.findByText('存在需要审阅的来源证据')).toBeInTheDocument();
  expect(screen.getByText('真实源列无法匹配：old')).toBeInTheDocument();
  expect(previewOdsMappingReconcile).not.toHaveBeenCalled();
  expect(confirmOdsMappingReconcile).not.toHaveBeenCalled();
  expect(setModelingMapping).not.toHaveBeenCalled();
  expect(screen.queryByText('ODS 来源映射批量核验/修复')).not.toBeInTheDocument();
});

it('filters DWD fields by mapping state and source search without changing saved mappings', async () => {
  (listModelingMappings as jest.Mock).mockResolvedValue([saved, { targetColumn: 'email', dataType: 'STRING', mapped: false }]);
  render(<MappingPage />);
  await screen.findByText('email');
  fireEvent.click(screen.getByText('待映射 1'));
  expect(screen.queryByText('user_id')).not.toBeInTheDocument();
  expect(screen.getByText('email')).toBeInTheDocument();
  fireEvent.click(screen.getByText('全部 2'));
  fireEvent.change(screen.getByRole('searchbox', { name: '搜索字段映射' }), { target: { value: 'users' } });
  expect(screen.getByText('user_id')).toBeInTheDocument();
  expect(screen.queryByText('email')).not.toBeInTheDocument();
  expect(setModelingMapping).not.toHaveBeenCalled();
});

it('protects a changed DWD mapping on cancel and reports its unsaved editor state', async () => {
  const changed = jest.fn();
  render(<MappingPage layerCode="DWD" onEditorStateChange={changed} />);
  fireEvent.click(await screen.findByText('编辑'));
  const dialog = within(await screen.findByRole('dialog', { name: '字段映射 · user_id' }));
  const expression = dialog.getByPlaceholderText('留空则直接使用源字段');
  await waitFor(() => expect(expression).toHaveValue('CAST(old AS BIGINT)'));
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith({ loading: false, saving: false, dirty: false }));
  fireEvent.change(expression, { target: { value: 'old + 1' } });
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith({ loading: false, saving: false, dirty: true }));
  fireEvent.click(dialog.getByRole('button', { name: /^取\s*消$/ }));
  expect((await screen.findAllByText('放弃未保存的映射？')).length).toBeGreaterThan(0);
  fireEvent.click(screen.getByRole('button', { name: '继续编辑' }));
  expect(expression).toHaveValue('old + 1');
  expect(setModelingMapping).not.toHaveBeenCalled();
  await waitFor(() => expect(screen.queryAllByText('放弃未保存的映射？')).toHaveLength(0));
  // Guard the second discard via its explicit confirm callback, not a portal animation.
  const confirmation = jest.spyOn(Modal, 'confirm');
  fireEvent.click(dialog.getByRole('button', { name: /^取\s*消$/ }));
  await waitFor(() => expect(confirmation).toHaveBeenCalled());
  const configuration = confirmation.mock.calls[confirmation.mock.calls.length - 1][0];
  expect(configuration).toEqual(expect.objectContaining({
    title: '放弃未保存的映射？', okText: '放弃修改', cancelText: '继续编辑',
  }));
  await act(async () => { await configuration.onOk?.(jest.fn() as any); });
  confirmation.mockRestore();
  await waitFor(() => expect(changed).toHaveBeenLastCalledWith({ loading: false, saving: false, dirty: false }));
  expect(screen.queryByRole('dialog', { name: '字段映射 · user_id' })).not.toBeInTheDocument();
});

it('retains DWD input and displays a failed conditional save in the editor', async () => {
  (setModelingMapping as jest.Mock).mockRejectedValueOnce(new Error('字段映射版本已变化'));
  render(<MappingPage />);
  fireEvent.click(await screen.findByText('编辑'));
  const dialog = within(await screen.findByRole('dialog', { name: '字段映射 · user_id' }));
  const expression = dialog.getByPlaceholderText('留空则直接使用源字段');
  await waitFor(() => expect(expression).toHaveValue('CAST(old AS BIGINT)'));
  fireEvent.change(expression, { target: { value: 'old + 2' } });
  fireEvent.click(dialog.getByRole('button', { name: /^保\s*存$/ }));
  expect(await dialog.findByText('字段映射版本已变化')).toBeInTheDocument();
  expect(expression).toHaveValue('old + 2');
  expect(screen.getByRole('dialog', { name: '字段映射 · user_id' })).toBeInTheDocument();
});

it('retries a failed DWD catalog read without reporting an empty model', async () => {
  (getModelingStructure as jest.Mock).mockRejectedValueOnce(new Error('读取暂不可用'));
  render(<MappingPage layerCode="DWD" />);
  expect(await screen.findByText('来源映射读取失败')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取' }));
  expect(await screen.findByText('user_id')).toBeInTheDocument();
  expect(screen.queryByText('来源映射读取失败')).not.toBeInTheDocument();
});

it('keeps a source field lookup failure visible and can retry within the same editor', async () => {
  (previewModelingImportColumns as jest.Mock).mockRejectedValueOnce(new Error('来源字段读取暂不可用'));
  render(<MappingPage />);
  fireEvent.click(await screen.findByText('编辑'));
  const dialog = within(await screen.findByRole('dialog', { name: '字段映射 · user_id' }));
  expect(await dialog.findByText('来源字段读取失败')).toBeInTheDocument();
  fireEvent.click(dialog.getByRole('button', { name: '重试字段' }));
  await waitFor(() => expect(dialog.queryByText('来源字段读取失败')).not.toBeInTheDocument());
  expect(dialog.getByPlaceholderText('留空则直接使用源字段')).toHaveValue('CAST(old AS BIGINT)');
});

it('saves a DWD expression without retaining a source identity from the other mode', async () => {
  render(<MappingPage />);
  fireEvent.click(await screen.findByText('编辑'));
  const dialog = within(await screen.findByRole('dialog', { name: '字段映射 · user_id' }));
  await waitFor(() => expect(dialog.getByPlaceholderText('留空则直接使用源字段')).toHaveValue('CAST(old AS BIGINT)'));
  fireEvent.click(dialog.getByText('表达式'));
  fireEvent.change(dialog.getByPlaceholderText("CURRENT_TIMESTAMP、NOW() 或 'fixed_value'"), { target: { value: "'unknown'" } });
  const save = dialog.getByRole('button', { name: /^保\s*存$/ });
  fireEvent.click(save); fireEvent.click(save);
  await waitFor(() => expect(setModelingMapping).toHaveBeenCalledTimes(1));
  expect(setModelingMapping).toHaveBeenCalledWith('7', 'user_id', {
    sourceDatasourceId: null, sourceDatabase: null, sourceTable: null, sourceColumn: null, transformExpr: "'unknown'",
  }, 'baseline');
});
