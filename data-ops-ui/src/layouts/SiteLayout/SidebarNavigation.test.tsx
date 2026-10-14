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
  activeId: 'semantic-standards',
  activeGroupPath: ['semantic', 'modeling'],
};

describe('owner-context menu, two levels maximum', () => {
  it('has no duplicate task labels, nested task disclosures or standalone professional bucket', () => {
    render(React.createElement(SidebarNavigation, props));
    expect(screen.getByRole('button', { name: '标准、指标与建模' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('标准规范')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '数据标准' })).toHaveAttribute('href', '/semantic/standards');
    expect(screen.getByRole('link', { name: '数据标准' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '标准字段' })).toHaveAttribute('href', '/semantic/fields');
    expect(screen.getByRole('link', { name: '业务域' })).toHaveAttribute('href', '/semantic/domains');
    expect(screen.getByRole('link', { name: '业务过程' })).toHaveAttribute('href', '/semantic/processes');
    expect(screen.getByRole('link', { name: '模型工作台' })).toHaveAttribute('href', '/modeling');
    expect(screen.queryByText('专业工具')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '数据标准' })).not.toBeInTheDocument();
  });

  it('expands only contextual extras, never a miscellaneous tools submenu', () => {
    render(React.createElement(SidebarNavigation, props));
    expect(screen.queryByRole('link', { name: '数仓分层' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '标准规范展开其他页面' }));
    expect(screen.getByRole('link', { name: '数仓分层' })).toHaveAttribute('href', '/semantic/layers');
    expect(screen.queryByText('专业工具')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '标准规范收起其他页面' }));
    expect(screen.queryByRole('link', { name: '数仓分层' })).not.toBeInTheDocument();
  });

  it('automatically locates an old deep link in its owning section and changes domains cleanly', () => {
    const { rerender } = render(React.createElement(SidebarNavigation, props));
    rerender(React.createElement(SidebarNavigation, {
      ...props,
      activeId: 'metric-impact',
      activeGroupPath: ['metric', 'modeling'],
    }));
    expect(screen.getByRole('link', { name: '影响分析' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText('专业工具')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '数据接入与集成' }));
    expect(screen.queryByRole('link', { name: '影响分析' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '数据源' })).toHaveAttribute('href', '/data-source');
  });

  it('shows independently authorized professional page as a regular direct link', () => {
    const filtered = getMainNavigationGroups(['resource:sql-execution:read'], ['sql-execution-audit']);
    render(React.createElement(SidebarNavigation, {
      ...props, groups: filtered, standaloneRoutes: [], activeId: 'sql-execution-audit',
      activeGroupPath: ['integration'],
    }));
    expect(screen.getByRole('link', { name: 'SQL 执行记录' })).toHaveAttribute('href', '/sql-execution-audit');
    expect(screen.queryByRole('link', { name: '数据源' })).not.toBeInTheDocument();
    expect(screen.queryByText('专业工具')).not.toBeInTheDocument();
  });

  it('keeps collapsed menu direct, grouped by a non-interactive context label', async () => {
    const { container } = render(React.createElement(SidebarNavigation, { ...props, compact: true }));
    expect(within(container).getAllByRole('button')).toHaveLength(6);
    fireEvent.click(screen.getByRole('button', { name: '标准、指标与建模' }));
    await waitFor(() => expect(screen.getByRole('link', { name: '业务域' })).toBeInTheDocument());
    expect(screen.getByRole('link', { name: '业务域' })).toHaveAttribute('href', '/semantic/domains');
    expect(screen.getByRole('link', { name: '指标管理' })).toHaveAttribute('href', '/metric/manage');
    expect(within(container).queryByRole('link', { name: '业务域' })).toBeNull();
  });

  it('closes the mobile drawer via onNavigate when a direct page is selected', () => {
    const onNavigate = jest.fn();
    render(React.createElement(SidebarNavigation, { ...props, onNavigate }));
    fireEvent.click(screen.getByRole('link', { name: '模型工作台' }));
    expect(onNavigate).toHaveBeenCalledTimes(1);
  });
});
