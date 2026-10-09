import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { history } from '@umijs/max';
import SiteLayout from './index';
import { getApplicationFeatures } from '@/services/applicationFeatures';

jest.mock('@/services/applicationFeatures', () => ({ getApplicationFeatures: jest.fn() }));
let mockPathname = '/home';
const mockOutletMounted = jest.fn();

jest.mock('@umijs/max', () => {
  const project = { id: 1, projectName: '测试空间', projectCode: 'test' };
  const initialState = {
    currentUser: {
      userid: '1', name: '测试用户',
      permissionCodes: ['security:root'], projectList: [project],
    },
    securityProject: project,
  };
  const setInitialState = jest.fn();
  return {
    useModel: () => ({ initialState, setInitialState }),
    useLocation: () => ({ pathname: mockPathname, search: '' }),
    getLocale: () => 'zh-CN',
    setLocale: jest.fn(),
    history: { push: jest.fn(), replace: jest.fn() },
    Link: ({ to, children, ...props }: { to: string; children?: React.ReactNode }) =>
      jest.requireActual('react').createElement('a', { href: to, ...props }, children),
    Outlet: () => { mockOutletMounted(); return null; },
  };
});

jest.mock('@/services/security/messages', () => ({
  getUnreadMessageCount: () => Promise.resolve(0),
  MESSAGE_COUNT_CHANGED_EVENT: 'test-message-count',
}));
jest.mock('@/services/security/account', () => ({ logout: jest.fn(), getCurrentUser: jest.fn() }));

beforeEach(() => {
  jest.clearAllMocks();
  mockPathname = '/home';
  jest.mocked(getApplicationFeatures).mockResolvedValue({ agentEnabled: true });
  window.matchMedia = jest.fn(() => ({
    matches: false, media: '', onchange: null,
    addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  }));
});

it('opens the workspace menu on click', async () => {
  render(<SiteLayout />);
  fireEvent.click(screen.getByRole('button', { name: '切换工作空间' }));
  await waitFor(() => expect(screen.getByRole('menuitem', { name: /管理工作空间/ })).toBeVisible());
});

it('opens the user menu on click', async () => {
  render(<SiteLayout />);
  fireEvent.click(screen.getByRole('button', { name: '打开用户菜单' }));
  await waitFor(() => expect(screen.getByRole('menuitem', { name: /退出登录/ })).toBeVisible());
});

it('navigates when workspace management is clicked', async () => {
  render(<SiteLayout />);
  fireEvent.click(screen.getByRole('button', { name: '切换工作空间' }));
  const item = await screen.findByRole('menuitem', { name: /管理工作空间/ });
  fireEvent.click(item);
  expect(history.push).toHaveBeenCalledWith('/system/projects');
});

it('navigates when user settings is clicked', async () => {
  render(<SiteLayout />);
  fireEvent.click(screen.getByRole('button', { name: '打开用户菜单' }));
  const item = await screen.findByRole('menuitem', { name: /^设置$/ });
  fireEvent.click(item);
  expect(history.push).toHaveBeenCalledWith('/settings');
});

it('hides the assistant entry and does not mount its page when the module is disabled', async () => {
  mockPathname = '/ai-agent';
  jest.mocked(getApplicationFeatures).mockResolvedValue({ agentEnabled: false });
  render(<SiteLayout />);
  expect(await screen.findByText('智能助手未开启')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '智能助手' })).not.toBeInTheDocument();
  expect(mockOutletMounted).not.toHaveBeenCalled();
});

it('waits for enablement before mounting the assistant and makes the entry clickable', async () => {
  mockPathname = '/ai-agent';
  let resolve!: (value: { agentEnabled: boolean }) => void;
  jest.mocked(getApplicationFeatures).mockReturnValue(new Promise(done => { resolve = done; }));
  render(<SiteLayout />);
  expect(screen.getByRole('status')).toHaveTextContent('正在确认智能助手是否可用');
  expect(screen.queryByRole('button', { name: '智能助手' })).not.toBeInTheDocument();
  expect(mockOutletMounted).not.toHaveBeenCalled();
  resolve({ agentEnabled: true });
  const entry = await screen.findByRole('button', { name: '智能助手' });
  fireEvent.click(entry);
  expect(history.push).toHaveBeenCalledWith('/ai-agent');
  expect(mockOutletMounted).toHaveBeenCalled();
});

it('distinguishes failed discovery from disabled and allows recovery after retry', async () => {
  mockPathname = '/ai-agent';
  jest.mocked(getApplicationFeatures).mockRejectedValueOnce(new Error('offline'));
  render(<SiteLayout />);
  expect(await screen.findByText('暂时无法确认智能助手是否可用')).toBeInTheDocument();
  expect(screen.queryByText('智能助手未开启')).not.toBeInTheDocument();
  expect(mockOutletMounted).not.toHaveBeenCalled();
  jest.mocked(getApplicationFeatures).mockResolvedValue({ agentEnabled: true });
  fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
  expect(await screen.findByRole('button', { name: '智能助手' })).toBeInTheDocument();
  expect(mockOutletMounted).toHaveBeenCalled();
});

it('can recheck a disabled deployment after the administrator enables the module', async () => {
  mockPathname = '/ai-agent';
  jest.mocked(getApplicationFeatures).mockResolvedValueOnce({ agentEnabled: false });
  render(<SiteLayout />);
  expect(await screen.findByText('智能助手未开启')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '重新检查' }));
  expect(await screen.findByRole('button', { name: '智能助手' })).toBeInTheDocument();
  expect(mockOutletMounted).toHaveBeenCalled();
});
