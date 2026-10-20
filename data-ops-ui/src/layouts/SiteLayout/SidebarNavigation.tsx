import {
  type NavigationGroupWithRoutes,
  type NavigationIconKey,
  type NavigationRoute,
} from '@/config/navigation';
import {
  getContextNavigationModel,
  type ContextNavigationModel,
} from '@/config/taskNavigation';
import { Link } from '@umijs/max';
import { Dropdown, type MenuProps } from 'antd';
import { type ReactNode, useEffect, useMemo, useState } from 'react';

interface SidebarNavigationProps {
  groups: NavigationGroupWithRoutes[];
  standaloneRoutes: NavigationRoute[];
  icons: Record<NavigationIconKey, ReactNode>;
  compact: boolean;
  activeId?: string;
  activeGroupPath: string[];
  onNavigate?: () => void;
}

/**
 * IA-550 / user-approved dual rail:
 * - global rail contains ONLY Home/Todo and existing authorized business domains
 * - context rail holds the selected domain's original, authorized page URLs
 * - captions are not links; there is no third level, "更多" or tool bucket
 * - collapsed mode retains the same routes in a single grouped popup
 */
export default function SidebarNavigation({
  groups, standaloneRoutes, icons, compact, activeId, activeGroupPath, onNavigate,
}: SidebarNavigationProps) {
  const activeDomain = activeGroupPath[activeGroupPath.length - 1];
  const domainNames: Record<string, string> = {
    integration: '数据接入', modeling: '标准与建模', development: '开发运行',
    'data-asset': '资产治理', 'data-analysis': '数据消费', system: '平台设置',
  };
  const models = useMemo(
    () => new Map(groups.map(group => [group.id, getContextNavigationModel(group)])),
    [groups],
  );
  const [selectedDomain, setSelectedDomain] = useState<string | undefined>(
    () => groups.find(group => group.id === activeDomain)?.id ?? groups[0]?.id,
  );
  // Keep navigation anchored to the active domain on deep links and back/forward
  // without forcing home/todo to wipe the operator's last selected domain.
  useEffect(() => {
    setSelectedDomain(previous =>
      groups.find(group => group.id === activeDomain)?.id
        ?? (groups.some(group => group.id === previous) ? previous : groups[0]?.id));
  }, [activeDomain, groups]);

  // Three direct links in the *existing* authorized SiteLayout context rail.
  // Derive all from the only existing Modeling menu grant; no duplicate menu
  // codes, Flyway grants, third-level tree or self-invented permissions.
  const expandedRoutes = (route: NavigationRoute) => route.id === 'modeling-workspace'
    ? [
      { id: 'model-overview', path: '/modeling', title: '模型总览' },
      { id: 'model-logical', path: '/modeling/logical', title: '逻辑模型' },
      { id: 'model-physical', path: '/modeling?view=physical', title: '物理模型' },
    ]
    : [{ id: route.id, path: route.path, title: route.title }];

  const isActiveModelingLink = (id: string, fallbackId: string) => {
    if (fallbackId !== 'modeling-workspace') return activeId === id;
    if (typeof window === 'undefined') return id === 'model-overview' && activeId === 'modeling-workspace';
    const pathname = window.location.pathname;
    const physical = new URLSearchParams(window.location.search).get('view') === 'physical';
    return id === 'model-logical' ? pathname === '/modeling/logical'
      : id === 'model-physical' ? pathname.startsWith('/modeling/models/') || (pathname === '/modeling' && physical)
      : pathname === '/modeling' && !physical;
  };

  const renderRoute = (route: NavigationRoute) => expandedRoutes(route).map(link => {
    const selected = isActiveModelingLink(link.id, route.id);
    return <Link
      key={link.id}
      to={link.path}
      title={link.title}
      onClick={onNavigate}
      aria-current={selected ? 'page' : undefined}
      className={[
        'flex min-h-9 w-full items-center rounded-md px-3 py-2 text-[13px] leading-5',
        'transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#475467]',
        selected
          ? 'bg-[rgba(254,44,85,0.07)] font-semibold text-[#fe2c55]'
          : 'text-[#475467] hover:bg-[#f4f5f7] hover:text-[#344054]',
      ].join(' ')}
    >{link.title}</Link>;
  });

  const popupItems = (group: NavigationGroupWithRoutes): MenuProps['items'] => {
    const model: ContextNavigationModel = models.get(group.id) ?? getContextNavigationModel(group);
    return model.sections.flatMap<NonNullable<MenuProps['items']>[number]>(section =>
      section.title && (section.routes.length > 1 || section.id === 'models')
        ? [{
            type: 'group' as const,
            key: 'section:' + group.id + ':' + section.id,
            label: section.title,
            children: section.routes.flatMap(route => expandedRoutes(route).map(link => ({
              key: link.id,
              label: <Link to={link.path} onClick={onNavigate}>{link.title}</Link>,
            }))),
          }]
        : section.routes.flatMap(route => expandedRoutes(route).map(link => ({
            key: link.id,
            label: <Link to={link.path} onClick={onNavigate}>{link.title}</Link>,
          }))),
    );
  };

  const globalButtonClass = (isSelected: boolean, collapsed = false) => [
    'flex w-full min-h-10 items-center gap-2 rounded-md border-0 text-left',
    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#475467]',
    'transition-colors',
    collapsed ? 'justify-center px-1' : 'px-3',
    isSelected ? 'bg-white font-semibold text-[#fe2c55] shadow-[0_1px_3px_rgba(16,24,40,0.06)]'
      : 'bg-transparent font-normal text-[#667085] hover:bg-white hover:text-[#344054]',
  ].join(' ');

  const standaloneLinks = (isCompact: boolean) => standaloneRoutes.map(route => <Link
    key={route.id}
    to={route.path}
    title={route.title}
    aria-label={route.title}
    onClick={onNavigate}
    aria-current={activeId === route.id ? 'page' : undefined}
    className={globalButtonClass(activeId === route.id, isCompact)}
  >
    {route.iconKey && <span className="flex h-5 w-5 shrink-0 items-center justify-center">{icons[route.iconKey]}</span>}
    {!isCompact && <span className="min-w-0 truncate text-[12px]">{route.title}</span>}
  </Link>);

  const domainControls = (isCompact: boolean) => groups.map((group, index) => {
    const selected = selectedDomain === group.id;
    const button = <button
      type="button"
      aria-label={group.title}
      title={group.title}
      aria-current={selected ? 'true' : undefined}
      aria-expanded={isCompact ? undefined : selectedDomain === group.id}
      aria-controls={isCompact ? undefined : 'navigation-context-panel'}
      aria-haspopup={isCompact ? 'menu' : undefined}
      onClick={isCompact ? undefined : () => setSelectedDomain(group.id)}
      className={globalButtonClass(selected && activeId !== 'home' && activeId !== 'approval-todo', isCompact)}
    >
      <span className="flex h-5 w-5 shrink-0 items-center justify-center">{icons[group.iconKey]}</span>
      {!isCompact && <>
        <span className="min-w-0 flex-1 truncate text-[12px]">{domainNames[group.id] ?? group.title}</span>
      </>}
    </button>;
    const content = isCompact
      ? <Dropdown key={group.id} trigger={['hover', 'click']} placement="bottomLeft"
          align={{ points: ['tl', 'tr'], offset: [8, 0] }}
          menu={{
            items: popupItems(group),
            selectedKeys: activeId ? [activeId] : [],
            style: { minWidth: 240, maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' },
          }}>
          {button}
        </Dropdown>
      : <div key={group.id}>{button}</div>;
    return <div key={group.id} className={index === groups.length - 1 && group.section === 'system'
      ? 'mt-auto border-t border-[#e5e7eb] pt-3' : undefined}>{content}</div>;
  });

  if (compact) return <div data-testid="dual-rail-compact" className="flex h-full flex-col gap-2">
    <div className="flex flex-col gap-1 border-b border-[#eaecf0] pb-3">{standaloneLinks(true)}</div>
    <div className="flex min-h-0 flex-1 flex-col gap-1">{domainControls(true)}</div>
  </div>;

  const currentGroup = groups.find(group => group.id === selectedDomain) ?? groups[0];
  const currentModel = currentGroup && (models.get(currentGroup.id) ?? getContextNavigationModel(currentGroup));

  return <div data-testid="dual-rail-navigation" className="flex h-full min-h-0 w-full overflow-hidden">
    <div className="flex h-full w-[146px] shrink-0 flex-col border-r border-[#e6e8ed] px-2 py-3"
      aria-label="全局导航">
      <div className="flex flex-col gap-1 border-b border-[#eaecf0] pb-3">
        {standaloneLinks(false)}
      </div>
      <div className="mt-3 flex min-h-0 flex-1 flex-col gap-1">
        {domainControls(false)}
      </div>
    </div>
    <div id="navigation-context-panel" role="navigation"
      aria-label={currentGroup ? currentGroup.title + '页面' : '业务页面'}
      className="min-h-0 min-w-0 flex-1 overflow-y-auto overflow-x-hidden bg-white px-3 pb-6 pt-4">
      {currentGroup && currentModel && <>
        <div className="mb-4 border-b border-[#eaecf0] px-2 pb-3 text-[13px] font-semibold text-[#344054]">
          {currentGroup.title}
        </div>
        <div className="space-y-1">
          {currentModel.sections.map(section => <section key={section.id}
            aria-label={section.title || currentGroup.title} className="pb-1">
            {section.title && (section.routes.length > 1 || section.id === 'models') && <div
              className="px-3 pb-1 pt-3 text-[11px] font-medium text-[#98a2b3]">
              {section.title}
            </div>}
            {section.routes.flatMap(renderRoute)}
          </section>)}
        </div>
      </>}
    </div>
  </div>;
}
