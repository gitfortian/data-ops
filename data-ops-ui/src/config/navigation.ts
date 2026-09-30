import {
  YAK_OPS_MENU_CODES,
  YAK_SECURITY_MENU_CODES,
  type SecurityMenuCode,
} from '../constants/securityMenuCodes';
import { hasMenuAccess } from '../utils/security/menu';
import {
  type PermissionRequirement,
  satisfiesPermissionRequirement,
} from '../utils/security/permission';
import { productFeatures } from './productFeatures';

export type NavigationIconKey =
  | 'home' | 'database' | 'sync' | 'realtime' | 'client' | 'connector'
  | 'workflow' | 'project' | 'instance' | 'quality' | 'report' | 'monitor' | 'alarm'
  | 'knowledge' | 'api' | 'insight' | 'system';
export type NavigationSectionKey = 'business' | 'system';
export type NavigationMenuCode = SecurityMenuCode;

interface NavigationRouteBase {
  id: string; path: string; title: string; component: string;
  /** Stable database-backed RBAC menu code. Never derive this from route id/path. */
  menuCode?: NavigationMenuCode;
  iconKey?: NavigationIconKey; menuGroup?: string; order?: number;
  hidden?: boolean; parentId?: string; quickCreateLabel?: string;
  quickCreateRequirement?: PermissionRequirement;
}
export type NavigationRoute = NavigationRouteBase &
  (PermissionRequirement | { parentId: string; mode?: never });
export interface NavigationGroup {
  id: string; title: string; iconKey: NavigationIconKey;
  /** Stable parent menu code returned by Yak Security. */
  menuCode: NavigationMenuCode;
  section: NavigationSectionKey; order: number;
  /** When set, this group renders nested under the referenced top-level domain. */
  parentGroupId?: string;
}
export interface NavigationGroupWithRoutes extends NavigationGroup {
  routes: NavigationRoute[];
  subGroups?: NavigationGroupWithRoutes[];
}

export type NavigationEntry =
  | { kind: 'route'; route: NavigationRoute }
  | { kind: 'group'; group: NavigationGroupWithRoutes };

/** Pages and specialist groups share task order within a capability. */
export const getNavigationEntries = (group: NavigationGroupWithRoutes): NavigationEntry[] => [
  ...group.routes.map((route): NavigationEntry => ({ kind: 'route', route })),
  ...(group.subGroups ?? []).map((child): NavigationEntry => ({ kind: 'group', group: child })),
].sort((left, right) =>
  ((left.kind === 'route' ? left.route.order : left.group.order) ?? 0)
  - ((right.kind === 'route' ? right.route.order : right.group.order) ?? 0));

export const navigationGroups: readonly NavigationGroup[] = [
  { id: 'integration', menuCode: YAK_OPS_MENU_CODES.integration, title: '数据接入与集成', iconKey: 'connector', section: 'business', order: 10 },
  { id: 'modeling', menuCode: YAK_OPS_MENU_CODES.modeling, title: '标准、指标与建模', iconKey: 'project', section: 'business', order: 20 },
  { id: 'semantic', menuCode: YAK_OPS_MENU_CODES.semantic, title: '业务语义', iconKey: 'knowledge', section: 'business', order: 10, parentGroupId: 'modeling' },
  { id: 'metric', menuCode: YAK_OPS_MENU_CODES.metric, title: '指标', iconKey: 'report', section: 'business', order: 30, parentGroupId: 'modeling' },
  { id: 'development', menuCode: YAK_OPS_MENU_CODES.development, title: '开发与运行', iconKey: 'workflow', section: 'business', order: 30 },
  { id: 'workflow', menuCode: YAK_OPS_MENU_CODES.workflow, title: '工作流', iconKey: 'instance', section: 'business', order: 40, parentGroupId: 'development' },
  { id: 'data-asset', menuCode: YAK_OPS_MENU_CODES.asset, title: '数据资产与治理', iconKey: 'quality', section: 'business', order: 40 },
  { id: 'asset-management', menuCode: YAK_OPS_MENU_CODES.governance, title: '资产管理', iconKey: 'database', section: 'business', order: 30, parentGroupId: 'data-asset' },
  { id: 'data-metadata', menuCode: YAK_OPS_MENU_CODES.metadata, title: '技术元数据', iconKey: 'sync', section: 'business', order: 40, parentGroupId: 'data-asset' },
  { id: 'data-quality', menuCode: YAK_OPS_MENU_CODES.dataQuality, title: '数据质量', iconKey: 'quality', section: 'business', order: 60, parentGroupId: 'data-asset' },
  { id: 'data-security', menuCode: YAK_OPS_MENU_CODES.dataSecurity, title: '数据安全', iconKey: 'monitor', section: 'business', order: 70, parentGroupId: 'data-asset' },
  { id: 'data-lifecycle', menuCode: YAK_OPS_MENU_CODES.lifecycle, title: '数据生命周期', iconKey: 'realtime', section: 'business', order: 80, parentGroupId: 'data-asset' },
  { id: 'mdm', menuCode: YAK_OPS_MENU_CODES.mdm, title: '专业方案：主数据管理', iconKey: 'database', section: 'business', order: 90, parentGroupId: 'data-asset' },
  { id: 'data-analysis', menuCode: YAK_OPS_MENU_CODES.dataAnalysis, title: '数据消费与服务', iconKey: 'insight', section: 'business', order: 50 },
  { id: 'data-service', menuCode: YAK_OPS_MENU_CODES.dataService, title: 'API 服务', iconKey: 'api', section: 'business', order: 30, parentGroupId: 'data-analysis' },
  { id: 'analysis-presentation', menuCode: YAK_OPS_MENU_CODES.analysisPresentation, title: '分析展示', iconKey: 'insight', section: 'business', order: 40, parentGroupId: 'data-analysis' },
  { id: 'system', menuCode: YAK_SECURITY_MENU_CODES.system, title: '平台设置', iconKey: 'system', section: 'system', order: 90 },
];

