import type { NavigationGroupWithRoutes, NavigationRoute } from './navigation';

/**
 * IA-550: render accessible routes in their business context instead of
 * inventing extra menu levels or a miscellaneous "专业工具" bucket.
 * Section captions are NOT routes and must never become permission parents.
 */
export interface ContextNavigationSection {
  id: string;
  title: string;
  routes: NavigationRoute[];
  /** The number of directly visible links. Other links expand IN this section. */
  visibleCount: number;
}
export interface ContextNavigationModel {
  sections: ContextNavigationSection[];
}

interface SectionSpec {
  id: string;
  title: string;
  routes: readonly string[];
  visible: number;
}

/** One domain expansion -> directly clickable pages (no nested task menu). */
export const CONTEXT_NAVIGATION_SECTIONS: Readonly<Record<string, readonly SectionSpec[]>> = {
  integration: [
    { id: 'source', title: '来源管理', routes: ['data-source', 'sql-execution-audit'], visible: 1 },
    { id: 'sync', title: '数据同步', routes: ['batch-link-up'], visible: 1 },
    { id: 'files', title: '文件资源', routes: ['resource-management'], visible: 1 },
  ],
  modeling: [
    { id: 'business', title: '业务定义', routes: ['semantic-domains', 'semantic-processes'], visible: 2 },
    { id: 'standards', title: '标准规范', routes: ['semantic-standards', 'semantic-fields', 'semantic-layers'], visible: 2 },
    { id: 'models', title: '模型设计', routes: ['modeling-workspace'], visible: 1 },
    { id: 'metrics', title: '指标体系', routes: ['metric-manage', 'metric-lineage', 'metric-impact', 'metric-service'], visible: 1 },
  ],
  development: [
    { id: 'development', title: '数据开发', routes: ['data-development'], visible: 1 },
    { id: 'workflow', title: '工作流', routes: ['workflow-definition', 'workflow-instances'], visible: 2 },
    { id: 'release', title: '发布与运行', routes: ['data-development-release', 'data-development-execution'], visible: 2 },
  ],
  'data-asset': [
    { id: 'catalog', title: '资产发现', routes: ['data-asset-catalog', 'data-asset-overview', 'data-analysis-lineage'], visible: 1 },
    { id: 'governance', title: '资产治理', routes: ['data-asset-inventory', 'data-asset-taxonomy'], visible: 1 },
    { id: 'metadata', title: '技术元数据', routes: ['data-metadata-overview', 'data-metadata-collect'], visible: 1 },
    { id: 'quality', title: '数据质量', routes: ['data-quality-overview', 'data-quality-table-config', 'data-quality-execution', 'data-quality-rule-template'], visible: 2 },
    { id: 'security', title: '数据安全', routes: ['data-security-overview', 'data-security-classification', 'data-security-access', 'data-security-masking', 'data-security-audit', 'data-security-compliance'], visible: 1 },
    { id: 'lifecycle', title: '生命周期', routes: ['data-lifecycle-policy', 'data-lifecycle-monitor', 'data-lifecycle-storage'], visible: 1 },
    { id: 'mdm', title: '主数据管理', routes: ['mdm-overview', 'mdm-modeling', 'mdm-identification', 'mdm-cleansing', 'mdm-approval'], visible: 1 },
  ],
  'data-analysis': [
    { id: 'products', title: '数据产品', routes: ['consumption-catalog'], visible: 1 },
    { id: 'datasets', title: '数据集', routes: ['dataset-management'], visible: 1 },
    { id: 'api', title: 'API 服务', routes: ['data-service-api', 'data-service-access', 'data-service-debug', 'data-service-overview', 'data-service-logs'], visible: 1 },
    { id: 'presentation', title: '分析展示', routes: ['dashboard', 'digital-screen'], visible: 2 },
  ],
};

interface AuthorizedLeaf {
  route: NavigationRoute;
  ownerGroup: string;
  ownerTitle: string;
}
const collectVisible = (group: NavigationGroupWithRoutes): AuthorizedLeaf[] => [
  ...group.routes.map(route => ({ route, ownerGroup: group.id, ownerTitle: group.title })),
  ...(group.subGroups ?? []).flatMap(collectVisible),
];

/**
 * This function receives ONLY routes already filtered by Security RBAC, actual
 * permissionCodes and feature flags. Every authorized leaf is returned once.
 * Unplanned future pages stay alongside their original capability, never lost.
 */
export const getContextNavigationModel = (group: NavigationGroupWithRoutes): ContextNavigationModel => {
  const available = collectVisible(group);
  const byId = new Map(available.map(item => [item.route.id, item.route]));
  const used = new Set<string>();
  const specs = CONTEXT_NAVIGATION_SECTIONS[group.id];

  if (!specs) return { sections: [{
    id: 'settings',
    title: '',
    routes: available.map(item => item.route),
    visibleCount: available.length,
  }] };

  const sections: ContextNavigationSection[] = specs.map(spec => {
    const routes = spec.routes.filter(id => byId.has(id)).map(id => {
      used.add(id);
      return byId.get(id)!;
    });
    return { id: spec.id, title: spec.title, routes, visibleCount: spec.visible };
  }).filter(section => section.routes.length > 0);

  // Fallback is not a miscellaneous sidebar bucket: place each new capability
  // under its existing authorized business owner, close to related pages.
  for (const item of available) {
    if (used.has(item.route.id)) continue;
    const section = sections.find(s => s.id === item.ownerGroup)
      ?? sections.find(s => s.title === item.ownerTitle);
    if (section) section.routes.push(item.route);
    else sections.push({
      id: item.ownerGroup,
      title: item.ownerGroup === group.id ? '' : item.ownerTitle,
      routes: [item.route],
      visibleCount: 1,
    });
    used.add(item.route.id);
  }
  return { sections };
};

export const contextNavigationRouteIds = (model: ContextNavigationModel): string[] =>
  model.sections.flatMap(section => section.routes.map(route => route.id));
