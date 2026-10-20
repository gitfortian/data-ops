import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import ModelingOverviewDashboard from './ModelingOverviewDashboard';

const mockPush = jest.fn();
const mockModels = jest.fn();
const mockProcesses = jest.fn();
const mockLayers = jest.fn();
const mockLogical = jest.fn();
let mockProjectId = 42;
let mockSemanticAllowed = true;

jest.mock('@umijs/max', () => ({ history: { push: (...args: unknown[]) => mockPush(...args) } }));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  __esModule: true, default: () => ({
    can: (permission: string) => permission === 'modeling:read'
      || (permission === 'semantic:read' && mockSemanticAllowed),
  }),
}));
jest.mock('@/services/modeling/api', () => ({
  pageModelingModels: (...args: unknown[]) => mockModels(...args),
}));
jest.mock('@/services/modeling/logical', () => ({
  listLogicalDrafts: (...args: unknown[]) => mockLogical(...args),
}));
jest.mock('@/services/semantic/api', () => ({
  pageSemanticProcesses: (...args: unknown[]) => mockProcesses(...args),
  listSemanticLayers: (...args: unknown[]) => mockLayers(...args),
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 42;
  mockSemanticAllowed = true;
  mockLogical.mockResolvedValue([{ id: 10, name: '订单逻辑模型' }]);
  mockProcesses.mockResolvedValue({ bizData: [], pagination: { total: 6 } });
  mockLayers.mockResolvedValue([
    { id: 1, status: 'ENABLED', code: 'ODS' },
    { id: 2, status: 'ENABLED', code: 'DWD' },
  ]);
  mockModels.mockImplementation(async (query: { layerCode?: string; status?: string }) => {
    if (query.layerCode === 'ODS') return { bizData: [], pagination: { total: 4 } };
    if (query.layerCode === 'DWD') return { bizData: [], pagination: { total: 2 } };
    if (query.status === 'DRAFT') return { bizData: [{
      id: 101, code: 'ods_orders', name: '订单 ODS', layerCode: 'ODS', status: 'DRAFT', updateTime: '2026-10-20T10:00:00',
    }], pagination: { total: 3 } };
    return { bizData: [{
      id: 102, code: 'dwd_orders', name: '订单明细', layerCode: 'DWD', status: 'PUBLISHED', updateTime: '2026-10-20T11:00:00',
    }], pagination: { total: 9 } };
  });
});

it('renders a true overview with project totals and no duplicated physical catalog controls', async () => {
  render(<ModelingOverviewDashboard />);
  expect(await screen.findByRole('heading', { name: '继续上次的建模工作' })).toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole('button', { name: /物理模型.*9/ })).toBeInTheDocument());
  expect(screen.getByRole('button', { name: /待继续设计.*3/ })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /逻辑模型.*1/ })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /业务过程.*6/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '新建物理模型' })).not.toBeInTheDocument();
  expect(screen.queryByPlaceholderText('搜索模型名称或编码')).not.toBeInTheDocument();
  expect(screen.queryByText('标签筛选')).not.toBeInTheDocument();
  expect(screen.queryByText('列设置')).not.toBeInTheDocument();
  expect(mockModels).toHaveBeenCalledWith({ pageNo: 1, pageSize: 5 });
  expect(mockModels).toHaveBeenCalledWith({ pageNo: 1, pageSize: 5, status: 'DRAFT' });
  expect(mockModels).toHaveBeenCalledWith({ pageNo: 1, pageSize: 1, layerCode: 'ODS' });
  expect(mockModels).not.toHaveBeenCalledWith(expect.objectContaining({ layerCode: 'DIM' }));
});

it('opens draft directly and preserves layer and draft scopes on physical-catalog links', async () => {
  render(<ModelingOverviewDashboard />);
  const work = await screen.findByRole('region', { name: '待继续的物理模型草稿' });
  fireEvent.click(await within(work).findByRole('button', { name: /订单 ODS/ }));
  expect(mockPush).toHaveBeenCalledWith('/modeling/models/101');
  const layers = screen.getByRole('region', { name: '数仓分层建设分布' });
  fireEvent.click(within(layers).getByRole('button', { name: /ODS/ }));
  expect(mockPush).toHaveBeenCalledWith('/modeling?view=physical&layer=ODS');
  fireEvent.click(screen.getByRole('button', { name: /查看全部草稿/ }));
  expect(mockPush).toHaveBeenCalledWith('/modeling?view=physical&status=DRAFT');
});

it('never displays zero for owner failures, while successful empty results may show zero', async () => {
  mockLogical.mockRejectedValue(new Error('logical unavailable'));
  mockModels.mockImplementation(async (query: { status?: string; layerCode?: string }) => {
    if (query.status) throw new Error('draft unavailable');
    if (query.layerCode === 'ODS') throw new Error('layer unavailable');
    return { bizData: [], pagination: { total: 0 } };
  });
  render(<ModelingOverviewDashboard />);
  expect(await screen.findByText('部分建模概况暂不可读取')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /物理模型.*0/ })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /逻辑模型.*—/ })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /待继续设计.*—/ })).toBeInTheDocument();
  const layerPanel = screen.getByRole('region', { name: '数仓分层建设分布' });
  expect(within(layerPanel).getByRole('button', { name: /ODS/ })).toHaveTextContent('—');
});

it('keeps prior project data hidden immediately after switching the active project', async () => {
  const { rerender } = render(<ModelingOverviewDashboard />);
  await waitFor(() => expect(screen.getByRole('button', { name: /物理模型.*9/ })).toBeInTheDocument());
  mockProjectId = 99;
  mockModels.mockImplementation(() => new Promise(() => {}));
  rerender(<ModelingOverviewDashboard />);
  expect(screen.queryByRole('button', { name: /物理模型.*9/ })).not.toBeInTheDocument();
  expect(screen.getByText('当前项目 #99')).toBeInTheDocument();
});

it('does not fabricate layer configuration or process count without semantic read permission', async () => {
  mockSemanticAllowed = false;
  render(<ModelingOverviewDashboard />);
  await waitFor(() => expect(screen.getByRole('button', { name: /物理模型.*9/ })).toBeInTheDocument());
  expect(mockProcesses).not.toHaveBeenCalled();
  expect(mockLayers).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: /业务过程/ })).toBeDisabled();
  const layerPanel = screen.getByRole('region', { name: '数仓分层建设分布' });
  expect(within(layerPanel).getByRole('button', { name: /ODS/ })).toHaveTextContent('—');
  expect(within(layerPanel).getByRole('button', { name: /ODS/ })).not.toHaveTextContent('未配置');
});
