import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import type { ModelingDerivePreview, ModelingDeriveReceipt } from '@/services/modeling/types';
import ModelDeriveDrawer from './ModelDeriveDrawer';

const mockPreview = jest.fn();
const mockDerive = jest.fn();
jest.mock('@/services/modeling/api', () => ({
  previewModelingDerive: (...args: unknown[]) => mockPreview(...args),
  deriveModelingModel: (...args: unknown[]) => mockDerive(...args),
}));
jest.mock('@/services/semantic/api', () => ({ listSemanticProcessFields: jest.fn().mockResolvedValue([]) }));
const evidence = (extra: Partial<ModelingDerivePreview> = {}): ModelingDerivePreview => ({
  processId: 7, layerCode: 'DWS', upstreamLayer: 'DWD', mode: 'AGGREGATE', supported: true,
  sources: [], upstreamModels: [{ modelId: 20, name: '订单明细', code: 'dwd_order', layerCode: 'DWD', fieldCount: 1, selected: true }],
  fields: [{ sourceTable: 'dwd_order', sourceColumn: 'amount', landingField: 'amount', dataType: 'BIGINT',
    tableRole: 'MAIN', include: true, conflicting: false, technical: false, convention: false, fieldRole: 'MEASURE', aggregateFunc: 'SUM' }],
  totalFields: 1, matchedFields: 0, unmatchedFields: 1, conflictFields: 0, technicalFields: 0, conventionFields: 0, governanceRate: 0, warnings: [], ...extra,
});
const props = (extra: Partial<ComponentProps<typeof ModelDeriveDrawer>> = {}): ComponentProps<typeof ModelDeriveDrawer> => ({
  base: { name: '订单汇总', code: 'dws_orders_1d', dialect: 'MYSQL', processId: 7, layerCode: 'DWS' },
  projectId: 1, canCreate: true, onClose: jest.fn(), onBack: jest.fn(), onOpenModel: jest.fn(), ...extra,
});
beforeEach(() => { jest.clearAllMocks(); mockPreview.mockResolvedValue(evidence()); });

it('requires review and a single explicit submit, then renders the actual receipt without auto-navigation', async () => {
  let resolve!: (value: ModelingDeriveReceipt) => void;
  mockDerive.mockReturnValue(new Promise<ModelingDeriveReceipt>(done => { resolve = done; }));
  const input = props();
  render(<ModelDeriveDrawer {...input} />);
  await screen.findByLabelText('目标字段 dwd_order.amount');
  expect(mockDerive).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '复核设计' }));
  expect(mockDerive).not.toHaveBeenCalled();
  const submit = screen.getByText('确认创建模型草稿');
  fireEvent.click(submit);
  fireEvent.click(submit);
  expect(mockDerive).toHaveBeenCalledTimes(1);
  expect(mockDerive.mock.calls[0][0]).toMatchObject({ processId: 7, layerCode: 'DWS', upstreamModelIds: [20] });
  await act(async () => resolve({ modelId: 88, code: 'dws_orders_1d', name: '订单汇总', columnCount: 3,
    governedCount: 1, unmatchedCount: 0, governanceRate: 100, backfilledCount: 0, sourceMappingSkipped: 1 }));
  expect(await screen.findByText('模型草稿已创建')).toBeInTheDocument();
  expect(input.onOpenModel).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('打开表结构'));
  expect(input.onOpenModel).toHaveBeenCalledWith(88, 'structure');
});

it('blocks an existing process/layer model and exposes its canonical detail', async () => {
  mockPreview.mockResolvedValue(evidence({ existingModel: { modelId: 99, name: '已有汇总', code: 'existing_dws' } }));
  const input = props();
  render(<ModelDeriveDrawer {...input} />);
  await screen.findByText('同一过程在目标层已有模型');
  expect(screen.getByRole('button', { name: '复核设计' })).toBeDisabled();
  fireEvent.click(screen.getByText('打开已有模型'));
  expect(input.onOpenModel).toHaveBeenCalledWith(99, 'structure');
  expect(mockDerive).not.toHaveBeenCalled();
});

it('isolates an old preview after app settings change until the user reads the new configuration', async () => {
  let resolve!: (value: ModelingDerivePreview) => void;
  mockPreview.mockReturnValueOnce(new Promise<ModelingDerivePreview>(done => { resolve = done; }));
  mockPreview.mockResolvedValue(evidence({ layerCode: 'ADS', upstreamLayer: 'DWS', mode: 'APPLICATION' }));
  const input = props({ base: { name: '应用', code: 'ads_ops', dialect: 'MYSQL', processId: 7, layerCode: 'ADS' } });
  render(<ModelDeriveDrawer {...input} />);
  fireEvent.change(screen.getByLabelText('应用编码'), { target: { value: 'ops' } });
  fireEvent.change(screen.getByLabelText('应用名称'), { target: { value: '经营分析' } });
  await act(async () => resolve(evidence({ layerCode: 'ADS', upstreamLayer: 'DWS', mode: 'APPLICATION' })));
  expect(screen.queryByLabelText('目标字段 dwd_order.amount')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '复核设计' })).toBeDisabled();
  fireEvent.click(screen.getByText('读取上游与字段'));
  await screen.findByLabelText('目标字段 dwd_order.amount');
  expect(mockPreview.mock.calls[1].slice(6, 9)).toEqual(['ops', '经营分析', 'DWS']);
  expect(mockDerive).not.toHaveBeenCalled();
});

