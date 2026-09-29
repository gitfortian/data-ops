import { YAK_OPS_MENU_CODES } from '../constants/securityMenuCodes';
import {
  appRoutes,
  canAccessNavigationRoute,
  getActiveNavigationGroupPath,
  getActiveNavigationId,
  getMainNavigationGroups,
  getQuickCreateRoutes,
  getStandaloneNavigationRoutes,
  resolveNavigationMenuCode,
  type NavigationGroupWithRoutes,
} from './navigation';

describe('permission-aware navigation', () => {
  const batchRead = ['task:batch:read'];
  const developmentRead = ['data-development:read'];
  const dataServiceRead = ['data-service:read'];
  const dataServiceAll = [
    'data-service:read',
    'data-service:runtime',
    'data-service:observe',
  ];

  const findGroup = (
    groups: NavigationGroupWithRoutes[],
    id: string,
  ): NavigationGroupWithRoutes | undefined => {
    for (const group of groups) {
      if (group.id === id) return group;
      const subGroup = group.subGroups?.find((candidate) => candidate.id === id);
      if (subGroup) return subGroup;
    }
    return undefined;
  };

  it('uses route permission metadata and lets details inherit their parent', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;
    expect(canAccessNavigationRoute(list, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(list, [])).toBe(false);
    expect(canAccessNavigationRoute(detail, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(detail, [])).toBe(false);
    expect(getActiveNavigationId('/sync/batch-link-up/42/detail', batchRead)).toBe('batch-link-up');
  });

  it('uses stable menu codes for protected routes and hidden descendants', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;

    expect(list.menuCode).toBe(YAK_OPS_MENU_CODES.batchLinkUp);
    expect(resolveNavigationMenuCode(detail)).toBe(YAK_OPS_MENU_CODES.batchLinkUp);
    expect(
      canAccessNavigationRoute(list, batchRead, [YAK_OPS_MENU_CODES.batchLinkUp]),
    ).toBe(true);
    expect(
      canAccessNavigationRoute(list, batchRead, [YAK_OPS_MENU_CODES.dataSource]),
    ).toBe(false);
    expect(
      canAccessNavigationRoute(detail, batchRead, [YAK_OPS_MENU_CODES.batchLinkUp]),
    ).toBe(true);
    expect(getActiveNavigationId(
      '/sync/batch-link-up/42/detail',
      batchRead,
      [YAK_OPS_MENU_CODES.dataSource],
    )).toBeUndefined();
  });

  it('keeps public groups while filtering permission-protected groups and quick-create independently', () => {
    // 工作流(公开)挂在「数据开发与编排」域下、全局血缘(公开)挂在「数据资产」
    // 域下，因此无权限用户也能看到这三个域。
    expect(getMainNavigationGroups([]).map((group) => group.id)).toEqual([
      'development',
      'data-asset',
      'data-analysis',
    ]);
    expect(getMainNavigationGroups(batchRead).map((group) => group.id)).toEqual([
      'integration',
      'development',
      'data-asset',
      'data-analysis',
    ]);
    expect(getMainNavigationGroups(developmentRead).map((group) => group.id)).toEqual([
      'development',
      'data-asset',
      'data-analysis',
    ]);
    expect(getMainNavigationGroups(dataServiceRead).map((group) => group.id)).toEqual([
      'development',
      'data-asset',
      'data-analysis',
    ]);
    expect(getQuickCreateRoutes(batchRead)).toEqual([]);
    expect(getQuickCreateRoutes([...batchRead, 'task:batch:create']).map((route) => route.id)).toEqual(['batch-link-up']);
  });

  it('uses menu grants when the backend menu contract is present', () => {
    expect(
      getMainNavigationGroups(batchRead, []).map((group) => group.id),
    ).toEqual(['development', 'data-asset', 'data-analysis']);
    expect(
      getMainNavigationGroups(
        batchRead,
        [YAK_OPS_MENU_CODES.batchLinkUp],
      ).map((group) => group.id),
    ).toEqual(['integration', 'development', 'data-asset', 'data-analysis']);
    expect(
      getQuickCreateRoutes(
        [...batchRead, 'task:batch:create'],
        [],
      ),
    ).toEqual([]);
    expect(
      getQuickCreateRoutes(
        [...batchRead, 'task:batch:create'],
        [YAK_OPS_MENU_CODES.batchLinkUp],
      ).map((route) => route.id),
    ).toEqual(['batch-link-up']);
  });

  it('keeps sidebar groups contiguous by navigation section', () => {
    const groups = getMainNavigationGroups(['security:root']);
    expect(groups.map((group) => group.id)).toEqual([
      'integration',
      'development',
      'modeling',
      'governance',
      'data-asset',
      'data-analysis',
      'approval',
      'system',
    ]);
    expect(groups.map((group) => group.section)).toEqual([
      'business',
      'business',
      'business',
      'business',
      'business',
      'business',
      'business',
      'system',
    ]);
    const governance = groups.find((group) => group.id === 'governance')!;
    expect(governance.routes).toEqual([]);
    expect(governance.subGroups?.map((sub) => sub.id)).toEqual([
      'mdm',
      'data-quality',
      'data-security',
      'data-lifecycle',
    ]);
  });

  it('nests sub-group domains and expands the active route up to its domain', () => {
    expect(
      getActiveNavigationGroupPath('/data-quality/overview', ['quality:execution:read']),
    ).toEqual(['data-quality', 'governance']);
    expect(
      getActiveNavigationGroupPath('/data-source', ['resource:data-source:read']),
    ).toEqual(['integration']);
    expect(getActiveNavigationGroupPath('/workflow/definitions', [])).toEqual([
      'workflow',
      'development',
    ]);
    expect(getActiveNavigationGroupPath('/home', [])).toEqual([]);
  });

  it('keeps consumption entries and moves global lineage into the asset domain', () => {
    const dataConsumption = getMainNavigationGroups([]).find(
      (group) => group.id === 'data-analysis',
    );
    expect(dataConsumption?.title).toBe('消费与服务');
    expect(dataConsumption?.routes.map((route) => route.id)).toEqual([
      'dashboard',
      'dataset-management',
      'digital-screen',
    ]);
    const assets = getMainNavigationGroups(['security:root']).find(
      (group) => group.id === 'data-asset',
    );
    expect(assets?.routes.map((route) => route.id)).toEqual([
      'data-asset-overview',
      'data-asset-catalog',
      'data-asset-inventory',
      'data-asset-taxonomy',
      'data-analysis-lineage',
    ]);
    expect(getActiveNavigationId('/dashboard', [])).toBe('dashboard');
    expect(getActiveNavigationId('/dashboard/new', [])).toBe('dashboard');
    expect(getActiveNavigationId('/dashboard/42', [])).toBe('dashboard');
    expect(getActiveNavigationId('/data-analysis/chart-analysis', [])).toBe('dashboard');
  });

  it('gates data-service pages by read, runtime and observe permissions', () => {
    expect(
      findGroup(getMainNavigationGroups([]), 'data-service'),
    ).toBeUndefined();
    const readOnly = findGroup(
      getMainNavigationGroups(dataServiceRead),
      'data-service',
    );
    expect(readOnly?.routes.map((route) => route.id)).toEqual(['data-service-api']);
    expect(getActiveNavigationId('/data-service/api/42', dataServiceRead)).toBe('data-service-api');
    expect(getActiveNavigationId('/data-service/debug', dataServiceRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-service/debug', ['data-service:runtime'])).toBeUndefined();
    expect(getActiveNavigationId('/data-service/overview', dataServiceRead)).toBeUndefined();

    const full = findGroup(getMainNavigationGroups(dataServiceAll), 'data-service');
    expect(full?.title).toBe('API 服务');
    expect(full?.routes.map((route) => route.id)).toEqual([
      'data-service-api',
      'data-service-debug',
      'data-service-overview',
      'data-service-logs',
    ]);
    expect(getActiveNavigationId('/data-service', dataServiceAll)).toBe('data-service-api');
    expect(getActiveNavigationId('/data-service/debug', dataServiceAll)).toBe('data-service-debug');
    expect(getActiveNavigationId('/data-service/overview', dataServiceAll)).toBe('data-service-overview');
    expect(getActiveNavigationId('/data-service/logs', dataServiceAll)).toBe('data-service-logs');
  });

  it('registers home as the only standalone navigation entry', () => {
    expect(getStandaloneNavigationRoutes(['security:root']).map((route) => route.id)).toEqual([
      'home',
    ]);
    expect(getActiveNavigationId('/home', [])).toBe('home');
  });

  it('moved data-source into the integration domain with its menu grant intact', () => {
    expect(
      getStandaloneNavigationRoutes(
        ['resource:data-source:read'],
        [YAK_OPS_MENU_CODES.dataSource],
      ).map((route) => route.id),
    ).toEqual(['home']);
    const integration = getMainNavigationGroups(
      ['resource:data-source:read'],
      [YAK_OPS_MENU_CODES.dataSource],
    ).find((group) => group.id === 'integration');
    expect(integration?.routes.map((route) => route.id)).toEqual(['data-source']);
  });

  it('keeps the personal settings page addressable without exposing it in the main sidebar', () => {
    expect(getStandaloneNavigationRoutes([]).map((route) => route.id)).not.toContain('settings');
    expect(getActiveNavigationId('/settings', [])).toBe('settings');
  });

  it('requires data-development read permission for workbench, releases, executions and child routes', () => {
    const development = findGroup(getMainNavigationGroups(developmentRead), 'development');
    expect(development?.routes.map((route) => route.id)).toEqual([
      'data-development',
      'data-development-release',
      'data-development-execution',
    ]);
    // 无开发权限时域仍因公开的工作流子组可见，但开发页面全部消失。
    const anon = findGroup(getMainNavigationGroups([]), 'development');
    expect(anon?.routes).toEqual([]);
    expect(getActiveNavigationId('/data-development', [])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/task/42', [])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/task/42', developmentRead)).toBe(
      'data-development',
    );
    expect(getActiveNavigationId('/data-development/releases', developmentRead)).toBe(
      'data-development-release',
    );
    expect(getActiveNavigationId('/data-development/executions', developmentRead)).toBe(
      'data-development-execution',
    );
  });

  it('registers the data-quality MVP pages and hidden monitor routes', () => {
    const qualityPermissions = [
      'quality:monitor:read',
      'quality:execution:read',
      'quality:template:read',
    ];
    const qualityGroup = findGroup(
      getMainNavigationGroups(qualityPermissions),
      'data-quality',
    );
    expect(qualityGroup?.routes.map((route) => route.id)).toEqual([
      'data-quality-overview',
      'data-quality-table-config',
      'data-quality-execution',
      'data-quality-rule-template',
    ]);
    expect(getActiveNavigationId('/data-quality/monitor/create', qualityPermissions)).toBe(
      'data-quality-table-config',
    );
    expect(getActiveNavigationId('/data-quality/monitor/42', qualityPermissions)).toBe(
      'data-quality-table-config',
    );
    expect(getActiveNavigationId('/data-quality/execution', qualityPermissions)).toBe(
      'data-quality-execution',
    );
    expect(
      getActiveNavigationId('/data-quality/execution/QM-20260807095619-ABC123', qualityPermissions),
    ).toBe('data-quality-execution');
  });

  it('does not expose removed modules', () => {
    expect(getActiveNavigationId('/sync/realtime-link-up', ['task:realtime:read'])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/workbench', developmentRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-quality/report', ['quality:report:read'])).toBeUndefined();
  });

  it('keeps the MDM change ledger inside the mdm group', () => {
    const mdm = findGroup(getMainNavigationGroups(['security:root']), 'mdm')!;
    // 台账查询与「撤回在途变更」只在这一页（通过/拒绝在审批中心），M0 重排时曾被连带退役。
    expect(mdm.routes.map((route) => route.id)).toEqual([
      'mdm-overview',
      'mdm-modeling',
      'mdm-identification',
      'mdm-cleansing',
      'mdm-approval',
    ]);
    expect(getActiveNavigationId('/mdm/approval', ['mdm:read'])).toBe('mdm-approval');
  });
});
