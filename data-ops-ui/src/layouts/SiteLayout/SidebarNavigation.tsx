import {
  type NavigationGroupWithRoutes,
  type NavigationIconKey,
  type NavigationRoute,
} from '@/config/navigation';
import {
  getContextNavigationModel,
  type ContextNavigationModel,
  type ContextNavigationSection,
} from '@/config/taskNavigation';
import { Link } from '@umijs/max';
import { Dropdown, type MenuProps } from 'antd';
import { ChevronDown, ChevronRight } from 'lucide-react';
import React, { type ReactNode, useEffect, useMemo, useState } from 'react';

interface SidebarNavigationProps {
  groups: NavigationGroupWithRoutes[];
  standaloneRoutes: NavigationRoute[];
  icons: Record<NavigationIconKey, ReactNode>;
  compact: boolean;
  activeId?: string;
  activeGroupPath: string[];
  onNavigate?: () => void;
}

const sectionKey = (domain: string, section: string) => domain + ':' + section;

export default function SidebarNavigation({
  groups, standaloneRoutes, icons, compact, activeId, activeGroupPath, onNavigate,
}: SidebarNavigationProps) {
  const rootActive = activeGroupPath[activeGroupPath.length - 1];
  const models = useMemo(() => new Map(groups.map(group =>
    [group.id, getContextNavigationModel(group)])), [groups]);
  const [openDomain, setOpenDomain] = useState<string | undefined>(rootActive);
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());

  // A deep link, browser back, or permission refresh must expose its owning
  // section without inventing another permission parent or submenu level.
  useEffect(() => {
    setOpenDomain(rootActive);
    const activeSections = new Set<string>();
    for (const group of groups) {
      const model = models.get(group.id);
      if (!model) continue;
      for (const section of model.sections) {
        const extra = section.routes.slice(section.visibleCount);
        if (extra.some(route => route.id === activeId))
          activeSections.add(sectionKey(group.id, section.id));
      }
    }
    setExpanded(activeSections);
  }, [activeId, rootActive, models]);

  const toggleExtra = (key: string) => setExpanded(current => {
    const next = new Set(current);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    return next;
  });
  const linkClass = 'flex h-9 w-full min-w-0 items-center rounded-md px-3 text-[13px] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#475467]';
  const selectedStyle = { color: '#fe2c55', background: 'rgba(254,44,85,0.07)' };
  const renderLink = (route: NavigationRoute) => <Link
    key={route.id}
    to={route.path}
    title={route.title}
    onClick={onNavigate}
    aria-current={activeId === route.id ? 'page' : undefined}
    style={activeId === route.id ? selectedStyle : undefined}
    className={linkClass + (activeId === route.id
      ? ' font-semibold' : ' text-[#667085] hover:bg-white hover:text-[#344054]')}
  >{route.title}</Link>;

  const renderSection = (group: NavigationGroupWithRoutes, section: ContextNavigationSection) => {
    const key = sectionKey(group.id, section.id);
    const isExpanded = expanded.has(key);
    const primary = section.routes.slice(0, section.visibleCount);
    const remaining = section.routes.slice(section.visibleCount);
    const extraCount = remaining.length;
    return <div key={key} className="py-0.5">
      {section.routes.length > 1 && section.title && <div
        className="px-3 pb-1 pt-3 text-[11px] font-medium tracking-[0.02em] text-[#98a2b3]"
      >{section.title}</div>}
      {primary.map(renderLink)}
      {isExpanded && <div id={`navigation-extra-${group.id}-${section.id}`}>{remaining.map(renderLink)}</div>}
      {extraCount > 0 && <button
        type="button"
        aria-label={section.title + (isExpanded ? '收起其他页面' : '展开其他页面')}
        aria-expanded={isExpanded}
        aria-controls={`navigation-extra-${group.id}-${section.id}`}
        className="flex h-8 w-full items-center gap-2 rounded-md border-0 bg-transparent px-3 text-left text-[12px] text-[#98a2b3] hover:bg-white hover:text-[#475467]"
        onClick={() => toggleExtra(key)}
      >
        <ChevronRight size={12} className={isExpanded ? 'rotate-90' : ''} />
        {isExpanded ? '收起' : `更多 ${extraCount} 项`}
      </button>}
    </div>;
  };

  // Popup is only two levels: business domain -> page. Menu group labels are
  // non-clickable captions, not a third nested submenu.
  const popupItems = (group: NavigationGroupWithRoutes): MenuProps['items'] => {
    const model = models.get(group.id) ?? getContextNavigationModel(group);
    return model.sections.flatMap(section => section.routes.length > 1 && section.title
      ? [{
          type: 'group' as const,
          key: `section:${group.id}:${section.id}`,
          label: section.title,
          children: section.routes.map(route => ({
            key: route.id,
            label: <Link to={route.path} onClick={onNavigate}>{route.title}</Link>,
          })),
        }]
      : section.routes.map(route => ({
          key: route.id,
          label: <Link to={route.path} onClick={onNavigate}>{route.title}</Link>,
        })));
  };

  const renderGroup = (group: NavigationGroupWithRoutes) => {
    const active = rootActive === group.id;
    const open = openDomain === group.id;
    const model: ContextNavigationModel = models.get(group.id) ?? getContextNavigationModel(group);
    const header = <button
      type="button"
      aria-label={group.title}
      aria-expanded={compact ? undefined : open}
      aria-controls={compact ? undefined : `navigation-${group.id}`}
      aria-haspopup={compact ? 'menu' : undefined}
      title={compact ? group.title : undefined}
      onClick={compact ? undefined : () => setOpenDomain(current => current === group.id ? undefined : group.id)}
      className={[
        'relative flex h-10 w-full items-center gap-3 rounded-md border-0 bg-transparent text-left text-[14px]',
        'transition-colors hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#475467]',
        compact ? 'justify-center' : 'px-2',
        active || open ? 'font-semibold text-[#344054]' : 'text-[#667085]',
      ].join(' ')}
    >
      <span className="flex h-5 w-5 shrink-0 items-center justify-center">{icons[group.iconKey]}</span>
      {!compact && <>
        <span className="min-w-0 flex-1 truncate" title={group.title}>{group.title}</span>
        <ChevronDown size={14} className={open ? 'shrink-0 rotate-180' : 'shrink-0'} />
      </>}
      {compact && active && <span className="absolute right-0 h-4 w-[3px] rounded-full bg-[#fe2c55]" />}
    </button>;
    if (compact) return <Dropdown
      key={group.id}
      trigger={['hover', 'click']}
      placement="bottomLeft"
      align={{ points: ['tl', 'tr'], offset: [8, 0] }}
      menu={{
        items: popupItems(group), selectedKeys: activeId ? [activeId] : [],
        style: { minWidth: 230, maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' },
      }}
    >{header}</Dropdown>;

    return <div key={group.id} className="mb-1">
      {header}
      {open && <div id={`navigation-${group.id}`} className="space-y-0.5 pb-2 pl-6">
        {model.sections.map(section => renderSection(group, section))}
      </div>}
    </div>;
  };

  return <React.Fragment>
    {standaloneRoutes.length > 0 && <div className="mb-3 border-b border-[#eaecf0] pb-3">
      {standaloneRoutes.map(route => <Link
        key={route.id}
        to={route.path}
        onClick={onNavigate}
        aria-label={route.title}
        title={compact ? route.title : undefined}
        aria-current={activeId === route.id ? 'page' : undefined}
        style={activeId === route.id ? selectedStyle : undefined}
        className={'mb-0.5 flex h-10 items-center gap-3 rounded-md text-[14px] transition-colors hover:bg-white ' +
          (compact ? 'justify-center' : 'px-2') +
          (activeId === route.id ? ' font-semibold' : ' text-[#667085] hover:text-[#344054]')}
      >
        {route.iconKey && <span className="flex h-5 w-5 shrink-0 items-center justify-center">{icons[route.iconKey]}</span>}
        {!compact && route.title}
      </Link>)}
    </div>}
    {groups.map((group, index) => <div key={group.id}
      className={index > 0 && groups[index - 1].section !== group.section
        ? 'mt-3 border-t border-[#eaecf0] pt-3' : undefined}>
      {renderGroup(group)}
    </div>)}
  </React.Fragment>;
}
