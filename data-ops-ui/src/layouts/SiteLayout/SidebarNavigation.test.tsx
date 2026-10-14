import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import {
  getMainNavigationGroups,
  getStandaloneNavigationRoutes,
} from '@/config/navigation';
import SidebarNavigation from './SidebarNavigation';

jest.mock('@umijs/max', () => ({
  Link: ({ to, children, ...props }: { to: string; children?: unknown }) =>
    jest.requireActual('react').createElement('a', { href: to, ...props }, children),
}));

const icons = {
  home: 'home', database: 'database', sync: 'sync', realtime: 'realtime',
  client: 'client', connector: 'connector', workflow: 'workflow', project: 'project',
  instance: 'instance', quality: 'quality', report: 'report', monitor: 'monitor',
  alarm: 'alarm', knowledge: 'knowledge', api: 'api', insight: 'insight', system: 'system',
};
const props = {
  groups: getMainNavigationGroups(['security:root']),
  standaloneRoutes: getStandaloneNavigationRoutes(['security:root']),
  icons,
  compact: false,
  activeId: 'data-quality-table-config',
  activeGroupPath: ['data-quality', 'data-asset'],
};

describe('IA-550 task-first sidebar', () => {
  it('opens the actual active task without forcing a nested specialist group or exposing hidden tool links', () => {
    render(React.createElement(SidebarNavigation, props));
    expect(screen.getByRole('link', { name: '数据表监控' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '质量治理' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.queryByRole('link', { name: '业务域' })).toBeNull();
    expect(screen.queryByRole('link', { name: '规则模板' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '标准、指标与建模' }));
    expect(screen.queryByRole('link', { name: '数据表监控' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '业务语义' }));
    expect(screen.getByRole('link', { name: '业务域' })).toHaveAttribute('href', '/semantic/domains');
    expect(screen.getByRole('link', { name: '业务过程' })).toHaveAttribute('href', '/semantic/processes');
    expect(screen.queryByRole('link', { name: '标准字段' })).toBeNull();
  });

  it('exposes every specialist through the same domain without putting it in the default list', () => {
    render(React.createElement(SidebarNavigation, {
      ...props, activeId: 'data-service-api', activeGroupPath: ['data-service', 'data-analysis'],
    }));
    expect(screen.getByRole('link', { name: 'API 服务' })).toHaveAttribute('href', '/data-service');
    expect(screen.queryByRole('link', { name: '调用方与密钥' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '数据消费与服务专业工具' }));
    expect(screen.getByRole('link', { name: '调用方与密钥' })).toHaveAttribute('href', '/data-service/access');
    expect(screen.getByRole('link', { name: '运行概览' })).toHaveAttribute('href', '/data-service/overview');
  });

  it('reveals an active standalone specialist on deep navigation and collapses the old domain', () => {
    const { rerender } = render(React.createElement(SidebarNavigation, props));
    rerender(React.createElement(SidebarNavigation, {
      ...props,
      activeId: 'metric-impact',
      activeGroupPath: ['metric', 'modeling'],
    }));
    expect(screen.getByRole('link', { name: '影响分析' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByRole('link', { name: '数据表监控' })).toBeNull();
    expect(screen.getByRole('button', { name: '标准、指标与建模专业工具' }))
      .toHaveAttribute('aria-expanded', 'true');
  });

  it('keeps the collapsed domain menu usable with its task views and specialists', async () => {
    const { container } = render(React.createElement(SidebarNavigation, { ...props, compact: true }));
    expect(within(container).getAllByRole('button')).toHaveLength(6);
    fireEvent.click(screen.getByRole('button', { name: '数据接入与集成' }));
    await waitFor(() => expect(screen.getByRole('link', { name: '文件资源' })).toBeInTheDocument());
    expect(screen.getByRole('link', { name: '文件资源' })).toHaveAttribute('href', '/resource-management');
    expect(within(container).queryByRole('link', { name: '文件资源' })).toBeNull();
  });

  it('notifies the host when a mobile task link is chosen so the drawer can close', () => {
    const onNavigate = jest.fn();
    render(React.createElement(SidebarNavigation, {
      ...props, activeId: 'modeling-workspace',
      activeGroupPath: ['modeling'], onNavigate,
    }));
    fireEvent.click(screen.getByRole('link', { name: '模型工作台' }));
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });

  it('supports independently authorized tools for roles without any default task', () => {
    const filtered = getMainNavigationGroups(
      ['resource:sql-execution:read'], ['sql-execution-audit'],
    );
    render(React.createElement(SidebarNavigation, {
      ...props, groups: filtered, standaloneRoutes: [],
      activeId: 'sql-execution-audit', activeGroupPath: ['integration'],
    }));
    expect(screen.getByRole('link', { name: 'SQL 执行记录' })).toHaveAttribute('href', '/sql-execution-audit');
    expect(screen.getByRole('link', { name: 'SQL 执行记录' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByRole('link', { name: '数据源' })).toBeNull();
  });
});