export const appRoutes: readonly NavigationRoute[] = [
  { id: 'home', menuCode: YAK_OPS_MENU_CODES.home, mode: 'public', path: '/home', title: '首页', component: './home', iconKey: 'home', order: 0 },
  { id: 'quick-create', path: '/create', title: '快速创建', component: './create', hidden: true, parentId: 'home' },
  { id: 'ai-agent', path: '/ai-agent', title: '智能助手', component: './ai-agent', hidden: true, mode: 'public', parentId: 'home' },
  { id: 'data-source', menuCode: YAK_OPS_MENU_CODES.dataSource, mode: 'one', permission: 'resource:data-source:read', path: '/data-source', title: '数据源', component: './data-source', iconKey: 'database', menuGroup: 'integration', order: 10 },
  { id: 'sql-execution-audit', menuCode: YAK_OPS_MENU_CODES.sqlExecutionAudit, mode: 'one', permission: 'resource:sql-execution:read', path: '/sql-execution-audit', title: 'SQL 执行记录', component: './data-source/sql-executions', iconKey: 'monitor', menuGroup: 'integration', order: 50 },
  { id: 'dashboard', menuCode: YAK_OPS_MENU_CODES.dashboard, mode: 'public', path: '/dashboard', title: '仪表盘', component: './data-analysis/dashboard', iconKey: 'insight', menuGroup: 'analysis-presentation', order: 10 },
  { id: 'dashboard-new', path: '/dashboard/new', title: '新建仪表盘', component: './data-analysis/dashboard/editor', hidden: true, parentId: 'dashboard' },
  { id: 'dashboard-editor', path: '/dashboard/:id/edit', title: '仪表盘编辑', component: './data-analysis/dashboard/editor', hidden: true, parentId: 'dashboard' },
  { id: 'dashboard-viewer', path: '/dashboard/:id', title: '仪表盘查看', component: './data-analysis/dashboard/viewer', hidden: true, parentId: 'dashboard' },
  { id: 'consumption-catalog', menuCode: YAK_OPS_MENU_CODES.consumptionCatalog, mode: 'one', permission: 'data-asset:read', path: '/data-analysis/consumption', title: '数据产品目录', component: './data-analysis/consumption', iconKey: 'knowledge', menuGroup: 'data-analysis', order: 10 },
  { id: 'consumption-detail', path: '/data-analysis/consumption/:productKey', title: '数据产品详情', component: './data-analysis/consumption/detail', hidden: true, parentId: 'consumption-catalog' },
  { id: 'dataset-management', menuCode: YAK_OPS_MENU_CODES.datasetManagement, mode: 'public', path: '/dataset', title: '数据集管理', component: './data-analysis/dataset', iconKey: 'database', menuGroup: 'data-analysis', order: 20 },
  { id: 'dataset-management-detail', path: '/dataset/:id', title: '数据集详情', component: './data-analysis/dataset/detail', hidden: true, parentId: 'dataset-management' },
  { id: 'data-analysis-catalog', mode: 'public', path: '/data-analysis/data-catalog', title: '数据目录', component: './data-analysis/data-catalog', iconKey: 'database', menuGroup: 'data-analysis', hidden: true, order: 21 },
  { id: 'data-analysis-lineage', menuCode: YAK_OPS_MENU_CODES.dataAnalysisLineage, mode: 'public', path: '/data-analysis/lineage', title: '数据血缘', component: './data-analysis/lineage', iconKey: 'workflow', menuGroup: 'data-asset', order: 50 },
  { id: 'digital-screen', menuCode: YAK_OPS_MENU_CODES.digitalScreen, mode: 'public', path: '/digital-screen', title: '数字大屏', component: './data-analysis/digital-screen', iconKey: 'insight', menuGroup: 'analysis-presentation', order: 20 },
  { id: 'digital-screen-new', path: '/digital-screen/new', title: '新建数字化大屏', component: './data-analysis/digital-screen/new', hidden: true, parentId: 'digital-screen' },
  { id: 'digital-screen-editor', path: '/digital-screen/:id/edit', title: '数字化大屏编辑', component: './data-analysis/digital-screen/editor', hidden: true, parentId: 'digital-screen' },
  { id: 'digital-screen-viewer', path: '/digital-screen/:id', title: '数字化大屏预览', component: './data-analysis/digital-screen/viewer', hidden: true, parentId: 'digital-screen' },
  { id: 'data-analysis-chart', path: '/data-analysis/chart-analysis', title: '图表分析', component: './data-analysis/chart-analysis-redirect', hidden: true, parentId: 'dashboard' },
  { id: 'data-service-api', menuCode: YAK_OPS_MENU_CODES.dataServiceApi, mode: 'one', permission: 'data-service:read', path: '/data-service', title: 'API 服务目录', component: './data-service', iconKey: 'api', menuGroup: 'data-service', order: 10 },
  { id: 'data-service-api-detail', path: '/data-service/api/:id', title: 'API 详情', component: './data-service/detail', hidden: true, parentId: 'data-service-api' },
  { id: 'data-service-access', menuCode: YAK_OPS_MENU_CODES.dataServiceAccess, mode: 'one', permission: 'data-service:access', path: '/data-service/access', title: '调用方与密钥', component: './data-service/access', iconKey: 'api', menuGroup: 'data-service', order: 20 },
  { id: 'data-service-debug', menuCode: YAK_OPS_MENU_CODES.dataServiceDebug, mode: 'all', permissions: ['data-service:read', 'data-service:runtime'], path: '/data-service/debug', title: 'API 调试', component: './data-service/debug', iconKey: 'api', menuGroup: 'data-service', order: 30 },
  { id: 'data-service-overview', menuCode: YAK_OPS_MENU_CODES.dataServiceOverview, mode: 'one', permission: 'data-service:observe', path: '/data-service/overview', title: '运行概览', component: './data-service/overview', iconKey: 'monitor', menuGroup: 'data-service', order: 40 },
  { id: 'data-service-logs', menuCode: YAK_OPS_MENU_CODES.dataServiceLogs, mode: 'one', permission: 'data-service:observe', path: '/data-service/logs', title: '调用记录', component: './data-service/logs', iconKey: 'report', menuGroup: 'data-service', order: 50 },
  { id: 'settings', mode: 'public', path: '/settings', title: '设置', component: './settings', hidden: true, order: 30 },
  {
    id: 'batch-link-up', menuCode: YAK_OPS_MENU_CODES.batchLinkUp,
    mode: 'one', permission: 'task:batch:read',
    path: '/sync/batch-link-up', title: '离线同步', component: './integration/batch-link-up',
    iconKey: 'sync', menuGroup: 'integration', order: 20,
    quickCreateRequirement: { mode: 'one', permission: 'task:batch:create' },
    quickCreateLabel: '新建离线同步',
  },
  { id: 'realtime-sync-detail', path: '/sync/realtime/:id/detail', title: '实时同步配置', component: './integration/realtime-sync/detail', hidden: true, parentId: 'realtime-sync' },
  {
    id: 'realtime-sync', menuCode: YAK_OPS_MENU_CODES.realtimeSync,
    mode: 'one', permission: 'task:realtime:read',
    path: '/sync/realtime', title: '实时同步', component: './integration/realtime-sync',
    iconKey: 'realtime', menuGroup: 'integration', order: 30,
    quickCreateRequirement: { mode: 'one', permission: 'task:realtime:create' },
    quickCreateLabel: '新建实时同步',
  },
  { id: 'batch-link-up-detail', path: '/sync/batch-link-up/:id/detail', title: '离线同步详情', component: './integration/batch-link-up/detail', hidden: true, parentId: 'batch-link-up' },
  { id: 'batch-link-up-single', path: '/sync/batch-link-up/:id/config/single', title: '单表同步配置', component: './integration/batch-link-up/config/single', hidden: true, parentId: 'batch-link-up' },
  { id: 'batch-link-up-multi', path: '/sync/batch-link-up/:id/config/multi', title: '多表同步配置', component: './integration/batch-link-up/config/multi', hidden: true, parentId: 'batch-link-up' },
  { id: 'batch-link-up-script', path: '/sync/batch-link-up/:id/config/script', title: '脚本同步配置', component: './integration/batch-link-up/config/script', hidden: true, parentId: 'batch-link-up' },
  { id: 'data-development', menuCode: YAK_OPS_MENU_CODES.dataDevelopment, mode: 'one', permission: 'data-development:read', path: '/data-development', title: '开发工作台', component: './development/data-development', iconKey: 'api', menuGroup: 'development', order: 10 },
  { id: 'data-development-release', menuCode: YAK_OPS_MENU_CODES.dataDevelopmentRelease, mode: 'one', permission: 'data-development:read', path: '/data-development/releases', title: '发布中心', component: './development/data-development/releases', iconKey: 'report', menuGroup: 'development', order: 20 },
  { id: 'data-development-execution', menuCode: YAK_OPS_MENU_CODES.dataDevelopmentExecution, mode: 'one', permission: 'data-development:read', path: '/data-development/executions', title: '开发运行记录', component: './development/data-development/executions', iconKey: 'report', menuGroup: 'development', order: 30 },
  { id: 'data-development-task-new', path: '/data-development/task/new', title: '新建开发任务', component: './development/data-development/task', hidden: true, parentId: 'data-development' },
  { id: 'data-development-task', path: '/data-development/task/:id', title: '开发任务配置', component: './development/data-development/task', hidden: true, parentId: 'data-development' },
  { id: 'workflow-definition', menuCode: YAK_OPS_MENU_CODES.workflowDefinition, mode: 'public', path: '/workflow/definitions', title: '工作流定义', component: './workflow/management', iconKey: 'workflow', menuGroup: 'workflow', order: 10 },
  { id: 'workflow-definition-editor', path: '/workflow/definition/:id', title: '工作流配置', component: './workflow/definition', hidden: true, parentId: 'workflow-definition' },
  { id: 'workflow-schedules', path: '/workflow/definition/:id/schedule', title: '调度配置', component: './workflow/schedules', hidden: true, parentId: 'workflow-definition' },
  { id: 'workflow-instances', menuCode: YAK_OPS_MENU_CODES.workflowInstances, mode: 'public', path: '/workflow/instances', title: '工作流实例', component: './workflow/instances', iconKey: 'instance', menuGroup: 'workflow', order: 30 },
  { id: 'workflow-instance-detail', path: '/workflow/instances/:executionId', title: '工作流实例详情', component: './workflow/instances/detail', hidden: true, parentId: 'workflow-instances' },
  { id: 'modeling-workspace', menuCode: YAK_OPS_MENU_CODES.modelingWorkspace, mode: 'one', permission: 'modeling:read', path: '/modeling', title: '模型工作台', component: './modeling', iconKey: 'database', menuGroup: 'modeling', order: 20 },
  { id: 'semantic-standards', menuCode: YAK_OPS_MENU_CODES.semanticStandards, mode: 'one', permission: 'semantic:read', path: '/semantic/standards', title: '数据标准', component: './semantic/standards', iconKey: 'database', menuGroup: 'semantic', order: 30 },
  { id: 'semantic-domains', menuCode: YAK_OPS_MENU_CODES.semanticDomains, mode: 'one', permission: 'semantic:read', path: '/semantic/domains', title: '业务域', component: './semantic/domains', iconKey: 'project', menuGroup: 'semantic', order: 10 },
  { id: 'semantic-processes', menuCode: YAK_OPS_MENU_CODES.semanticProcesses, mode: 'one', permission: 'semantic:read', path: '/semantic/processes', title: '业务过程', component: './semantic/processes', iconKey: 'workflow', menuGroup: 'semantic', order: 20 },
  { id: 'semantic-process-edit', path: '/semantic/processes/:id/edit', title: '业务过程编辑', component: './semantic/processes/edit', hidden: true, parentId: 'semantic-processes' },
  { id: 'semantic-fields', menuCode: YAK_OPS_MENU_CODES.semanticFields, mode: 'one', permission: 'semantic:read', path: '/semantic/fields', title: '标准字段', component: './semantic/fields', iconKey: 'knowledge', menuGroup: 'semantic', order: 40 },
  { id: 'semantic-layers', menuCode: YAK_OPS_MENU_CODES.semanticLayers, mode: 'one', permission: 'semantic:read', path: '/semantic/layers', title: '数仓分层', component: './semantic/layers', iconKey: 'database', menuGroup: 'semantic', order: 50 },
  { id: 'mdm-overview', menuCode: YAK_OPS_MENU_CODES.mdmOverview, mode: 'one', permission: 'mdm:read', path: '/mdm/overview', title: '主数据总览', component: './mdm/overview', iconKey: 'database', menuGroup: 'mdm', order: 10 },
  { id: 'mdm-modeling', menuCode: YAK_OPS_MENU_CODES.mdmModeling, mode: 'one', permission: 'mdm:read', path: '/mdm/modeling', title: '主数据建模', component: './mdm/modeling', iconKey: 'database', menuGroup: 'mdm', order: 20 },
  { id: 'mdm-identification', menuCode: YAK_OPS_MENU_CODES.mdmIdentification, mode: 'one', permission: 'mdm:read', path: '/mdm/identification', title: '主数据识别', component: './mdm/identification', iconKey: 'instance', menuGroup: 'mdm', order: 30 },
  { id: 'mdm-cleansing', menuCode: YAK_OPS_MENU_CODES.mdmCleansing, mode: 'one', permission: 'mdm:read', path: '/mdm/cleansing', title: '主数据清洗', component: './mdm/cleansing', iconKey: 'quality', menuGroup: 'mdm', order: 40 },
  { id: 'mdm-approval', menuCode: YAK_OPS_MENU_CODES.mdmApproval, mode: 'one', permission: 'mdm:read', path: '/mdm/approval', title: '主数据变更', component: './mdm/approval', iconKey: 'workflow', menuGroup: 'mdm', order: 50 },
  { id: 'mdm-entity-detail', path: '/mdm/modeling/:id', title: '主数据实体详情', component: './mdm/modeling/detail', hidden: true, parentId: 'mdm-modeling' },
  { id: 'metric-manage', menuCode: YAK_OPS_MENU_CODES.metricManage, mode: 'one', permission: 'metric:read', path: '/metric/manage', title: '指标管理', component: './metric/manage', iconKey: 'report', menuGroup: 'metric', order: 10 },
  { id: 'metric-detail', path: '/metric/manage/:id', title: '指标详情', component: './metric/detail', hidden: true, parentId: 'metric-manage' },
  { id: 'metric-lineage', menuCode: YAK_OPS_MENU_CODES.metricLineage, mode: 'one', permission: 'metric:read', path: '/metric/lineage', title: '指标血缘', component: './metric/lineage', iconKey: 'workflow', menuGroup: 'metric', order: 20 },
  { id: 'metric-impact', menuCode: YAK_OPS_MENU_CODES.metricImpact, mode: 'one', permission: 'metric:read', path: '/metric/impact', title: '影响分析', component: './metric/impact', iconKey: 'monitor', menuGroup: 'metric', order: 30 },
  { id: 'metric-service', menuCode: YAK_OPS_MENU_CODES.metricService, mode: 'one', permission: 'metric:read', path: '/metric/service', title: '指标标签与统计', component: './metric/service', iconKey: 'knowledge', menuGroup: 'metric', order: 40 },
  { id: 'data-lifecycle-policy', menuCode: YAK_OPS_MENU_CODES.lifecyclePolicy, mode: 'one', permission: 'data-lifecycle:read', path: '/data-lifecycle/policy', title: '生命周期策略', component: './data-lifecycle/policy', iconKey: 'database', menuGroup: 'data-lifecycle', order: 10 },
  { id: 'data-lifecycle-monitor', menuCode: YAK_OPS_MENU_CODES.lifecycleMonitor, mode: 'one', permission: 'data-lifecycle:read', path: '/data-lifecycle/monitor', title: 'TTL 运行监控', component: './data-lifecycle/monitor', iconKey: 'monitor', menuGroup: 'data-lifecycle', order: 20 },
  { id: 'data-lifecycle-storage', menuCode: YAK_OPS_MENU_CODES.lifecycleStorage, mode: 'one', permission: 'data-lifecycle:read', path: '/data-lifecycle/storage', title: '存储统计', component: './data-lifecycle/storage', iconKey: 'insight', menuGroup: 'data-lifecycle', order: 30 },
  { id: 'data-security-overview', menuCode: YAK_OPS_MENU_CODES.dataSecurityOverview, mode: 'one', permission: 'data-security:read', path: '/data-security/overview', title: '数据安全总览', component: './data-security/overview', iconKey: 'monitor', menuGroup: 'data-security', order: 10 },
  { id: 'data-security-classification', menuCode: YAK_OPS_MENU_CODES.dataSecurityClassification, mode: 'one', permission: 'data-security:read', path: '/data-security/classification', title: '分级分类', component: './data-security/classification', iconKey: 'database', menuGroup: 'data-security', order: 20 },
  { id: 'data-security-access', menuCode: YAK_OPS_MENU_CODES.dataSecurityAccess, mode: 'one', permission: 'data-security:read', path: '/data-security/access', title: '数据访问策略', component: './data-security/access', iconKey: 'system', menuGroup: 'data-security', order: 30 },
  { id: 'data-security-masking', menuCode: YAK_OPS_MENU_CODES.dataSecurityMasking, mode: 'one', permission: 'data-security:read', path: '/data-security/masking', title: '数据脱敏', component: './data-security/masking', iconKey: 'quality', menuGroup: 'data-security', order: 40 },
  { id: 'data-security-audit', menuCode: YAK_OPS_MENU_CODES.dataSecurityAudit, mode: 'one', permission: 'data-security:read', path: '/data-security/audit', title: '访问审计', component: './data-security/audit', iconKey: 'report', menuGroup: 'data-security', order: 50 },
  { id: 'data-security-compliance', menuCode: YAK_OPS_MENU_CODES.dataSecurityCompliance, mode: 'one', permission: 'data-security:read', path: '/data-security/compliance', title: '合规管理', component: './data-security/compliance', iconKey: 'insight', menuGroup: 'data-security', order: 60 },
  { id: 'approval-todo', menuCode: YAK_OPS_MENU_CODES.approvalTodo, mode: 'one', permission: 'data-approval:read', path: '/approval/todo', title: '我的待办', component: './approval/todo', iconKey: 'report', order: 10 },
  { id: 'approval-flows', menuCode: YAK_OPS_MENU_CODES.approvalFlows, mode: 'one', permission: 'data-approval:manage', path: '/approval/flows', title: '审批流程配置', component: './approval/flows', iconKey: 'workflow', menuGroup: 'system', order: 45 },
  { id: 'approval-detail', path: '/approval/instance/:id', title: '审批单详情', component: './approval/detail', hidden: true, parentId: 'approval-todo' },
  { id: 'data-metadata-overview', menuCode: YAK_OPS_MENU_CODES.metadataOverview, mode: 'one', permission: 'data-metadata:read', path: '/data-metadata/overview', title: '元数据概览', component: './data-metadata/overview', iconKey: 'insight', menuGroup: 'data-metadata', order: 10 },
  { id: 'data-metadata-collect', menuCode: YAK_OPS_MENU_CODES.metadataCollect, mode: 'one', permission: 'data-metadata:read', path: '/data-metadata/collect', title: '采集与对账', component: './data-metadata/collect', iconKey: 'sync', menuGroup: 'data-metadata', order: 40 },
  { id: 'data-asset-overview', menuCode: YAK_OPS_MENU_CODES.assetOverview, mode: 'one', permission: 'data-asset:read', path: '/data-asset/overview', title: '资产概览', component: './data-asset/overview', iconKey: 'insight', menuGroup: 'data-asset', order: 20 },
  { id: 'data-asset-catalog', menuCode: YAK_OPS_MENU_CODES.assetCatalog, mode: 'one', permission: 'data-asset:read', path: '/data-asset/catalog', title: '资产目录', component: './data-asset/catalog', iconKey: 'knowledge', menuGroup: 'data-asset', order: 10 },
  { id: 'data-asset-inventory', menuCode: YAK_OPS_MENU_CODES.assetInventory, mode: 'one', permission: 'data-asset:read', path: '/data-asset/inventory', title: '盘点上架', component: './data-asset/inventory', iconKey: 'quality', menuGroup: 'asset-management', order: 10 },
  { id: 'data-asset-taxonomy', menuCode: YAK_OPS_MENU_CODES.assetTaxonomy, mode: 'one', permission: 'data-asset:read', path: '/data-asset/taxonomy', title: '目录与标签', component: './data-asset/taxonomy', iconKey: 'database', menuGroup: 'asset-management', order: 20 },
  { id: 'data-asset-detail', path: '/data-asset/detail/:id', title: '资产详情', component: './data-asset/detail', hidden: true, parentId: 'data-asset-catalog' },
  { id: 'modeling-model-detail', path: '/modeling/models/:id', title: '模型详情', component: './modeling/unified', hidden: true, parentId: 'modeling-workspace' },
  { id: 'modeling-model-mapping', path: '/modeling/models/:id/mapping', title: '来源映射', component: './modeling/mapping', hidden: true, parentId: 'modeling-workspace' },
  { id: 'modeling-model-layer-mapping', path: '/modeling/models/:id/layer-mapping', title: '字段分层映射', component: './modeling/layer-mapping', hidden: true, parentId: 'modeling-workspace' },
  { id: 'modeling-mainline', path: '/modeling/mainline', title: '业务过程主线视图', component: './modeling/mainline', hidden: true, parentId: 'modeling-workspace' },
  { id: 'modeling-impact', path: '/modeling/impact', title: '变更影响分析', component: './modeling/impact', hidden: true, parentId: 'modeling-workspace' },
  { id: 'resource-management', menuCode: YAK_OPS_MENU_CODES.resourceManagement, mode: 'one', permission: 'resource:view', path: '/resource-management', title: '文件资源', component: './resources/resource-management', iconKey: 'database', menuGroup: 'integration', order: 40 },
  { id: 'data-quality-overview', menuCode: YAK_OPS_MENU_CODES.dataQualityOverview, mode: 'one', permission: 'quality:execution:read', path: '/data-quality/overview', title: '质量总览', component: './data-quality/overview', iconKey: 'monitor', menuGroup: 'data-quality', order: 5 },
  { id: 'data-quality-table-config', menuCode: YAK_OPS_MENU_CODES.dataQualityTableConfig, mode: 'one', permission: 'quality:monitor:read', path: '/data-quality/table-config', title: '数据表监控', component: './data-quality/table-config', iconKey: 'quality', menuGroup: 'data-quality', order: 10 },
  { id: 'data-quality-monitor-create', path: '/data-quality/monitor/create', title: '新增监控', component: './data-quality/monitor/editor', hidden: true, parentId: 'data-quality-table-config' },
  { id: 'data-quality-monitor-detail', path: '/data-quality/monitor/:id', title: '规则管理', component: './data-quality/monitor/detail', hidden: true, parentId: 'data-quality-table-config' },
  { id: 'data-quality-monitor-edit', path: '/data-quality/monitor/:id/edit', title: '编辑监控', component: './data-quality/monitor/editor', hidden: true, parentId: 'data-quality-table-config' },
  { id: 'data-quality-execution', menuCode: YAK_OPS_MENU_CODES.dataQualityExecution, mode: 'one', permission: 'quality:execution:read', path: '/data-quality/execution', title: '质量检查记录', component: './data-quality/execution', iconKey: 'report', menuGroup: 'data-quality', order: 20 },
  { id: 'data-quality-execution-detail', path: '/data-quality/execution/:executionNo', title: '质量检查详情', component: './data-quality/execution/detail', hidden: true, parentId: 'data-quality-execution' },
  { id: 'data-quality-rule-template', menuCode: YAK_OPS_MENU_CODES.dataQualityRuleTemplate, mode: 'one', permission: 'quality:template:read', path: '/data-quality/rule-template', title: '规则模板', component: './data-quality/rule-template', iconKey: 'quality', menuGroup: 'data-quality', order: 30 },
  { id: 'system-users', menuCode: YAK_SECURITY_MENU_CODES.users, mode: 'one', permission: 'security:user:read', path: '/system/users', title: '用户管理', component: './system/users', iconKey: 'system', menuGroup: 'system', order: 10 },
  { id: 'system-departments', menuCode: YAK_SECURITY_MENU_CODES.departments, mode: 'one', permission: 'security:department:read', path: '/system/departments', title: '部门管理', component: './system/departments', iconKey: 'system', menuGroup: 'system', order: 20 },
  { id: 'system-roles', menuCode: YAK_SECURITY_MENU_CODES.roles, mode: 'one', permission: 'security:role:read', path: '/system/roles', title: '角色与权限', component: './system/roles', iconKey: 'system', menuGroup: 'system', order: 30 },
  { id: 'system-permissions', menuCode: YAK_SECURITY_MENU_CODES.permissions, mode: 'one', permission: 'security:permission:read', path: '/system/permissions', title: '权限管理', component: './system/permissions', iconKey: 'system', hidden: true, order: 31 },
  { id: 'system-security-projects', menuCode: YAK_SECURITY_MENU_CODES.projects, mode: 'one', permission: 'security:project:read', path: '/system/projects', title: '项目空间', component: './system/security-projects', iconKey: 'system', menuGroup: 'system', hidden: !productFeatures.projectSpace, order: 5 },
  { id: 'system-resource-permissions', menuCode: YAK_SECURITY_MENU_CODES.resourcePermissions, mode: 'one', permission: 'security:resource-permission:read', path: '/system/resource-permissions', title: '资源授权', component: './system/resource-permissions', iconKey: 'system', menuGroup: 'system', hidden: !productFeatures.resourceAuthorization, order: 50 },
  { id: 'system-configs', menuCode: YAK_SECURITY_MENU_CODES.configs, mode: 'one', permission: 'security:config:read', path: '/system/configs', title: '系统配置', component: './system/configs', iconKey: 'system', menuGroup: 'system', hidden: !productFeatures.systemConfig, order: 60 },
  { id: 'system-operation-logs', menuCode: YAK_SECURITY_MENU_CODES.operationLogs, mode: 'one', permission: 'security:operation-log:read', path: '/system/oplogs', title: '操作日志', component: './system/oplogs', iconKey: 'system', menuGroup: 'system', order: 70 },
  { id: 'system-messages', mode: 'public', path: '/system/messages', title: '消息中心', component: './system/messages', iconKey: 'system', hidden: true, order: 90 },
];

