import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { history } from '@umijs/max';
import SiteLayout from './index';

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
    useLocation: () => ({ pathname: '/home', search: '' }),
    getLocale: () => 'zh-CN',
    setLocale: jest.fn(),
    history: { push: jest.fn(), replace: jest.fn() },
    Link: ({ to, children, ...props }: { to: string; children?: React.ReactNode }) =>
      jest.requireActual('react').createElement('a', { href: to, ...props }, children),
    Outlet: () => null,
  };
});

jest.mock('@/services/security/messages', () => ({
  getUnreadMessageCount: () => Promise.resolve(0),
  MESSAGE_COUNT_CHANGED_EVENT: 'test-message-count',
}));
jest.mock('@/services/security/account', () => ({ logout: jest.fn(), getCurrentUser: jest.fn() }));

beforeEach(() => {
  jest.clearAllMocks();
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
