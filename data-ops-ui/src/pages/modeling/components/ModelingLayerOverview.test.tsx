import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ComponentProps } from 'react';
import type { ModelingPageParams, ModelingPageResult } from '@/services/modeling/types';
import ModelingLayerOverview from './ModelingLayerOverview';

const mockPage = jest.fn();
const mockMainline = jest.fn();
jest.mock('@/services/modeling/api', () => ({ pageModelingModels: (...args: unknown[]) => mockPage(...args) }));
jest.mock('@/services/modeling/view', () => ({ getModelingMainline: () => mockMainline() }));
jest.mock('@/components/ui', () => ({ YakEmpty: ({ title }: { title: string }) => <div>{title}</div> }));

const empty: ModelingPageResult = { bizData: [], pagination: { pageNo: 1, pageSize: 2, total: 0 } };
const allLayers = ['ODS', 'DWD', 'DWS', 'ADS', 'DIM'];
const props = (extra: Partial<ComponentProps<typeof ModelingLayerOverview>> = {}): ComponentProps<typeof ModelingLayerOverview> => ({
  projectId: 1, configuredLayers: allLayers, configurationAvailable: true, processReady: true, canCreate: true,
  onSelectProcess: jest.fn(), onOpenLayer: jest.fn(), onCreateLayer: jest.fn(),
  onOpenModel: jest.fn(), onOpenLogical: jest.fn(), onOpenSource: jest.fn(), onOpenMainline: jest.fn(), onOpenLayerConfig: jest.fn(), ...extra,
});

beforeEach(() => {
  jest.clearAllMocks();
  mockPage.mockResolvedValue(empty);
  mockMainline.mockResolvedValue([]);
});

it('shows unassigned models using bounded server pages, while isolating failed layer and process reads', async () => {
  mockPage.mockImplementation(({ layerCode }: ModelingPageParams) => {
    if (layerCode === 'DWD') return Promise.reject(new Error('unavailable'));
    return Promise.resolve(layerCode === 'ODS' ? {
      bizData: [{ id: 51, name: '未关联过程的订单源', code: 'ods_order', status: 'PUBLISHED' }],
      pagination: { pageNo: 1, pageSize: 2, total: 27 },
    } : empty);
  });
  mockMainline.mockRejectedValue(new Error('semantic unavailable'));
  const input = props();
  render(<ModelingLayerOverview {...input} />);
  fireEvent.click(await screen.findByText('未关联过程的订单源'));
  expect(input.onOpenModel).toHaveBeenCalledWith(51);
  expect(screen.getByLabelText('ODS 模型数')).toHaveTextContent('27');
  expect(screen.getByLabelText('DWD 模型数')).toHaveTextContent('—');
  expect(screen.getByLabelText('DIM 模型数')).toHaveTextContent('0');
  expect(screen.getByText('业务过程覆盖暂时无法读取，各层模型仍可独立查看。')).toBeInTheDocument();
  expect(mockPage).toHaveBeenCalledTimes(5);
  expect(mockPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 2, layerCode: 'ODS', processId: undefined, domainId: undefined });
});

it('waits for verified process scope, then preserves that scope and configured layer actions', async () => {
  const input = props({ processReady: false, processId: 71, processName: '下单', configuredLayers: ['ODS', 'DWD'] });
  const view = render(<ModelingLayerOverview {...input} />);
  expect(mockPage).not.toHaveBeenCalled();
  view.rerender(<ModelingLayerOverview {...input} processReady />);
  await waitFor(() => expect(screen.getByLabelText('DWD 模型数')).toHaveTextContent('0'));
  expect(mockPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 2, layerCode: 'DWD', processId: 71, domainId: undefined });
  fireEvent.click(within(screen.getByLabelText('DWD 明细事实')).getByText('新建设计'));
  expect(input.onCreateLayer).toHaveBeenCalledWith('DWD');
  expect(within(screen.getByLabelText('DIM 公共维度')).getByText('新建设计').closest('button')).toBeDisabled();
  fireEvent.click(within(screen.getByLabelText('ODS 贴源接入')).getByText('查看模型'));
  expect(input.onOpenLayer).toHaveBeenCalledWith('ODS');
});

it('discards late model and coverage responses after switching projects', async () => {
  let resolveOld!: (value: ModelingPageResult) => void;
  const old = new Promise<ModelingPageResult>(resolve => { resolveOld = resolve; });
  let calls = 0;
  mockPage.mockImplementation(() => {
    calls += 1;
    return calls <= 5 ? old : Promise.resolve({
      bizData: [{ id: 22, name: '新项目模型', code: 'new_model', status: 'DRAFT' }],
      pagination: { pageNo: 1, pageSize: 2, total: 1 },
    });
  });
  const input = props();
  const view = render(<ModelingLayerOverview {...input} />);
  view.rerender(<ModelingLayerOverview {...input} projectId={2} />);
  await screen.findAllByText('新项目模型');
  await act(async () => resolveOld({
    bizData: [{ id: 11, name: '旧项目模型', code: 'old_model' }],
    pagination: { pageNo: 1, pageSize: 2, total: 99 },
  }));
  expect(screen.queryByText('旧项目模型')).not.toBeInTheDocument();
  expect(screen.getByLabelText('ODS 模型数')).toHaveTextContent('1');
});