const sortByOrder = <T extends { order?: number }>(left: T, right: T) =>
  (left.order ?? 0) - (right.order ?? 0);
const navigationSectionOrder: Record<NavigationSectionKey, number> = {
  business: 10,
  system: 20,
};
const sortNavigationGroups = (left: NavigationGroup, right: NavigationGroup) =>
  navigationSectionOrder[left.section] - navigationSectionOrder[right.section]
  || sortByOrder(left, right);
const routeMap = new Map(appRoutes.map((route) => [route.id, route]));
const groupMap = new Map(navigationGroups.map((group) => [group.id, group]));

/** Resolve the stable RBAC menu code, recursively inheriting it for hidden child routes. */
export const resolveNavigationMenuCode = (
  route: NavigationRoute,
): NavigationMenuCode | undefined => {
  const visited = new Set<string>();
  let candidate: NavigationRoute | undefined = route;
  while (candidate && !visited.has(candidate.id)) {
    visited.add(candidate.id);
    if (candidate.menuCode) return candidate.menuCode;
    candidate = candidate.parentId ? routeMap.get(candidate.parentId) : undefined;
  }
  return undefined;
};

export const canAccessNavigationRoute = (
  route: NavigationRoute,
  permissionCodes: readonly string[] | null | undefined,
  menuCodes?: readonly string[] | null,
) => {
  const visited = new Set<string>();
  let candidate: NavigationRoute | undefined = route;
  while (candidate && !visited.has(candidate.id)) {
    visited.add(candidate.id);
    if (candidate.mode && !satisfiesPermissionRequirement(
      permissionCodes,
      candidate as PermissionRequirement,
    )) return false;
    candidate = candidate.parentId ? routeMap.get(candidate.parentId) : undefined;
  }

  return hasMenuAccess(
    menuCodes,
    resolveNavigationMenuCode(route),
    permissionCodes,
    route.mode === 'public',
  );
};