it('preserves field edits when creation fails and never shows a false success receipt', async () => {
  mockDerive.mockRejectedValue(new Error('source changed'));
  render(<ModelDeriveDrawer {...props()} />);
  fireEvent.change(await screen.findByLabelText('目标字段 dwd_order.amount'), { target: { value: 'paid_total' } });
  fireEvent.click(screen.getByRole('button', { name: '复核设计' }));
  fireEvent.click(screen.getByText('确认创建模型草稿'));
  await screen.findByText(/source changed/);
  expect(screen.queryByText('模型草稿已创建')).not.toBeInTheDocument();
  fireEvent.click(screen.getByText('返回字段设计'));
  expect(screen.getByLabelText('目标字段 dwd_order.amount')).toHaveValue('paid_total');
});

it('ignores late preview data when the project changes', async () => {
  let resolve!: (value: ModelingDerivePreview) => void;
  mockPreview.mockReturnValueOnce(new Promise<ModelingDerivePreview>(done => { resolve = done; }));
  mockPreview.mockResolvedValue(evidence({ fields: [] }));
  const input = props();
  const view = render(<ModelDeriveDrawer {...input} />);
  view.rerender(<ModelDeriveDrawer {...input} projectId={2} />);
  await waitFor(() => expect(mockPreview).toHaveBeenCalledTimes(2));
  await act(async () => resolve(evidence()));
  expect(screen.queryByLabelText('目标字段 dwd_order.amount')).not.toBeInTheDocument();
});

it('does not serialize a cleared upstream selection into an all-model preview', async () => {
  render(<ModelDeriveDrawer {...props()} />);
  await screen.findByLabelText('目标字段 dwd_order.amount');
  fireEvent.keyDown(screen.getByRole('combobox', { name: '参与的上游模型' }), { key: 'Backspace', code: 'Backspace', keyCode: 8, which: 8 });
  await waitFor(() => expect(screen.getByRole('button', { name: '复核设计' })).toBeDisabled());
  fireEvent.click(screen.getByText('读取上游与字段'));
  expect(await screen.findByText('请至少选择一个上游模型；清空选择不会按默认全选读取。')).toBeInTheDocument();
  expect(mockPreview).toHaveBeenCalledTimes(1);
  expect(mockDerive).not.toHaveBeenCalled();
});

it('identifies a model upstream by its actual layer instead of labeling it ODS', async () => {
  mockPreview.mockResolvedValue(evidence({ sources: [{ bindingId: 20, datasourceId: 0,
    sourceTable: 'dwd_order', tableRole: 'DWD', odsModelId: 20, ready: true, fieldCount: 1 }] }));
  render(<ModelDeriveDrawer {...props()} />);
  expect(await screen.findByText('dwd_order · DWD · DWD #20')).toBeInTheDocument();
  expect(screen.queryByText(/ODS #20/)).not.toBeInTheDocument();
});

it('does not present a previous preview failure as evidence for changed app settings', async () => {
  mockPreview.mockResolvedValue(evidence({ layerCode: 'ADS', upstreamLayer: 'DWS', supported: false,
    unsupportedReason: '原 DWS 上游不存在', warnings: ['原 DWS 的校验提示'] }));
  render(<ModelDeriveDrawer {...props({ base: { name: '应用', code: 'ads_ops', dialect: 'MYSQL', processId: 7, layerCode: 'ADS' } })} />);
  await screen.findByText('原 DWS 上游不存在');
  fireEvent.change(screen.getByLabelText('应用编码'), { target: { value: 'ops' } });
  expect(screen.getByText('请读取当前配置后继续设计')).toBeInTheDocument();
  expect(screen.queryByText('原 DWS 上游不存在')).not.toBeInTheDocument();
  expect(screen.queryByText('原 DWS 的校验提示')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '复核设计' })).toBeDisabled();
});

it('protects edited application settings on exit even after a successful preview read', async () => {
  mockPreview.mockResolvedValue(evidence({ layerCode: 'ADS', upstreamLayer: 'DWS' }));
  const input = props({ base: { name: '应用', code: 'ads_ops', dialect: 'MYSQL', processId: 7, layerCode: 'ADS' } });
  const view = render(<ModelDeriveDrawer {...input} />);
  await screen.findByLabelText('目标字段 dwd_order.amount');
  fireEvent.change(screen.getByLabelText('应用编码'), { target: { value: 'ops' } });
  fireEvent.click(screen.getByText('读取上游与字段'));
  await waitFor(() => expect(screen.getByLabelText('目标字段 dwd_order.amount')).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: /^取\s*消$/ }));
  const discardMessage = '离开会放弃此处的上游配置与字段设计。返回基本信息会保留之前填写的模型信息。';
  await screen.findByText(discardMessage);
  expect(input.onClose).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('继续设计'));
  await waitFor(() => expect(screen.queryByText(discardMessage)).not.toBeInTheDocument());
  view.unmount();
});
