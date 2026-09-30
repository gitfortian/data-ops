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

describe('task-oriented sidebar', () => {
  it('opens only the active capability and specialist, with hidden branches absent from keyboard navigation', () => {
    render(React.createElement(SidebarNavigation, props));
    expect(screen.getByRole('link', { name: '数据表监控' }).getAttribute('aria-current')).toBe('page');
    expect(screen.queryByRole('link', { name: '业务域' })).toBeNull();
    expect(screen.queryByRole('link', { name: '主数据建模' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '标准、指标与建模' }));
    expect(screen.queryByRole('link', { name: '数据表监控' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '业务语义' }));
    expect(screen.getByRole('link', { name: '业务域' }).getAttribute('href')).toBe('/semantic/domains');
  });

  it('resets expansion when navigation moves to another detail context', () => {
    const { rerender } = render(React.createElement(SidebarNavigation, props));
    rerender(React.createElement(SidebarNavigation, { ...props, activeId: 'consumption-catalog', activeGroupPath: ['data-analysis'] }));
    expect(screen.getByRole('link', { name: '数据产品目录' }).getAttribute('aria-current')).toBe('page');
    expect(screen.queryByRole('link', { name: '数据表监控' })).toBeNull();
    expect(screen.queryByRole('link', { name: '调用方与密钥' })).toBeNull();
  });

  it('keeps collapsed navigation at domain level and opens specialist pages in a popup', async () => {
    const { container } = render(React.createElement(SidebarNavigation, { ...props, compact: true }));
    expect(within(container).getAllByRole('button')).toHaveLength(6);
    expect(within(container).queryByRole('link', { name: '数据表监控' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '数据接入与集成' }));
    await waitFor(() => expect(screen.getByRole('link', { name: '文件资源' })).toBeTruthy());
    expect(screen.getByRole('link', { name: '文件资源' }).getAttribute('href')).toBe('/resource-management');
    expect(within(container).queryByRole('link', { name: '文件资源' })).toBeNull();
  });

  it('renders task order across routes and groups, not all pages before all groups', () => {
    render(React.createElement(SidebarNavigation, { ...props, activeId: 'modeling-workspace', activeGroupPath: ['modeling'] }));
    const domain = screen.getByRole('button', { name: '标准、指标与建模' }).parentElement!;
    const items = within(domain).getAllByRole('button').map((item) => item.getAttribute('aria-label'));
    expect(items).toEqual(['标准、指标与建模', '业务语义', '指标']);
    const semantic = within(domain).getByRole('button', { name: '业务语义' });
    const model = within(domain).getByRole('link', { name: '模型工作台' });
    expect(semantic.compareDocumentPosition(model) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});
