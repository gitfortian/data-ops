import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import AssetInventory from './index';

let mockProjectId = 42;
let mockTab = 'publish';
let mockPermissions = ['data-asset:read', 'data-asset:update'];
const mockPageAssets = jest.fn();
const mockPageChanges = jest.fn();
const mockReconcile = jest.fn();
const row = (id: number, name: string) => ({ id, name, assetName: name, assetKey: "table:test:" + name, sourceType: name, status: 'PENDING' });
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  useSearchParams: () => [new URLSearchParams({ tab: mockTab }), jest.fn()],
}));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({
    permissionCodes: mockPermissions,
    can: (code: string) => mockPermissions.includes(code),
  }),
}));
jest.mock('@/services/data-asset/api', () => ({
  pageAssets: (...args: unknown[]) => mockPageAssets(...args),
  pageChanges: (...args: unknown[]) => mockPageChanges(...args),
  getReconcileStatus: (...args: unknown[]) => mockReconcile(...args),
  confirmChange: jest.fn(),
  ignoreChange: jest.fn(),
  triggerReconcile: jest.fn(),
}));
jest.mock('@/components/ui', () => {
  const React = require('react');
  return {
    YakButton: ({ children, onClick, disabled }: any) =>
      React.createElement('button', { onClick, disabled }, children),
    YakEmpty: ({ title, description }: any) =>
      React.createElement('div', null, title, description),
  };
});
jest.mock('antd', () => {
  const React = require('react');
  const Box = ({ children }: any) => React.createElement('div', null, children);
  const Search = ({ onSearch }: any) =>
    React.createElement('button', { onClick: () => onSearch('different-filter') }, '搜索其它资产');
  const Table = ({ dataSource = [], locale, pagination }: any) =>
    React.createElement('div', null,
      dataSource.map((item: any) =>
        React.createElement('span', { key: item.id || item.sourceType }, item.assetName || item.name || item.sourceType)),
      dataSource.length === 0 && locale?.emptyText,
      pagination && React.createElement('button',
        { onClick: () => pagination.onChange(2, 20) }, '下一页'));
  const Segmented = ({ options }: any) =>
    React.createElement('div', null, options.map((o: any) =>
      React.createElement('span', { key: o.value || o }, o.label || o.value || o)));
  const Alert = ({ message, description, action }: any) =>
    React.createElement('div', { role: 'alert' }, message, description, action);
  return {
    Alert, Input: { Search }, Modal: { confirm: jest.fn() },
    message: { success: jest.fn() },
    Segmented, Select: Box, Space: Box, Table, Tag: Box, Tooltip: Box,
  };
});
jest.mock('../components/AssetStatusTag', () => () => null);
jest.mock('../components/HealthRing', () => () => null);
jest.mock('../components/ManualRegisterModal', () => () => null);
jest.mock('../components/PublishPrecheckModal', () => () => null);

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 42;
  mockTab = 'publish';
  mockPermissions = ['data-asset:read', 'data-asset:update'];
  mockPageAssets.mockResolvedValue({ records: [row(1, 'default')], total: 1 });
  mockPageChanges.mockResolvedValue({ records: [], total: 0 });
  mockReconcile.mockResolvedValue([]);
});

it('does not show old-filter success arriving after a later successful search', async () => {
  const old = deferred<{ records: ReturnType<typeof row>[]; total: number }>();
  mockPageAssets.mockReset();
  mockPageAssets.mockImplementationOnce(() => old.promise)
    .mockResolvedValueOnce({ records: [row(2, 'new-query')], total: 1 });
  render(<AssetInventory />);
  fireEvent.click(screen.getByRole('button', { name: '搜索其它资产' }));
  expect(await screen.findByText('new-query')).toBeInTheDocument();
  await act(async () => { old.resolve({ records: [row(1, 'stale-filter')], total: 1 }); });
  expect(screen.queryByText('stale-filter')).not.toBeInTheDocument();
  expect(screen.getByText('new-query')).toBeInTheDocument();
});

it('remounts on Project/role change and rejects old-project response', async () => {
  const old = deferred<{ records: ReturnType<typeof row>[]; total: number }>();
  mockPageAssets.mockReset();
  mockPageAssets.mockImplementationOnce(() => old.promise)
    .mockResolvedValueOnce({ records: [row(3, 'new-project')], total: 1 })
    .mockResolvedValueOnce({ records: [row(4, 'new-role')], total: 1 });
  const view = render(<AssetInventory />);
  mockProjectId = 43; view.rerender(<AssetInventory />);
  expect(await screen.findByText('new-project')).toBeInTheDocument();
  await act(async () => { old.resolve({ records: [row(2, 'old-project')], total: 1 }); });
  expect(screen.queryByText('old-project')).not.toBeInTheDocument();
  mockPermissions = ['data-asset:read'];
  view.rerender(<AssetInventory />);
  expect(await screen.findByText('new-role')).toBeInTheDocument();
  expect(screen.queryByText('new-project')).not.toBeInTheDocument();
});

it('clears prior results on forbidden new query, then recovers on explicit retry', async () => {
  mockPageAssets.mockReset();
  mockPageAssets.mockResolvedValueOnce({ records: [row(1, 'previous')], total: 1 })
    .mockRejectedValueOnce({ response: { status: 403 } })
    .mockResolvedValueOnce({ records: [row(2, 'retried')], total: 1 });
  render(<AssetInventory />);
  expect(await screen.findByText('previous')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '搜索其它资产' }));
  expect(await screen.findByText('无权读取待上架资产')).toBeInTheDocument();
  expect(screen.queryByText('previous')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByText('retried')).toBeInTheDocument();
});

it('rejects stale change-record page responses after pagination changes', async () => {
  mockTab = 'changes';
  const old = deferred<{ records: ReturnType<typeof row>[]; total: number }>();
  mockPageChanges.mockReset();
  mockPageChanges.mockImplementationOnce(() => old.promise)
    .mockResolvedValueOnce({ records: [row(7, 'new-changes')], total: 1 });
  render(<AssetInventory />);
  fireEvent.click(screen.getByRole('button', { name: '下一页' }));
  expect(await screen.findByText('new-changes')).toBeInTheDocument();
  await act(async () => { old.resolve({ records: [row(8, 'old-changes')], total: 1 }); });
  expect(screen.queryByText('old-changes')).not.toBeInTheDocument();
});

it('rejects delayed stale source-provider statuses after an explicit refresh', async () => {
  mockTab = 'sources';
  const old = deferred<ReturnType<typeof row>[]>();
  mockReconcile.mockReset();
  mockReconcile.mockImplementationOnce(() => old.promise)
    .mockResolvedValueOnce([row(7, 'new-provider')]);
  render(<AssetInventory />);
  fireEvent.click(screen.getByRole('button', { name: '刷新状态' }));
  expect(await screen.findByText('new-provider')).toBeInTheDocument();
  await act(async () => { old.resolve([row(8, 'old-provider')]); });
  expect(screen.queryByText('old-provider')).not.toBeInTheDocument();
});
