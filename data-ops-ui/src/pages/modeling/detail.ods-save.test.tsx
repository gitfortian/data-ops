import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { message } from 'antd';
import { commitOdsManualImport, getModelingModel, getModelingStructure, getModelingTypeCatalog,
  saveModelingStructure, validateModelingStructure } from '@/services/modeling/api';
import type { ModelingSaveStructurePayload } from '@/services/modeling/types';
import ModelingModelDetail from './detail';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() }, useParams: () => ({ id: '701' }),
  useSearchParams: () => [new URLSearchParams()],
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: 42 } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({ canAll: () => true }),
}));
jest.mock('@/services/modeling/api', () => ({
  commitOdsManualImport: jest.fn(), getModelingModel: jest.fn(), getModelingStructure: jest.fn(),
  getModelingTypeCatalog: jest.fn(), saveModelingStructure: jest.fn(), validateModelingStructure: jest.fn(),
}));
jest.mock('@/services/modeling/import', () => ({
  previewModelingImportColumns: jest.fn().mockResolvedValue([
    { name: 'order_id', typeName: 'BIGINT' }, { name: 'order_no', typeName: 'BIGINT' },
    { name: 'process_time', typeName: 'BIGINT', technical: true },
  ]),
}));
jest.mock('@/services/semantic/api', () => ({
  pageSemanticFields: jest.fn().mockResolvedValue({ bizData: [] }),
  getStandardOptions: jest.fn().mockResolvedValue({}),
}));
jest.mock('@/services/approval/api', () => ({
  listFlows: jest.fn().mockResolvedValue({ bizData: [] }), findByBiz: jest.fn().mockResolvedValue(null),
}));
jest.mock('@/pages/modeling/components/BatchStandardAssistant', () => () => null);
jest.mock('@/pages/modeling/components/DwdPublishedMappingHistoryPanel', () => () => null);
jest.mock('@/pages/modeling/components/PhysicalStandardCoveragePanel', () => () => null);
jest.mock('@/pages/modeling/components/PhysicalDataDevHandoffPanel', () => () => null);
jest.mock('@/pages/modeling/components/DwsGrainDesignPanel', () => () => null);
jest.mock('@/pages/modeling/components/AdsApplicationDesignPanel', () => () => null);
jest.mock('@/pages/modeling/components/StandardAssistantDrawer', () => () => null);
jest.mock('@/components/version/JsonDiffView', () => () => null);

// Keep real editor state and save handlers. Replace presentation widgets so the
// tests drive field edits and mapping choices without drag/portal machinery.
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return {
    ...actual,
    AutoComplete: ({ value, onChange }: any) => <input value={value} onChange={e => onChange(e.target.value)} />,
    Select: ({ value, onChange, options = [], disabled, 'aria-label': label }: any) => (
      <select aria-label={label} value={value ?? ''} disabled={disabled}
        onChange={e => onChange?.(e.target.value || undefined)}>
        <option value="" />
        {options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    ),
    Table: ({ columns = [], dataSource = [] }: any) => (
      <table><tbody>{dataSource.map((row: any, index: number) => <tr key={index}>
        {columns.map((column: any, cell: number) => <td key={cell}>
          {column.render ? column.render(row[column.dataIndex], row, index) : row[column.dataIndex]}
        </td>)}
      </tr>)}</tbody></table>
    ),
    Modal: Object.assign(({ open, title, children, onOk, onCancel, okText, okButtonProps, confirmLoading }: any) => open ? (
      <div role="dialog"><h2>{title}</h2>{children}
        <button onClick={onCancel}>取消</button>
        <button disabled={okButtonProps?.disabled || confirmLoading} onClick={onOk}>{okText}</button>
      </div>
    ) : null, { confirm: jest.fn() }),
  };
});

let savedStructure: ModelingSaveStructurePayload | undefined;
const receipt = { modelId: 701, status: 'SAVED_AND_VERIFIED' as const,
  sourceDatasourceId: 10, sourceDatabase: 'trade_db', sourceTable: 'orders',
  sourceColumnCount: 2, confirmedMappingCount: 2, newStructureDefinition: 'saved-definition' };

beforeEach(() => {
  jest.clearAllMocks();
  savedStructure = undefined;
  window.history.replaceState({}, '', '/modeling/models/701?initMode=import&datasourceId=10&database=trade_db&table=orders');
  jest.mocked(getModelingModel).mockResolvedValue({ id: 701, code: 'ods_orders', name: '订单',
    layerCode: 'ODS', status: 'DRAFT', dialect: 'MYSQL' });
  jest.mocked(getModelingStructure).mockImplementation(async () => ({
    modelId: 701, modelCode: 'ods_orders', dialect: 'MYSQL', columns: [],
    ...savedStructure, definition: savedStructure ? 'saved-definition' : 'initial-definition',
  }));
  jest.mocked(getModelingTypeCatalog).mockResolvedValue([{ name: 'BIGINT' }, { name: 'VARCHAR' }]);
  jest.mocked(validateModelingStructure).mockResolvedValue([]);
  jest.mocked(commitOdsManualImport).mockImplementation(async (_, payload) => {
    savedStructure = payload.structure;
    return { ...receipt, confirmedMappingCount: payload.selections.length };
  });
  jest.spyOn(message, 'success').mockImplementation(jest.fn());
  jest.spyOn(message, 'error').mockImplementation(jest.fn());
  jest.spyOn(message, 'info').mockImplementation(jest.fn());
});
afterEach(() => jest.restoreAllMocks());

