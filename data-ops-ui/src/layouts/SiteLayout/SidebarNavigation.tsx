import {
  getNavigationEntries,
  type NavigationGroupWithRoutes,
  type NavigationIconKey,
  type NavigationRoute,
} from '@/config/navigation';
import { Link } from '@umijs/max';
import { Dropdown, type MenuProps } from 'antd';
import { ChevronDown } from 'lucide-react';
import React, { type ReactNode, useEffect, useState } from 'react';

// Root labels start after 4px padding, a 20px icon and a 12px gap.
// Nested groups and their sibling pages share that text column.
const ROOT_LABEL_PADDING = 36;
const NESTED_INDENT = 16;
const nestedLabelPadding = (depth: number) => ROOT_LABEL_PADDING + (depth - 1) * NESTED_INDENT;

interface SidebarNavigationProps {
  groups: NavigationGroupWithRoutes[];
  standaloneRoutes: NavigationRoute[];
  icons: Record<NavigationIconKey, ReactNode>;
  compact: boolean;
  activeId?: string;
  activeGroupPath: string[];
}

export default function SidebarNavigation({
  groups, standaloneRoutes, icons, compact, activeId, activeGroupPath,
}: SidebarNavigationProps) {
  const activePathKey = activeGroupPath.join('/');
  const [openIds, setOpenIds] = useState(() => new Set(activeGroupPath));

  useEffect(() => {
    setOpenIds(new Set(activePathKey.split('/').filter(Boolean)));
  }, [activePathKey, activeId]);

  const toggleGroup = (group: NavigationGroupWithRoutes) => {
    setOpenIds((current) => {
      const next = group.parentGroupId ? new Set(current) : new Set<string>();
      if (current.has(group.id)) next.delete(group.id);
      else {
        next.add(group.id);
        // Returning to the current domain also reveals its active specialist.
        if (!group.parentGroupId && activeGroupPath.includes(group.id)) {
          activeGroupPath.forEach((id) => next.add(id));
        }
      }
      return next;
    });
  };

  const renderLink = (route: NavigationRoute, depth = 1) => (
    <Link
      key={route.id}
      to={route.path}
      aria-current={activeId === route.id ? 'page' : undefined}
      style={{ paddingInlineStart: nestedLabelPadding(depth) }}
      className={[
        'flex h-9 w-full items-center rounded-md pr-2 text-[14px] transition-colors',
        activeId === route.id
          ? 'bg-white/80 font-semibold text-[#161823]'
          : 'text-[rgba(37,38,50,.6)] hover:bg-white/50 hover:text-[#161823]',
      ].join(' ')}
    >
      <span className="min-w-0 truncate" title={route.title}>{route.title}</span>
    </Link>
  );

  const popupItems = (group: NavigationGroupWithRoutes): MenuProps['items'] =>
    getNavigationEntries(group).map((entry) => entry.kind === 'route' ? {
      key: entry.route.id,
      label: <Link to={entry.route.path} aria-current={activeId === entry.route.id ? 'page' : undefined}>{entry.route.title}</Link>,
    } : {
      key: entry.group.id,
      label: entry.group.title,
      children: popupItems(entry.group),
    });

  const renderGroup = (group: NavigationGroupWithRoutes, depth = 0): ReactNode => {
    const open = openIds.has(group.id);
    const active = activeGroupPath.includes(group.id);
    const button = (
      <button
        type="button"
        aria-label={group.title}
        aria-expanded={compact ? undefined : open}
        aria-controls={compact ? undefined : `navigation-${group.id}`}
        aria-haspopup={compact ? 'menu' : undefined}
        title={compact ? group.title : undefined}
        onClick={compact ? undefined : () => toggleGroup(group)}
        style={!compact && depth > 0 ? { paddingInlineStart: nestedLabelPadding(depth) } : undefined}
        className={[
          'relative flex h-10 w-full items-center gap-3 rounded-md border-0 bg-transparent text-left text-[14px] transition-colors hover:bg-white/60',
          compact ? 'justify-center' : depth > 0 ? 'pr-1' : 'px-1',
          active || open ? 'font-semibold text-[#161823]' : 'text-[rgba(22,24,35,.55)]',
        ].join(' ')}
      >
        {depth === 0 && <span className="flex h-5 w-5 shrink-0 items-center justify-center">{icons[group.iconKey]}</span>}
        {!compact && <>
          <span className="min-w-0 flex-1 truncate" title={group.title}>{group.title}</span>
          <ChevronDown size={13} className={open ? 'shrink-0 rotate-180' : 'shrink-0'} />
        </>}
        {compact && active && <span className="absolute right-0 h-4 w-[2px] rounded-full bg-[#161823]" />}
      </button>
    );

    if (compact) return (
      <Dropdown
        key={group.id}
        trigger={['hover', 'click']}
        placement="bottomLeft"
        align={{ points: ['tl', 'tr'], offset: [8, 0] }}
        menu={{ items: popupItems(group), selectedKeys: activeId ? [activeId] : [],
          style: { minWidth: 240, maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' } }}
      >{button}</Dropdown>
    );

    return (
      <div key={group.id} className={depth === 0 ? 'mb-2' : undefined}>
        {button}
        {/* Unmount closed branches so invisible links never remain keyboard targets. */}
        {open && <div id={`navigation-${group.id}`} className="space-y-0.5 py-1">
          {getNavigationEntries(group).map((entry) => entry.kind === 'route'
            ? renderLink(entry.route, depth + 1)
            : renderGroup(entry.group, depth + 1))}
        </div>}
      </div>
    );
  };

  return <React.Fragment>
    {standaloneRoutes.length > 0 && <div className="mb-3 border-b border-black/10 pb-3">
      {standaloneRoutes.map((route) => <Link
        key={route.id}
        to={route.path}
        aria-label={route.title}
        title={compact ? route.title : undefined}
        aria-current={activeId === route.id ? 'page' : undefined}
        className={[
          'flex h-10 items-center gap-3 rounded-md text-[14px] hover:bg-white/60',
          compact ? 'justify-center' : 'px-1',
          activeId === route.id ? 'font-semibold text-[#161823]' : 'text-[rgba(22,24,35,.55)]',
        ].join(' ')}
      >
        {route.iconKey && <span className="flex h-5 w-5 items-center justify-center">{icons[route.iconKey]}</span>}
        {!compact && route.title}
      </Link>)}
    </div>}
    {groups.map((group, index) => <div key={group.id}
      className={index > 0 && groups[index - 1].section !== group.section ? 'mt-3 border-t border-black/10 pt-3' : undefined}>
      {renderGroup(group)}
    </div>)}
  </React.Fragment>;
}
