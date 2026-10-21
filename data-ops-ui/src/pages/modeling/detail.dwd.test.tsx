import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { Modal, message } from 'antd';
import { getModelingModel, getModelingStructure, getModelPublicationReadiness } from '@/services/modeling/api';
import ModelingModelDetail from './detail';

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() }, useParams: () => ({ id: '701' }), useSearchParams: () => [new URLSearchParams()] }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: 1 } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ canAll: () => true }) }));
jest.mock('@/services/modeling/api', () => ({
  getModelingModel: jest.fn(), getModelingStructure: jest.fn(), getModelPublicationReadiness: jest.fn(),
  getModelingTypeCatalog: jest.fn().mockResolvedValue([{ name: 'BIGINT' }]),
}));
jest.mock('@/services/semantic/api', () => ({ pageSemanticFields: jest.fn().mockResolvedValue({ bizData: [] }), getStandardOptions: jest.fn().mockResolvedValue({}) }));
jest.mock('@/services/approval/api', () => ({ listFlows: jest.fn().mockResolvedValue({ bizData: [] }), findByBiz: jest.fn().mockResolvedValue(null) }));
jest.mock('@/pages/modeling/components/BatchStandardAssistant', () => () => null);
jest.mock('@/pages/modeling/components/DwdPublishedMappingHistoryPanel', () => () => null);
jest.mock('@/pages/modeling/components/PhysicalStandardCoveragePanel', () => () => null);
jest.mock('@/pages/modeling/components/PhysicalDataDevHandoffPanel', () => () => null);
jest.mock('@/pages/modeling/components/DwsGrainDesignPanel', () => () => null);
jest.mock('@/pages/modeling/components/AdsApplicationDesignPanel', () => () => null);
jest.mock('@/pages/modeling/components/StandardAssistantDrawer', () => () => null);
jest.mock('@/components/version/JsonDiffView', () => () => null);
jest.mock('antd', () => {
  const actual = jest.requireActual('antd');
  return { ...actual, Table: ({ columns = [], dataSource = [] }: any) => <table><tbody>
    {dataSource.map((row: any, index: number) => <tr key={index}>{columns.map((column: any, cell: number) => <td key={cell}>
      {column.render ? column.render(row[column.dataIndex], row, index) : row[column.dataIndex]}
    </td>)}</tr>)}
  </tbody></table> };
});

const structure = { modelId: 701, modelCode: 'dwd_order', tableName: 'dwd_order', dialect: 'MYSQL' as const,
  definition: 'saved', columns: [{ id: 901, columnName: 'order_id', dataType: 'BIGINT', stdTypeId: 7, stdFieldId: null }] };

beforeEach(() => {
  jest.clearAllMocks();
  window.history.replaceState({}, '', '/modeling/models/701');
  jest.mocked(getModelingModel).mockResolvedValue({ id: 701, code: 'dwd_order', name: '订单事实', layerCode: 'DWD', status: 'DRAFT', dialect: 'MYSQL' });
  jest.mocked(getModelingStructure).mockResolvedValue(structure);
  jest.mocked(getModelPublicationReadiness).mockResolvedValue({ modelId: 701, layerCode: 'DWD', structureSha256: null,
    standardsVerdict: 'VALID', semanticRevision: 2, standardEvidenceSha256: null, columns: [], findings: ['发布资格仍待核实'],
    authorizedForPublication: false, authorizationStatus: 'BLOCKED', mapping: null });
  jest.spyOn(message, 'error').mockImplementation(jest.fn());
});
afterEach(() => { jest.restoreAllMocks(); Modal.destroyAll(); });

it('counts standard-field references without claiming that type bindings complete governance', async () => {
  render(<ModelingModelDetail layerCode="DWD" />);
  expect(await screen.findByText('1 个字段 · 已关联标准字段 0 个')).toBeInTheDocument();
  expect(screen.queryByText('全部已治理')).not.toBeInTheDocument();
  expect(screen.queryByText('设计编辑与版本发布')).not.toBeInTheDocument();
});

it('keeps unsaved DWD edits when a refresh is cancelled', async () => {
  const confirm = jest.spyOn(Modal, 'confirm').mockReturnValue({ destroy: jest.fn(), update: jest.fn() });
  render(<ModelingModelDetail layerCode="DWD" />);
  await screen.findByDisplayValue('dwd_order');
  fireEvent.change(screen.getByLabelText('表注释'), { target: { value: '未保存的注释' } });
  fireEvent.click(screen.getByRole('button', { name: /^刷\s*新$/ }));
  expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ title: '重新加载表结构？', cancelText: '继续编辑' }));
  expect(getModelingStructure).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText('表注释')).toHaveValue('未保存的注释');
});

it('keeps publication disabled when the owner has not authorized it', async () => {
  render(<ModelingModelDetail layerCode="DWD" />);
  await screen.findByDisplayValue('dwd_order');
  fireEvent.click(screen.getByText('发布设计'));
  const dialog = within(await screen.findByRole('dialog', { name: '首次发布' }));
  expect(dialog.getByText('当前设计尚未满足发布条件')).toBeInTheDocument();
  expect(dialog.getByRole('button', { name: '发布 V1' })).toBeDisabled();
  expect(getModelPublicationReadiness).toHaveBeenCalledWith('701');
});

it('retries a failed DWD structure read from the same editor', async () => {
  jest.mocked(getModelingStructure).mockRejectedValueOnce(new Error('暂时无法读取'));
  render(<ModelingModelDetail layerCode="DWD" />);
  expect(await screen.findByText('暂时无法读取')).toBeInTheDocument();
  fireEvent.click(screen.getByText('重新加载'));
  expect(await screen.findByDisplayValue('dwd_order')).toBeInTheDocument();
  expect(getModelingStructure).toHaveBeenCalledTimes(2);
});