async function openImportedDraft() {
  render(<ModelingModelDetail />);
  await screen.findByDisplayValue('order_no');
  await waitFor(() => expect(screen.getByText('保存表结构').closest('button')).toBeEnabled());
}

it('saves matching source fields in one atomic command without a confirmation dialog', async () => {
  await openImportedDraft();
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(commitOdsManualImport).toHaveBeenCalledTimes(1));
  expect(commitOdsManualImport).toHaveBeenCalledWith('701', expect.objectContaining({
    expectedDefinition: 'initial-definition', sourceDatasourceId: 10, sourceDatabase: 'trade_db', sourceTable: 'orders',
    selections: [{ targetColumn: 'order_id', sourceColumn: 'order_id' },
      { targetColumn: 'order_no', sourceColumn: 'order_no' }],
  }));
  expect(saveModelingStructure).not.toHaveBeenCalled();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  await waitFor(() => expect(screen.getByText('保存表结构').closest('button')).toBeDisabled());
});

it('shows renamed fields as structural differences and saves only known mappings without a dialog', async () => {
  await openImportedDraft();
  fireEvent.change(screen.getByDisplayValue('order_no'), { target: { value: 'order_number' } });
  expect(await screen.findByText('order_no')).toBeInTheDocument();
  expect(screen.getByText('模型新增')).toBeInTheDocument();
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(commitOdsManualImport).toHaveBeenCalledTimes(1));
  expect(commitOdsManualImport).toHaveBeenCalledWith('701', expect.objectContaining({
    selections: [{ targetColumn: 'order_id', sourceColumn: 'order_id' }],
    structure: expect.objectContaining({ columns: expect.arrayContaining([
      expect.objectContaining({ columnName: 'order_number' }),
    ]) }),
  }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  await waitFor(() => expect(message.success).toHaveBeenCalledWith('表结构已保存'));
});

it('allows a source column to be omitted while keeping its name visible in the difference hint', async () => {
  await openImportedDraft();
  const row = screen.getByDisplayValue('order_no').closest('tr')!;
  fireEvent.click(within(row).getByText('删除'));
  expect(await screen.findByText('order_no')).toBeInTheDocument();
  expect(screen.getByText('源表独有 1')).toBeInTheDocument();
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(commitOdsManualImport).toHaveBeenCalledTimes(1));
  expect(commitOdsManualImport).toHaveBeenCalledWith('701', expect.objectContaining({
    selections: [{ targetColumn: 'order_id', sourceColumn: 'order_id' }],
  }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  await waitFor(() => expect(message.success).toHaveBeenCalledWith('表结构已保存'));
});

it('highlights a changed source-field type without making it a save blocker', async () => {
  await openImportedDraft();
  const row = screen.getByDisplayValue('order_no').closest('tr')!;
  fireEvent.change(within(row).getByDisplayValue('BIGINT'), { target: { value: 'VARCHAR' } });
  expect(await within(row).findByText('属性差异')).toBeInTheDocument();
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(commitOdsManualImport).toHaveBeenCalledTimes(1));
  expect(commitOdsManualImport).toHaveBeenCalledWith('701', expect.objectContaining({
    structure: expect.objectContaining({ columns: expect.arrayContaining([
      expect.objectContaining({ columnName: 'order_no', dataType: 'VARCHAR' }),
    ]) }),
  }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('keeps edits made during saving and uses the saved receipt as the next edit baseline', async () => {
  let finish!: (value: typeof receipt) => void;
  jest.mocked(commitOdsManualImport).mockImplementationOnce((_, payload) => {
    savedStructure = payload.structure;
    return new Promise(resolve => { finish = resolve; });
  });
  await openImportedDraft();
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(commitOdsManualImport).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText('表注释'), { target: { value: '保存期间的新编辑' } });
  await act(async () => { finish(receipt); });
  expect(screen.getByLabelText('表注释')).toHaveValue('保存期间的新编辑');
  await waitFor(() => expect(screen.getByText('保存表结构').closest('button')).toBeEnabled());
  jest.mocked(saveModelingStructure).mockImplementationOnce(async (_, payload) => ({
    ...payload, definition: 'next-definition',
  }));
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(saveModelingStructure).toHaveBeenCalledWith('701',
    expect.objectContaining({ tableComment: '保存期间的新编辑' }), 'saved-definition'));
  expect(commitOdsManualImport).toHaveBeenCalledTimes(1);
});

it('does not commit invalid structures or report an incomplete save receipt as success', async () => {
  jest.mocked(validateModelingStructure).mockResolvedValueOnce([{ severity: 'ERROR', message: '类型不兼容' }]);
  await openImportedDraft();
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(message.error).toHaveBeenCalled());
  expect(commitOdsManualImport).not.toHaveBeenCalled();
  jest.mocked(commitOdsManualImport).mockResolvedValueOnce({ ...receipt, confirmedMappingCount: 1 });
  await waitFor(() => expect(screen.getByText('保存表结构').closest('button')).toBeEnabled());
  fireEvent.click(screen.getByText('保存表结构'));
  await waitFor(() => expect(message.error).toHaveBeenCalledWith('来源映射保存回执不完整，不能认定导入成功'));
  expect(message.success).not.toHaveBeenCalledWith('表结构已保存');
  expect(saveModelingStructure).not.toHaveBeenCalled();
});
