import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import AssetOverview from './index';

let mockProjectId = 42;
let mockPermissions = ['data-asset:read'];
const mockOverview = jest.fn();
const makeOverview = (total: number) => ({
  generatedAt: '2026-10-09T10:00:00',
  kpis: {
    total, published: total, pending: 0, added30d: 0,
    ownerCoverage: 0, classifiedRate: 0, gradeACount: 0, gradeDCount: 0,
  },
  todos: { pendingPublish: 0, openChanges: 0, noOwner: 0, gradeD: 0, sourceGone: 0 },
  distributions: { status: [], grade: [], type: [], layer: [] },
  recentListed: [], recentOffline: [],
});
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((yes) => { resolve = yes; });
  return { promise, resolve };
};

jest.mock('@umijs/max', () => ({ history: { push: jest.fn() } }));
jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({ currentProject: { id: mockProjectId } }),
}));
jest.mock('@/hooks/usePermissionAccess', () => ({
  usePermissionAccess: () => ({ permissionCodes: mockPermissions }),
}));
jest.mock('@/services/data-asset/api', () => ({
  getAssetOverview: (...args: unknown[]) => mockOverview(...args),
}));
jest.mock('@/components/ui', () => ({
  YakButton: ({ children, onClick, disabled }: any) =>
    React.createElement('button', { onClick, disabled }, children),
  YakEmpty: ({ title }: any) => React.createElement('div', null, title),
}));
jest.mock('antd', () => {
  const React = require('react');
  const Box = ({ children }: any) => React.createElement('div', null, children);
  return {
    Alert: ({ message, description, action }: any) =>
      React.createElement('div', { role: 'alert' }, message, description, action),
    Button: ({ children, onClick }: any) => React.createElement('button', { onClick }, children),
    Card: ({ children, loading, title }: any) =>
      React.createElement('section', null, title, loading ? '正在读取' : children),
    Space: Box, Tag: Box, Tooltip: Box,
  };
});

beforeEach(() => {
  jest.clearAllMocks();
  mockProjectId = 42;
  mockPermissions = ['data-asset:read'];
  mockOverview.mockResolvedValue(makeOverview(0));
});

it('renders a genuine successful empty overview rather than treating zero as an error', async () => {
  render(<AssetOverview />);
  expect(await screen.findByText('资产待上架 0')).toBeInTheDocument();
  expect(screen.getByText('台账总量')).toBeInTheDocument();
  expect(screen.getAllByText('暂无数据')).toHaveLength(4);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('shows permission denial without a fake zero KPI, empty list or to-do', async () => {
  mockOverview.mockReset().mockRejectedValueOnce({ response: { status: 403 } })
    .mockResolvedValueOnce(makeOverview(5));
  render(<AssetOverview />);
  expect(await screen.findByText('无权读取资产概览')).toBeInTheDocument();
  expect(screen.queryByText('资产待上架 0')).not.toBeInTheDocument();
  expect(screen.queryByText('暂无数据')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByText('5')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('drops previously successful KPIs on a failed refresh and recovers on retry', async () => {
  mockOverview.mockReset().mockResolvedValueOnce(makeOverview(13))
    .mockRejectedValueOnce(new Error('upstream unavailable'))
    .mockResolvedValueOnce(makeOverview(17));
  render(<AssetOverview />);
  expect(await screen.findByText('13')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  expect(await screen.findByText('资产概览读取失败')).toBeInTheDocument();
  expect(screen.queryByText('13')).not.toBeInTheDocument();
  expect(screen.queryByText('资产待上架 0')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  expect(await screen.findByText('17')).toBeInTheDocument();
});

it('discards an older refresh response even when it arrives last', async () => {
  const old = deferred<ReturnType<typeof makeOverview>>();
  mockOverview.mockReset().mockImplementationOnce(() => old.promise)
    .mockResolvedValueOnce(makeOverview(8));
  render(<AssetOverview />);
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  expect(await screen.findByText('8')).toBeInTheDocument();
  await act(async () => { old.resolve(makeOverview(99)); });
  expect(screen.queryByText('99')).not.toBeInTheDocument();
  expect(screen.getByText('8')).toBeInTheDocument();
});

it('remounts across Project and permission changes to prevent old-project metrics', async () => {
  const old = deferred<ReturnType<typeof makeOverview>>();
  mockOverview.mockReset().mockImplementationOnce(() => old.promise)
    .mockResolvedValueOnce(makeOverview(23))
    .mockResolvedValueOnce(makeOverview(31));
  const view = render(<AssetOverview />);
  mockProjectId = 43;
  view.rerender(<AssetOverview />);
  expect(await screen.findByText('23')).toBeInTheDocument();
  await act(async () => { old.resolve(makeOverview(88)); });
  expect(screen.queryByText('88')).not.toBeInTheDocument();
  mockPermissions = [];
  view.rerender(<AssetOverview />);
  expect(await screen.findByText('31')).toBeInTheDocument();
  expect(screen.queryByText('23')).not.toBeInTheDocument();
});
