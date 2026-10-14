import {
  appRoutes, getMainNavigationGroups, getStandaloneNavigationRoutes,
  resolveNavigationMenuCode,
  type NavigationGroupWithRoutes,
} from './navigation';
import {
  DEFAULT_TASK_MENU, getTaskNavigationModel, taskNavigationIds,
} from './taskNavigation';

const allRoutes = (group: NavigationGroupWithRoutes): string[] => [
  ...group.routes.map(route => route.id),
  ...(group.subGroups ?? []).flatMap(allRoutes),
];

const countSpec = Object.values(DEFAULT_TASK_MENU).map(specs => specs.length);

describe('IA-550 navigation presentation without auth or route migration', () => {
  it('provides the 3+4+3+6+4 task structure from the approved planning candidate', () => {
    expect(countSpec).toEqual([3, 4, 3, 6, 4]);
    expect(countSpec.reduce((a, b) => a + b)).toBe(20);
    expect(Object.keys(DEFAULT_TASK_MENU)).toEqual([
      'integration', 'modeling', 'development', 'data-asset', 'data-analysis',
    ]);
  });

  it('never loses, doubles or grants access to any existing authorized route', () => {
    const groups = getMainNavigationGroups(['security:root']);
    expect(groups.map(group => group.id)).toEqual([
      'integration', 'modeling', 'development', 'data-asset', 'data-analysis', 'system',
    ]);
    for (const group of groups) {
      const sourceIds = allRoutes(group);
      const displayedIds = taskNavigationIds(getTaskNavigationModel(group));
      expect(new Set(displayedIds).size).toBe(displayedIds.length);
      expect(displayedIds.slice().sort()).toEqual(sourceIds.slice().sort());
      for (const id of displayedIds) {
        const declared = appRoutes.find(route => route.id === id)!;
        expect(declared.hidden).not.toBe(true);
        expect(declared.path).toMatch(/^\//);
        expect(resolveNavigationMenuCode(declared)).toBe(declared.menuCode);
      }
    }
    expect(getStandaloneNavigationRoutes(['security:root']).map(route => route.id))
      .toEqual(expect.arrayContaining(['home', 'approval-todo']));
  });

  it('preserves independent menu grants and permission for SQL audit without source READ', () => {
    const groups = getMainNavigationGroups(
      ['resource:sql-execution:read'], ['sql-execution-audit'],
    );
    const group = groups.find(x => x.id === 'integration');
    expect(group).toBeDefined();
    const model = getTaskNavigationModel(group!);
    expect(model.tasks).toHaveLength(0);
    expect(taskNavigationIds(model)).toEqual(['sql-execution-audit']);
    expect(model.tools[0].routes[0].path).toBe('/sql-execution-audit');
  });

  it.each([
    ['data-service:access', 'data-service-access', 'data-analysis', '/data-service/access'],
    ['data-service:observe', 'data-service-overview', 'data-analysis', '/data-service/overview'],
    ['quality:execution:read', 'data-quality-execution', 'data-asset', '/data-quality/execution'],
  ])('retains a separate project-level specialist tool for %s', (permission, code, parent, path) => {
    const groups = getMainNavigationGroups([permission], [code]);
    const group = groups.find(x => x.id === parent)!;
    expect(group).toBeDefined();
    const ids = taskNavigationIds(getTaskNavigationModel(group));
    expect(ids).toContain(code);
    const route = appRoutes.find(x => x.id === code)!;
    expect(route.path).toBe(path);
  });

  it('keeps model and semantic pages in task order but never alters their actual paths', () => {
    const group = getMainNavigationGroups(['security:root']).find(g => g.id === 'modeling')!;
    const model = getTaskNavigationModel(group);
    expect(model.tasks.map(x => x.title)).toEqual([
      '业务语义', '数据标准', '模型工作台', '指标中心',
    ]);
    expect(model.tasks.find(x => x.id === 'business')!.routes.map(r => r.path))
      .toEqual(['/semantic/domains', '/semantic/processes']);
    expect(model.tasks.find(x => x.id === 'standards')!.routes.map(r => r.path))
      .toEqual(['/semantic/standards', '/semantic/fields']);
    expect(model.tasks.find(x => x.id === 'models')!.routes.map(r => r.path))
      .toEqual(['/modeling']);
    expect(taskNavigationIds(model)).toContain('semantic-layers');
    expect(taskNavigationIds(model)).toContain('metric-impact');
  });
});
