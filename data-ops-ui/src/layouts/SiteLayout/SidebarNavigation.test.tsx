import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import { getMainNavigationGroups, getStandaloneNavigationRoutes } from '@/config/navigation';
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
  activeId: 'semantic-standards',
  activeGroupPath: ['semantic', 'modeling'],
};

describe('IA-550 fixed global rail + independent business context rail', () => {
  it('keeps five domains visible while revealing only one full set of context links', () => {
    render(React.createElement(SidebarNavigation, props));
    const global = screen.getByLabelText('全局导航');
    expect(screen.getByTestId('dual-rail-navigation')).toBeInTheDocument();
    expect(within(global).getByRole('link', { name: '首页' })).toHaveAttribute('href', '/home');
    expect(within(global).getByRole('link', { name: '我的待办' })).toHaveAttribute('href', '/approval/todo');
    expect(within(global).getByRole('button', { name: '标准、指标与建模' })).toHaveAttribute('aria-expanded', 'true');
    expect(within(global).getAllByRole('button')).toHaveLength(6);
    const context = screen.getByRole('navigation', { name: '标准、指标与建模页面' });
    expect(within(context).getByRole('link', { name: '数据标准' })).toHaveAttribute('href', '/semantic/standards');
    expect(within(context).getByRole('link', { name: '业务过程' })).toHaveAttribute('href', '/semantic/processes');
    expect(within(context).getByRole('link', { name: '数仓分层' })).toHaveAttribute('href', '/semantic/layers');
    expect(within(context).getByRole('link', { name: '影响分析' })).toHaveAttribute('href', '/metric/impact');
    expect(within(context).getByRole('link', { name: '模型总览' })).toHaveAttribute('href', '/modeling');
    expect(within(context).getByRole('link', { name: '逻辑模型' })).toHaveAttribute('href', '/modeling/logical');
    expect(within(context).getByRole('link', { name: '物理模型' })).toHaveAttribute('href', '/modeling?view=physical');
    expect(within(context).getByRole('link', { name: '数据标准' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText(/更多 [0-9]+ 项/)).not.toBeInTheDocument();
    expect(screen.queryByText('专业工具')).not.toBeInTheDocument();
  });


  it('does not expose modeling subpages without the original Modeling authorization', () => {
    const groups = getMainNavigationGroups(['semantic:read'], ['semantic-processes']);
    render(React.createElement(SidebarNavigation, {
      ...props, groups, standaloneRoutes: [], activeId: 'semantic-processes',
    }));
    // Without the Modeling grant the entire section may legitimately be absent,
    // so do not assert a context rail that the RBAC filter did not create.
    expect(groups.every(group => !group.routes.some(route => route.id === 'modeling-workspace'))).toBe(true);
    expect(screen.queryByRole('link', { name: '模型总览' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '逻辑模型' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '物理模型' })).not.toBeInTheDocument();
  });

  it('switches the right rail without hiding the five global domains', () => {
    render(React.createElement(SidebarNavigation, props));
    const global = screen.getByLabelText('全局导航');
    fireEvent.click(within(global).getByRole('button', { name: '数据接入与集成' }));
    const context = screen.getByRole('navigation', { name: '数据接入与集成页面' });
    expect(within(context).getByRole('link', { name: '数据源' })).toHaveAttribute('href', '/data-source');
    expect(within(context).getByRole('link', { name: 'SQL 执行记录' })).toHaveAttribute('href', '/sql-execution-audit');
    expect(within(context).queryByRole('link', { name: '数据标准' })).not.toBeInTheDocument();
    expect(within(global).getByRole('button', { name: '标准、指标与建模' })).toBeInTheDocument();
  });

  it('reveals the original domain and deep-linked page on browser return and changes of role', () => {
    const { rerender } = render(React.createElement(SidebarNavigation, props));
    rerender(React.createElement(SidebarNavigation, {
      ...props, activeId: 'data-security-audit',
      activeGroupPath: ['data-security', 'data-asset'],
    }));
    const context = screen.getByRole('navigation', { name: '数据资产与治理页面' });
    expect(within(context).getByRole('link', { name: '访问审计' })).toHaveAttribute('aria-current', 'page');
    expect(within(context).getByRole('link', { name: '访问审计' })).toHaveAttribute('href', '/data-security/audit');
  });

  it('keeps specialist-only users in their authorized context without fake parent grants', () => {
    const groups = getMainNavigationGroups(['resource:sql-execution:read'], ['sql-execution-audit']);
    render(React.createElement(SidebarNavigation, {
      ...props, groups, standaloneRoutes: [], activeId: 'sql-execution-audit',
      activeGroupPath: ['integration'],
    }));
    const context = screen.getByRole('navigation', { name: '数据接入与集成页面' });
    expect(within(context).getByRole('link', { name: 'SQL 执行记录' })).toHaveAttribute('href', '/sql-execution-audit');
    expect(within(context).queryByRole('link', { name: '数据源' })).not.toBeInTheDocument();
  });

  it('uses only icons with an authorized grouped direct-link popup in collapsed mode', async () => {
    const { container } = render(React.createElement(SidebarNavigation, { ...props, compact: true }));
    expect(screen.getByTestId('dual-rail-compact')).toBeInTheDocument();
    expect(within(container).getAllByRole('button')).toHaveLength(6);
    fireEvent.click(screen.getByRole('button', { name: '标准、指标与建模' }));
    await waitFor(() => expect(screen.getByRole('link', { name: '业务域' })).toBeInTheDocument());
    expect(screen.getByRole('link', { name: '指标管理' })).toHaveAttribute('href', '/metric/manage');
    expect(screen.getByRole('link', { name: '逻辑模型' })).toHaveAttribute('href', '/modeling/logical');
    expect(screen.getByRole('link', { name: '物理模型' })).toHaveAttribute('href', '/modeling?view=physical');
    expect(within(container).queryByRole('link', { name: '业务域' })).toBeNull();
  });

  it('closes the mobile drawer through onNavigate after selecting a right-rail page', () => {
    const onNavigate = jest.fn();
    render(React.createElement(SidebarNavigation, { ...props, onNavigate }));
    fireEvent.click(screen.getByRole('link', { name: '模型总览' }));
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });
});
