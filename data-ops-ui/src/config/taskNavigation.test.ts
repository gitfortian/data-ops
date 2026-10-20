import {
  appRoutes, getMainNavigationGroups, getStandaloneNavigationRoutes,
  resolveNavigationMenuCode,
  type NavigationGroupWithRoutes,
} from './navigation';
import {
  CONTEXT_NAVIGATION_SECTIONS,
  contextNavigationRouteIds,
  getContextNavigationModel,
} from './taskNavigation';

const allRoutes = (group: NavigationGroupWithRoutes): string[] => [
  ...group.routes.map(route => route.id),
  ...(group.subGroups ?? []).flatMap(allRoutes),
];

describe('IA-550 domain-local navigation without the redundant third level', () => {
  it('keeps five business domains and the actual authorized page links', () => {
    const groups = getMainNavigationGroups(['security:root']);
    expect(groups.map(g => g.id)).toEqual([
      'integration', 'modeling', 'development', 'data-asset', 'data-analysis', 'system',
    ]);
    expect(Object.keys(CONTEXT_NAVIGATION_SECTIONS)).toEqual(groups.slice(0, 5).map(g => g.id));
    for (const group of groups) {
      const original = allRoutes(group);
      const presented = contextNavigationRouteIds(getContextNavigationModel(group));
      expect(new Set(presented).size).toBe(presented.length);
      expect(presented.slice().sort()).toEqual(original.slice().sort());
      for (const id of presented) {
        const route = appRoutes.find(r => r.id === id)!;
        expect(route.hidden).not.toBe(true);
        expect(route.path).toMatch(/^\//);
        expect(resolveNavigationMenuCode(route)).toBe(route.menuCode);
      }
    }
    expect(getStandaloneNavigationRoutes(['security:root']).map(r => r.id))
      .toEqual(expect.arrayContaining(['home', 'approval-todo']));
  });

  it('labels batch tasks as task management and keeps the independent explorer in source management', () => {
    const group = getMainNavigationGroups(['security:root']).find(g => g.id === 'integration')!;
    const model = getContextNavigationModel(group);
    const source = model.sections.find(section => section.id === 'source');
    const sync = model.sections.find(section => section.id === 'sync');
    expect(source?.routes.map(route => route.id)).toContain('data-source-explore');
    expect(source?.routes.find(route => route.id === 'data-source')?.title).toBe('数据源管理');
    expect(sync?.routes.map(route => route.id)).toEqual(['batch-link-up']);
    expect(sync?.routes[0]?.title).toBe('任务管理');
  });

  it('uses static context captions instead of a repeated clickable task label', () => {
    const group = getMainNavigationGroups(['security:root']).find(g => g.id === 'modeling')!;
    const model = getContextNavigationModel(group);
    expect(model.sections.map(s => s.title)).toEqual(['业务定义', '标准规范', '模型设计', '指标体系']);
    expect(model.sections.find(s => s.id === 'business')?.routes.map(r => r.path))
      .toEqual(['/semantic/domains', '/semantic/processes']);
    expect(model.sections.find(s => s.id === 'standards')?.routes.map(r => r.path))
      .toEqual(['/semantic/standards', '/semantic/fields', '/semantic/layers']);
    expect(model.sections.find(s => s.id === 'metrics')?.routes.map(r => r.id))
      .toEqual(['metric-manage', 'metric-lineage', 'metric-impact', 'metric-service']);
    expect(model.sections.every(s => s.visibleCount >= 1)).toBe(true);
    expect(model.sections.find(s => s.id === 'standards')?.visibleCount).toBe(2);
  });

  it('preserves independent SQL audit access without the DataSource read permission', () => {
    const group = getMainNavigationGroups(['resource:sql-execution:read'],
      ['sql-execution-audit']).find(g => g.id === 'integration')!;
    const model = getContextNavigationModel(group);
    expect(model.sections).toHaveLength(1);
    expect(model.sections[0].routes.map(r => r.path)).toEqual(['/sql-execution-audit']);
    expect(model.sections[0].visibleCount).toBe(1);
  });

  it.each([
    ['data-service:access', 'data-service-access', 'data-analysis', '/data-service/access'],
    ['data-service:observe', 'data-service-overview', 'data-analysis', '/data-service/overview'],
    ['quality:execution:read', 'data-quality-execution', 'data-asset', '/data-quality/execution'],
  ])('shows only authorized contextual pages for %s', (permission, id, domain, path) => {
    const group = getMainNavigationGroups([permission], [id]).find(g => g.id === domain)!;
    expect(group).toBeDefined();
    const model = getContextNavigationModel(group);
    expect(contextNavigationRouteIds(model)).toContain(id);
    expect(model.sections.find(s => s.routes.some(r => r.id === id))?.routes
      .some(r => r.path === path)).toBe(true);
    expect(contextNavigationRouteIds(model)).not.toContain('data-source');
  });

  it('excludes realtime-sync from the integration context sections even with full grants', () => {
    const group = getMainNavigationGroups(['security:root']).find(g => g.id === 'integration')!;
    const model = getContextNavigationModel(group);
    const sync = model.sections.find(section => section.id === 'sync');
    expect(sync?.routes.map(route => route.id)).toEqual(['batch-link-up']);
    expect(sync?.visibleCount).toBe(1);
    expect(contextNavigationRouteIds(model)).not.toContain('realtime-sync');
  });

  it('keeps all authorized Metadata, quality, security and MDM pages inside their original business neighborhood', () => {
    const asset = getMainNavigationGroups(['security:root']).find(g => g.id === 'data-asset')!;
    const model = getContextNavigationModel(asset);
    const section = (id: string) => model.sections.find(s => s.id === id)!;
    expect(section('metadata').routes.map(r => r.id))
      .toEqual(['data-metadata-overview', 'data-metadata-collect']);
    expect(section('quality').routes.map(r => r.id)).toEqual(expect.arrayContaining([
      'data-quality-execution', 'data-quality-rule-template',
    ]));
    expect(section('security').routes).toHaveLength(6);
    expect(section('mdm').routes).toHaveLength(5);
    expect(contextNavigationRouteIds(model)).toContain('data-analysis-lineage');
  });
});