const buildNavigationGroup = (
  group: NavigationGroup,
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
): NavigationGroupWithRoutes | undefined => {
  const accessible = (route: NavigationRoute) => route.menuGroup === group.id
    && !route.hidden
    && canAccessNavigationRoute(route, permissionCodes, menuCodes);
  const node: NavigationGroupWithRoutes = {
    ...group,
    routes: appRoutes.filter(accessible).sort(sortByOrder),
    subGroups: navigationGroups
      .filter((candidate) => candidate.parentGroupId === group.id)
      .map((candidate) => buildNavigationGroup(candidate, permissionCodes, menuCodes))
      .filter((child): child is NavigationGroupWithRoutes => Boolean(child))
      .sort(sortByOrder),
  };
  return node.routes.length > 0 || (node.subGroups?.length ?? 0) > 0 ? node : undefined;
};

export const getNavigationGroups = (
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
): NavigationGroupWithRoutes[] =>
  navigationGroups
    .filter((group) => !group.parentGroupId)
    .map((group) => buildNavigationGroup(group, permissionCodes, menuCodes))
    .filter((node): node is NavigationGroupWithRoutes => Boolean(node))
    .sort(sortNavigationGroups);
export const getMainNavigationGroups = getNavigationGroups;
export const getQuickCreateRoutes = (
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
) => appRoutes
  .filter((route) => Boolean(route.quickCreateLabel)
    && canAccessNavigationRoute(route, permissionCodes, menuCodes)
    && (!route.quickCreateRequirement || satisfiesPermissionRequirement(
      permissionCodes,
      route.quickCreateRequirement,
    )))
  .sort(sortByOrder);
