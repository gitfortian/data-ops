import {
  type NavigationGroupWithRoutes,
  type NavigationIconKey,
  type NavigationRoute,
} from '@/config/navigation';
import { getTaskNavigationModel, type TaskNavigationModel } from '@/config/taskNavigation';
import { Link } from '@umijs/max';
import { Dropdown, type MenuProps } from 'antd';
import { ChevronDown, ChevronRight, Wrench } from 'lucide-react';
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

const taskKey = (domain: string, task: string) => domain + ':' + task;
const allTools = (model: TaskNavigationModel) => model.tools.flatMap(section => section.routes);
const isTaskActive = (routes: NavigationRoute[], activeId?: string) =>
  routes.some(route => route.id === activeId);

export default function SidebarNavigation({
  groups, standaloneRoutes, icons, compact, activeId, activeGroupPath, onNavigate,
}: SidebarNavigationProps) {
  const rootActive = activeGroupPath[activeGroupPath.length - 1];
  const models = useMemo(() => new Map(groups.map(group =>
    [group.id, getTaskNavigationModel(group)])), [groups]);
  const [openDomain, setOpenDomain] = useState<string | undefined>(rootActive);
  const [openTaskIds, setOpenTaskIds] = useState<Set<string>>(() => new Set());
  const [openTools, setOpenTools] = useState<Set<string>>(() => new Set());

  // Deep links, browser back, role changes and project menu changes must always
  // reveal the original accessible route, whether it is primary or a tool.
  useEffect(() => {
    setOpenDomain(rootActive);
    const activeTasks = new Set<string>();
    const activeToolDomains = new Set<string>();
    for (const group of groups) {
      const model = models.get(group.id);
      if (!model) continue;
      for (const task of model.tasks) {
        if (task.routes.length > 1 && isTaskActive(task.routes, activeId))
          activeTasks.add(taskKey(group.id, task.id));
      }
      if (isTaskActive(allTools(model), activeId))
        activeToolDomains.add(group.id);
    }
    setOpenTaskIds(activeTasks);
    setOpenTools(activeToolDomains);
  }, [rootActive, activeId, models]);

  const toggleTask = (key: string) => setOpenTaskIds(current => {
    const next = new Set(current);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    return next;
  });
  const toggleTools = (id: string) => setOpenTools(current => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const linkStyle = (selected: boolean) => selected
    ? { color: 'var(--yak-brand-color, #fe2c55)', backgroundColor: 'var(--yak-brand-color-soft, rgba(254,44,85,.06))' }
    : undefined;
  const rowClasses = 'flex min-h-9 w-full items-center rounded-lg px-3 py-2 text-[13px] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#475467]';
  const routeLink = (route: NavigationRoute, opts: { label?: string; indent?: boolean } = {}) => (
    <Link
      key={route.id}
      to={route.path}
      onClick={onNavigate}
      aria-current={activeId === route.id ? 'page' : undefined}
      title={route.title}
      style={linkStyle(activeId === route.id)}
      className={rowClasses + ' ' +
        (opts.indent ? 'pl-7 ' : '') +
        (activeId === route.id ? 'font-semibold' : 'text-[#667085] hover:bg-[#f5f6f8] hover:text-[#344054]')}
    >{opts.label || route.title}</Link>
  );

  const popupItems = (group: NavigationGroupWithRoutes): MenuProps['items'] => {
    const model = models.get(group.id) ?? getTaskNavigationModel(group);
    const tasks: NonNullable<MenuProps['items']> = model.tasks.map(task => task.routes.length === 1 ? ({
      key: task.routes[0].id,
      label: <Link to={task.routes[0].path} onClick={onNavigate}>{task.title}</Link>,
    }) : ({
      key: 'task:' + task.id, label: task.title,
      children: task.routes.map(route => ({
        key: route.id, label: <Link to={route.path} onClick={onNavigate}>{route.title}</Link>,
      })),
    }));
    const tools = model.tools.flatMap(section => section.routes.map(route => ({
      key: route.id, label: <Link to={route.path} onClick={onNavigate}>{route.title}</Link>,
    })));
    if (tools.length) tasks.push({
      key: 'tools:' + group.id,
      label: '专业工具',
      children: tools,
    });
    return tasks;
  };

  const renderTask = (group: NavigationGroupWithRoutes, model: TaskNavigationModel['tasks'][number]) => {
    const selected = isTaskActive(model.routes, activeId);
    const key = taskKey(group.id, model.id);
    const expanded = openTaskIds.has(key);
    if (model.routes.length === 1) {
      const route = model.routes[0];
      return <div key={key}>{routeLink(route, { label: model.title })}</div>;
    }
    return (
      <div key={key}>
        <button
          type="button"
          aria-label={model.title}
          aria-expanded={expanded}
          aria-controls={`task-navigation-${group.id}-${model.id}`}
          onClick={() => toggleTask(key)}
          style={selected ? linkStyle(true) : undefined}
          className={rowClasses + ' justify-between gap-2 border-0 bg-transparent text-left ' +
            (selected ? 'font-semibold' : 'text-[#667085] hover:bg-[#f5f6f8] hover:text-[#344054]')}
        >
          <span className="truncate">{model.title}</span>
          <ChevronRight className={expanded ? 'shrink-0 rotate-90' : 'shrink-0'} size={14} />
        </button>
        {expanded && <div id={`task-navigation-${group.id}-${model.id}`}
          className="ml-3 border-l border-[#eaecf0] py-0.5 pl-1.5">
          {model.routes.map(route => routeLink(route))}
        </div>}
      </div>
    );
  };

  const renderTools = (group: NavigationGroupWithRoutes, model: TaskNavigationModel) => {
    const routes = allTools(model);
    if (!routes.length) return null;
    // If this role has only a specialist grant, it must be immediately
    // discoverable. Do not render a fake collapsible button that cannot close.
    if (!model.tasks.length) return <div className="pt-1" key={group.id + ':tools'}>
      <div className="px-3 py-2 text-[12px] font-medium text-[#667085]">专业工具</div>
      <div className="ml-3 border-l border-[#eaecf0] pl-1.5">
        {model.tools.map(section => <div key={section.id}>
          {model.tools.length > 1 && <div className="px-3 pb-1 pt-2 text-[11px] text-[#98a2b3]">{section.title}</div>}
          {section.routes.map(route => routeLink(route))}
        </div>)}
      </div>
    </div>;
    const expanded = openTools.has(group.id);
    return <div className="mt-2 border-t border-[#eaecf0] pt-2" key={group.id + ':tools'}>
      <button
        type="button"
        aria-label={group.title + '专业工具'}
        aria-expanded={expanded}
        aria-controls={`task-navigation-tools-${group.id}`}
        onClick={() => toggleTools(group.id)}
        className={rowClasses + ' justify-between gap-2 border-0 bg-transparent text-left text-[#667085] hover:bg-[#f5f6f8]'}
      >
        <span className="flex min-w-0 items-center gap-2">
          <Wrench size={14} className="shrink-0" />
          <span className="truncate">专业工具</span>
          <span className="text-[11px] text-[#98a2b3]">{routes.length}</span>
        </span>
        <ChevronDown size={13} className={expanded ? 'shrink-0 rotate-180' : 'shrink-0'} />
      </button>
      {expanded && <div id={`task-navigation-tools-${group.id}`} className="ml-3 border-l border-[#eaecf0] pb-1 pl-1.5">
        {model.tools.map(section => <div key={section.id}>
          {model.tools.length > 1 && <div className="px-3 pb-1 pt-3 text-[11px] font-medium text-[#98a2b3]">
            {section.title}
          </div>}
          {section.routes.map(route => routeLink(route))}
        </div>)}
      </div>}
    </div>;
  };

  const renderGroup = (group: NavigationGroupWithRoutes) => {
    const model = models.get(group.id) ?? getTaskNavigationModel(group);
    const active = rootActive === group.id;
    const open = openDomain === group.id;
    const header = <button
      type="button"
      aria-label={group.title}
      aria-expanded={compact ? undefined : open}
      aria-controls={compact ? undefined : `navigation-${group.id}`}
      aria-haspopup={compact ? 'menu' : undefined}
      title={compact ? group.title : undefined}
      onClick={compact ? undefined : () => setOpenDomain(current => current === group.id ? undefined : group.id)}
      style={active ? { color: '#344054' } : undefined}
      className={[
        'relative flex h-10 w-full items-center gap-3 rounded-lg border-0 bg-transparent text-left text-[14px]',
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
      {compact && active && <span className="absolute right-0 h-4 w-[3px] rounded-full"
        style={{ background: 'var(--yak-brand-color, #fe2c55)' }} />}
    </button>;

    if (compact) return <Dropdown
      key={group.id}
      trigger={['hover', 'click']}
      placement="bottomLeft"
      align={{ points: ['tl', 'tr'], offset: [8, 0] }}
      menu={{ items: popupItems(group), selectedKeys: activeId ? [activeId] : [],
        style: { minWidth: 240, maxHeight: 'calc(100vh - 32px)', overflowY: 'auto' } }}
    >{header}</Dropdown>;

    return <div key={group.id} className="mb-1">
      {header}
      {open && <div id={`navigation-${group.id}`} className="space-y-0.5 pb-2 pl-3">
        {model.tasks.map(task => renderTask(group, task))}
        {renderTools(group, model)}
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
        style={linkStyle(activeId === route.id)}
        className={rowClasses + ' mb-0.5 gap-3 ' +
          (compact ? 'justify-center !px-0' : '') +
          (activeId === route.id ? ' font-semibold' : ' text-[#667085] hover:bg-white hover:text-[#344054]')}
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
