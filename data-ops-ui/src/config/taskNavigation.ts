import type { NavigationGroupWithRoutes, NavigationRoute } from './navigation';

/**
 * IA-550 V3.1 presentation only. A task is a discoverable group of *existing*
 * independent routes, not a new URL, backend resource or inherited grant.
 * Never mutate menuGroup / parentId / menuCode / required permissions.
 */
export interface TaskMenuItem {
  id: string;
  title: string;
  routes: NavigationRoute[];
}
export interface TaskToolSection {
  id: string;
  title: string;
  routes: NavigationRoute[];
}
export interface TaskNavigationModel {
  tasks: TaskMenuItem[];
  tools: TaskToolSection[];
}

interface TaskSpec {
  id: string;
  title: string;
  routeIds: readonly string[];
}

/** 3 + 4 + 3 + 6 + 4 default task concepts per #550 / IA-V3.1. */
export const DEFAULT_TASK_MENU: Readonly<Record<string, readonly TaskSpec[]>> = {
  integration: [
    { id: 'sources', title: '数据源', routeIds: ['data-source'] },
    { id: 'sync', title: '同步任务', routeIds: ['batch-link-up', 'realtime-sync'] },
    { id: 'files', title: '文件资源', routeIds: ['resource-management'] },
  ],
  modeling: [
    { id: 'business', title: '业务语义', routeIds: ['semantic-domains', 'semantic-processes'] },
    { id: 'standards', title: '数据标准', routeIds: ['semantic-standards', 'semantic-fields'] },
    { id: 'models', title: '模型工作台', routeIds: ['modeling-workspace'] },
    { id: 'metrics', title: '指标中心', routeIds: ['metric-manage'] },
  ],
  development: [
    { id: 'dev', title: '开发工作台', routeIds: ['data-development'] },
    { id: 'workflows', title: '工作流', routeIds: ['workflow-definition', 'workflow-instances'] },
    { id: 'operations', title: '发布与运行', routeIds: ['data-development-release', 'data-development-execution'] },
  ],
  'data-asset': [
    { id: 'catalog', title: '资产目录', routeIds: ['data-asset-catalog'] },
    { id: 'governance', title: '治理工作队列', routeIds: ['data-asset-inventory'] },
    { id: 'quality', title: '质量治理', routeIds: ['data-quality-overview', 'data-quality-table-config'] },
    { id: 'security', title: '数据安全', routeIds: ['data-security-overview'] },
    { id: 'lifecycle', title: '生命周期', routeIds: ['data-lifecycle-policy'] },
    { id: 'mdm', title: '主数据专业工作台', routeIds: ['mdm-overview'] },
  ],
  'data-analysis': [
    { id: 'products', title: '数据产品目录', routeIds: ['consumption-catalog'] },
    { id: 'datasets', title: '数据集', routeIds: ['dataset-management'] },
    { id: 'services', title: 'API 服务', routeIds: ['data-service-api'] },
    { id: 'analytics', title: '分析展示', routeIds: ['dashboard', 'digital-screen'] },
  ],
};

interface SectionWithRoute { sectionId: string; sectionTitle: string; route: NavigationRoute }
const enumerateAuthorizedRoutes = (group: NavigationGroupWithRoutes): SectionWithRoute[] => {
  const result = group.routes.map(route => ({ sectionId: group.id, sectionTitle: '', route }));
  for (const sub of group.subGroups ?? []) {
    result.push(...enumerateAuthorizedRoutes(sub).map(x => ({
      ...x,
      sectionTitle: x.sectionTitle || sub.title,
    })));
  }
  return result;
};

/**
 * Input groups are already permission/menu-grant/feature filtered by
 * getMainNavigationGroups. This function must not introduce extra auth gates.
 * Every original visible authorized leaf is retained exactly once.
 */
export function getTaskNavigationModel(group: NavigationGroupWithRoutes): TaskNavigationModel {
  const available = enumerateAuthorizedRoutes(group);
  const byId = new Map(available.map(x => [x.route.id, x.route]));
  const consumed = new Set<string>();
  const specs = DEFAULT_TASK_MENU[group.id];

  // Existing platform administration is a domain with its own menu authorization:
  // keep each authorized management page directly accessible, not under Tools.
  if (!specs) return {
    tasks: group.routes.map(route => ({ id: route.id, title: route.title, routes: [route] })),
    tools: available.some(x => x.sectionId !== group.id)
      ? [{ id: 'other', title: '专业工具', routes: available.filter(x => x.sectionId !== group.id).map(x => x.route) }]
      : [],
  };

  const tasks: TaskMenuItem[] = specs.map(spec => {
    const routes = spec.routeIds
      .filter(id => byId.has(id) && !consumed.has(id))
      .map(id => { consumed.add(id); return byId.get(id)!; });
    return { id: spec.id, title: spec.title, routes };
  }).filter(item => item.routes.length > 0);

  const toolMap = new Map<string, TaskToolSection>();
  for (const item of available) {
    if (consumed.has(item.route.id)) continue;
    const section = toolMap.get(item.sectionId) ?? {
      id: item.sectionId,
      title: item.sectionTitle || '其他专业工具',
      routes: [],
    };
    section.routes.push(item.route);
    toolMap.set(item.sectionId, section);
  }
  return { tasks, tools: [...toolMap.values()] };
}

export function taskNavigationIds(model: TaskNavigationModel): string[] {
  return [...model.tasks.flatMap(item => item.routes.map(route => route.id)),
    ...model.tools.flatMap(section => section.routes.map(route => route.id))];
}