export const getStandaloneNavigationRoutes = (
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
) => appRoutes
  .filter((route) => !route.menuGroup && !route.hidden
    && canAccessNavigationRoute(route, permissionCodes, menuCodes))
  .sort(sortByOrder);

const normalizePath = (path: string) =>
  path.split(/[?#]/, 1)[0].replace(/\/+$/, '') || '/';
const matchesRoute = (pattern: string, pathname: string) => {
  const patternParts = normalizePath(pattern).split('/').filter(Boolean);
  const pathParts = normalizePath(pathname).split('/').filter(Boolean);
  return patternParts.length === pathParts.length
    && patternParts.every((part, index) => part.startsWith(':') || part === pathParts[index]);
};
export const getRouteMetadata = (pathname: string) =>
  appRoutes.find((route) => matchesRoute(route.path, pathname));
export const getActiveNavigationId = (
  pathname: string,
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
) => {
  const route = getRouteMetadata(pathname);
  if (!route || !canAccessNavigationRoute(route, permissionCodes, menuCodes)) return undefined;
  return route.parentId ?? route.id;
};
export const getActiveNavigationGroupId = (
  pathname: string,
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
) => {
  const activeId = getActiveNavigationId(pathname, permissionCodes, menuCodes);
  return activeId ? routeMap.get(activeId)?.menuGroup : undefined;
};
/** Group ids from the deepest owning group up to its top-level domain. */
export const getActiveNavigationGroupPath = (
  pathname: string,
  permissionCodes?: readonly string[] | null,
  menuCodes?: readonly string[] | null,
): string[] => {
  const groupId = getActiveNavigationGroupId(pathname, permissionCodes, menuCodes);
  const path: string[] = [];
  const visited = new Set<string>();
  let candidate = groupId ? groupMap.get(groupId) : undefined;
  while (candidate && !visited.has(candidate.id)) {
    visited.add(candidate.id);
    path.push(candidate.id);
    candidate = candidate.parentGroupId ? groupMap.get(candidate.parentGroupId) : undefined;
  }
  return path;
};
