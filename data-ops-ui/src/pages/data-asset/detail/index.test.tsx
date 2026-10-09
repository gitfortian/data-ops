import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { history } from '@umijs/max';
import AssetDetail from './index';

let mockProjectId = 42;
let mockAssetId = '7';
let mockPermissions = ['data-asset:read', 'agent:chat:run'];
const mockDetail = jest.fn();
let mockUsage: Record<string, unknown> | null = null;
const asset = (id: number) => ({ asset: { id, name: `资产 ${id}`, sourceType: 'METADATA', sourceId: String(id), assetType: 'TABLE', status: 'PUBLISHED' }, sections: {} });
jest.mock('@umijs/max', () => ({ history: { push: jest.fn(), back: jest.fn() }, useParams: () => ({ id: mockAssetId }) }));
jest.mock('@/contexts/SecurityProjectContext', () => ({ useSecurityProject: () => ({ currentProject: { id: mockProjectId } }) }));
jest.mock('@/hooks/usePermissionAccess', () => ({ usePermissionAccess: () => ({
  permissionCodes: mockPermissions, can: (permission: string) => mockPermissions.includes(permission),
}) }));
jest.mock('@/components/ai/GovernanceSuggestionPanel', () => () => null);
jest.mock('@/components/UserSelect', () => () => null);
jest.mock('../components/OfflineModal', () => () => null);
jest.mock('../components/PublishPrecheckModal', () => () => null);
jest.mock('../components/StatusFlowStrip', () => () => null);
jest.mock('../components/HealthRing', () => () => null);
jest.mock('@/components/ui', () => ({ YakEmpty: ({ title }: any) => <div>{title}</div> }));
// Keep the page's reads and entry lifecycle real; jsdom cannot parse Ant tabs' escaped utility selectors.
jest.mock('antd', () => {
  const React = require('react');
  const Box = ({ children }: any) => React.createElement('div', null, children);
  const Input = Object.assign(() => null, { TextArea: () => null });
  return { Space: Box, Card: Box, Tag: Box, Tooltip: Box,
    Tabs: ({ items }: any) => React.createElement('div', null, items.find((item: any) => item.key === 'usage')?.children),
    Descriptions: ({ items }: any) => React.createElement('div', null, items.map((item: any) => React.createElement('div', { key: item.key }, item.children))), Input, Select: () => null,
    Button: ({ children, onClick, disabled }: any) => React.createElement('button', { onClick, disabled }, children),
    Alert: ({ message, action }: any) => React.createElement('div', null, message, action), message: { success: jest.fn(), error: jest.fn() },
  };
});
jest.mock('@/services/data-asset/api', () => ({
  getAssetDetail: (...args: any[]) => mockDetail(...args),
  getAssetSection: jest.fn(async (_id: number, section: string) => section === 'USAGE' && mockUsage ? mockUsage : { status: 'UNAVAILABLE' }),
  getAssetSourceAttributes: jest.fn(async () => ({ status: 'UNAVAILABLE' })),
  getAssetTags: jest.fn(async () => []), listAssetTags: jest.fn(async () => []), getDirectoryTree: jest.fn(async () => []),
  reportAssetView: jest.fn(async () => undefined),
}));

beforeEach(() => {
  jest.clearAllMocks(); mockProjectId = 42; mockAssetId = '7'; mockPermissions = ['data-asset:read', 'agent:chat:run'];
  mockUsage = null;
  mockDetail.mockImplementation(async (id: number) => asset(id));
});

it('offers a fixed impact entry after loading and only navigates on explicit click', async () => {
  render(<AssetDetail />);
  const entry = await screen.findByRole('button', { name: 'AI 影响说明' });
  expect(history.push).not.toHaveBeenCalled();
  fireEvent.click(entry);
  expect(history.push).toHaveBeenCalledWith('/ai-agent?assetId=7&purpose=ASSET_IMPACT');
});

it('discards old project and route data, failed reads and late results', async () => {
  const view = render(<AssetDetail />); await screen.findByRole('button', { name: 'AI 影响说明' });
  let resolveOld: (value: unknown) => void = () => undefined;
  mockDetail.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }));
  mockProjectId = 43; view.rerender(<AssetDetail />);
  expect(screen.queryByRole('button', { name: 'AI 影响说明' })).not.toBeInTheDocument();
  mockDetail.mockRejectedValueOnce(new Error('source unavailable'));
  mockAssetId = '8'; view.rerender(<AssetDetail />);
  await screen.findByText('资产详情读取失败');
  await act(async () => { resolveOld(asset(7)); });
  expect(screen.queryByRole('button', { name: 'AI 影响说明' })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新读取' }));
  fireEvent.click(await screen.findByRole('button', { name: 'AI 影响说明' }));
  expect(history.push).toHaveBeenCalledWith('/ai-agent?assetId=8&purpose=ASSET_IMPACT');
});

it('removes the entry on permission revocation and rejects a mismatched source identity', async () => {
  const view = render(<AssetDetail />); await screen.findByRole('button', { name: 'AI 影响说明' });
  mockPermissions = ['data-asset:read']; view.rerender(<AssetDetail />);
  await waitFor(() => expect(mockDetail).toHaveBeenCalledTimes(2));
  expect(screen.queryByRole('button', { name: 'AI 影响说明' })).not.toBeInTheDocument();
  mockDetail.mockResolvedValueOnce(asset(9)); mockPermissions = ['data-asset:read', 'agent:chat:run']; view.rerender(<AssetDetail />);
  await screen.findByText('资产 9');
  expect(screen.queryByRole('button', { name: 'AI 影响说明' })).not.toBeInTheDocument();
});

it.each(['EMPTY', 'OK'])('renders %s consumption scope in the original section without replacing unknown with zero', async (status) => {
  mockUsage = { status: 'OK', summary: { businessConsumption: { status,
    consumerCount: status === 'EMPTY' ? 0 : 1, successfulUsageCount: status === 'EMPTY' ? 0 : null,
    activeSubscriptionCount: status === 'EMPTY' ? 0 : 1,
    subscriptionState: status === 'EMPTY' ? 'EMPTY' : 'READY', usageState: status === 'EMPTY' ? 'EMPTY' : 'UNAVAILABLE',
    subscriptionWindowLimit: 200, usageWindowLimit: 200,
    subscriptionWindowState: 'WITHIN_LIMIT', usageWindowState: status === 'EMPTY' ? 'WITHIN_LIMIT' : 'UNKNOWN',
    sourceReconciliation: 'NOT_PERFORMED',
  } } };
  render(<AssetDetail />);
  await screen.findByText(/本次未同步原始来源/);
  expect(screen.getByText(status === 'EMPTY' ? /成功使用 0 次，活动订阅 0 个/ : /成功使用 未知 次，活动订阅 1 个/)).toBeInTheDocument();
  expect(screen.getByText(/有效订阅来源：.*窗口最多 200 行.*不证明历史完整/)).toBeInTheDocument();
});
