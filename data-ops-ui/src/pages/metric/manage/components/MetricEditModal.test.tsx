import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MetricEditModal from './MetricEditModal';
import { getModelingModel } from '@/services/modeling/api';
import { getMetric, updateMetric } from '@/services/metric/api';
let mockProjectId = 1;
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ __esModule: true, default: () => ({ canAll: () => true }) }));
jest.mock('@umijs/max', () => ({ useModel: () => ({ initialState: { currentUser: { userName: 'tester' } } }) }));
jest.mock('@/components/ui', () => ({ YakButton: (props: React.ButtonHTMLAttributes<HTMLButtonElement>) => <button {...props} /> }));
jest.mock('@/components/ai/MetricExplanationPanel', () => ({ __esModule: true, default: ({ disabled, onApply, version }: { disabled: boolean; version: number; onApply: (value: string) => void }) =>
  <button disabled={disabled} onClick={() => onApply('金额合计，人工核对后保存')}>AI 带入 v{version}</button> }));
jest.mock('@/components/ai/MetricDefinitionPanel', () => ({ __esModule: true, default: ({ disabled, onApply }: any) => <button disabled={disabled} onClick={() => onApply({ name: 'AI金额', description: 'AI说明', period: 'WEEK', aggregation: 'AVG', field: 'amount', qualifiers: [], tokens: [] }, '')}>带入定义草稿</button> }));
jest.mock('@/services/metric/api', () => ({ getMetric: jest.fn(), updateMetric: jest.fn(), createMetric: jest.fn(), pageMetrics: jest.fn().mockResolvedValue({ records: [] }) }));
jest.mock('@/services/modeling/api', () => ({ getModelingModel: jest.fn(), getModelingStructure: jest.fn().mockResolvedValue({ columns: [] }), pageModelingModels: jest.fn().mockResolvedValue({ bizData: [] }) }));
jest.mock('@/services/semantic/api', () => ({ getSemanticDomainTree: jest.fn().mockResolvedValue([]), pageSemanticProcesses: jest.fn().mockResolvedValue({ bizData: [] }), pageSemanticStandards: jest.fn().mockResolvedValue({ bizData: [] }) }));
const record = { id: 7, metricName: '交易额', metricCode: 'amount', metricType: 'ATOMIC' as const,
  domainId: 1, processId: 2, measureExpr: 'SUM(amount)', modelId: 9, statPeriod: 'DAY' as const, status: 'ENABLED' as const, businessDesc: '旧说明', version: 3 };
const props = { open: true, editing: record, onClose: jest.fn(), onSaved: jest.fn() };
beforeEach(() => { jest.clearAllMocks(); mockProjectId = 1; (getMetric as jest.Mock).mockResolvedValue(record); (updateMetric as jest.Mock).mockResolvedValue({ ...record, version: 4 }); });
it('adopts only the description into the original form and manually saves with its loaded version', async () => {
  render(<MetricEditModal {...props} />);
  const adopt = await screen.findByText('AI 带入 v3'); await waitFor(() => expect(adopt).toBeEnabled());
  fireEvent.click(adopt); expect(updateMetric).not.toHaveBeenCalled();
  expect(screen.getByPlaceholderText('描述指标的业务含义和口径（选填）')).toHaveValue('金额合计，人工核对后保存');
  fireEvent.click(screen.getByText(/^保\s*存$/).closest('button')!);
  await waitFor(() => expect(updateMetric).toHaveBeenCalledWith(7, expect.objectContaining({ expectedVersion: 3, measureExpr: 'SUM(amount)', modelId: 9, businessDesc: '金额合计，人工核对后保存' })));
  expect(props.onSaved).toHaveBeenCalledTimes(1);
});
it('blocks explanation when the working definition differs from the saved version', async () => {
  render(<MetricEditModal {...props} />); await waitFor(() => expect(screen.getByText('AI 带入 v3')).toBeEnabled());
  fireEvent.change(screen.getByPlaceholderText('如 总交易额'), { target: { value: '改后的名称' } });
  await waitFor(() => expect(screen.getByText('AI 带入 v3')).toBeDisabled()); expect(updateMetric).not.toHaveBeenCalled();
});
it('does not adopt a late detail after changing projects', async () => {
  let resolve: (value: unknown) => void = () => {};
  (getMetric as jest.Mock).mockImplementationOnce(() => new Promise(r => { resolve = r; })).mockResolvedValue({ ...record, version: 4 });
  const view = render(<MetricEditModal {...props} />); mockProjectId = 2; view.rerender(<MetricEditModal {...props} />);
  await screen.findByText('AI 带入 v4'); await act(async () => resolve(record));
  expect(screen.queryByText('AI 带入 v3')).not.toBeInTheDocument(); expect(updateMetric).not.toHaveBeenCalled();
});
it('makes a failed full-detail read visible and blocks saving a list-row fallback', async () => {
  (getMetric as jest.Mock).mockRejectedValue(new Error('unavailable'));
  render(<MetricEditModal {...props} />);
  await screen.findByText('完整指标详情读取失败，无法保存或使用 AI；请关闭编辑框后重试。');
  expect(screen.getByText(/^保\s*存$/).closest('button')).toBeDisabled(); expect(screen.getByText('AI 带入 v3')).toBeDisabled();
});
it('blocks explanation after editing a composite formula while preserving the saved formula on adoption', async () => {
  const composite = { ...record, metricType: 'COMPOSITE' as const, measureExpr: undefined, modelId: undefined, processId: undefined,
    compositions: [{ subMetricId: 8, operator: 'REF', subMetricCode: 'order_cnt', sortOrder: 0 }] };
  (getMetric as jest.Mock).mockResolvedValue(composite);
  render(<MetricEditModal {...props} editing={composite} />);
  await waitFor(() => expect(screen.getByText('AI 带入 v3')).toBeEnabled());
  const formula = screen.getByPlaceholderText('输入公式，如 order_cnt / uv（点击上方标签和运算符快速插入）');
  expect(formula).toHaveValue('order_cnt');
  fireEvent.change(formula, { target: { value: 'order_cnt * 2' } });
  await waitFor(() => expect(screen.getByText('AI 带入 v3')).toBeDisabled()); expect(updateMetric).not.toHaveBeenCalled();
});

it('adopts definition fields but leaves source identity and CAS save with the original form', async () => {
  render(<MetricEditModal {...props} />); await waitFor(() => expect(screen.getByText('AI 带入 v3')).toBeEnabled());
  fireEvent.click(screen.getByText('带入定义草稿')); expect(updateMetric).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText(/^保\s*存$/).closest('button')!);
  await waitFor(() => expect(updateMetric).toHaveBeenCalledWith(7, expect.objectContaining({ expectedVersion: 3, metricName: 'AI金额', measureExpr: 'AVG(amount)', modelId: 9, domainId: 1, processId: 2, businessDesc: 'AI说明', statPeriod: 'WEEK' })));
});
it('rechecks URL model in the current project before applying its domain and process', async () => {
  (getModelingModel as jest.Mock).mockResolvedValue({ id: 9, layerCode: 'DWD', name: '订单', domainId: 1, processId: 2 });
  render(<MetricEditModal {...props} editing={null} sourceModelId={9} />);
  fireEvent.click(screen.getByText('使用来源模型'));
  await screen.findByText('已核对来源模型：订单。请继续设计并人工保存。');
  expect(getModelingModel).toHaveBeenCalledWith(9); expect(updateMetric).not.toHaveBeenCalled();
});
