import {
  appRoutes, canAccessNavigationRoute, getActiveNavigationId,
  getMainNavigationGroups, getRouteMetadata,
} from './navigation';
import { getModelingNavigationItem, MODELING_NAVIGATION_ITEMS } from './modelingNavigation';
import { getContextNavigationModel } from './taskNavigation';

const modelingRoutes = (permissions: string[], menus: string[]) => {
  const group = getMainNavigationGroups(permissions, menus).find(item => item.id === 'modeling');
  return group ? getContextNavigationModel(group).sections.flatMap(section => section.routes) : [];
};

it('keeps five modeling destinations within one authorized workspace menu', () => {
  const permissions = ['modeling:read'];
  const menus = ['modeling-workspace'];
  const routes = modelingRoutes(permissions, menus);
  expect(routes.filter(item => item.id === 'modeling-workspace')).toHaveLength(1);
  expect(MODELING_NAVIGATION_ITEMS.map(item => item.title))
    .toEqual(['建模总览', '逻辑模型', '物理模型', '过程覆盖', '变更影响']);
  expect(new Set(MODELING_NAVIGATION_ITEMS.map(item => item.id)).size).toBe(5);
  for (const link of MODELING_NAVIGATION_ITEMS) {
    const pathname = link.path.split('?')[0];
    const search = link.path.includes('?') ? link.path.slice(link.path.indexOf('?')) : '';
    expect(getModelingNavigationItem(pathname, search)?.id).toBe(link.id);
    const ownerRoute = getRouteMetadata(link.path);
    expect(ownerRoute).toBeDefined();
    expect(canAccessNavigationRoute(ownerRoute!, permissions, menus)).toBe(true);
    expect(getActiveNavigationId(link.path, permissions, menus)).toBe('modeling-workspace');
  }
  expect(appRoutes.filter(route => route.id === 'modeling-workspace')).toHaveLength(1);
});

it.each([
  [[], ['modeling-workspace']],
  [['modeling:read'], []],
  [['semantic:read'], ['semantic-standards']],
])('does not expose a Modeling entry without owner menu and permission', (permissions, menus) => {
  expect(modelingRoutes(permissions, menus).some(link => link.id === 'modeling-workspace')).toBe(false);
  for (const link of MODELING_NAVIGATION_ITEMS) {
    expect(getActiveNavigationId(link.path, permissions, menus)).not.toBe('modeling-workspace');
  }
});

it.each([
  ['/modeling', '', 'modeling-overview'],
  ['/modeling/', '?view=overview&processId=7', 'modeling-overview'],
  ['/modeling', '?view=list&layerCode=DIM&processId=7', 'modeling-physical'],
  ['/modeling', '?view=invalid', 'modeling-overview'],
  ['/modeling', '?sourceJourney=ods&view=overview', 'modeling-physical'],
  ['/modeling/logical', '?draftId=991&processId=7', 'modeling-logical'],
  ['/modeling/models/701', '?tab=version', 'modeling-physical'],
  ['/modeling/models/701/mapping', '?column=order_id', 'modeling-physical'],
  ['/modeling/models/701/layer-mapping', '', 'modeling-physical'],
  ['/modeling/mainline', '?processId=7', 'modeling-coverage'],
  ['/modeling/impact', '?processId=7', 'modeling-impact'],
])('classifies authorized workspace location %s %s without adding a new menu', (pathname, search, id) => {
  expect(getModelingNavigationItem(pathname, search)?.id).toBe(id);
  expect(getActiveNavigationId(pathname+search, ['modeling:read'], ['modeling-workspace']))
    .toBe('modeling-workspace');
});

it('does not classify non-modeling or unknown paths as modeling tasks', () => {
  expect(getModelingNavigationItem('/semantic/standards')).toBeUndefined();
  expect(getModelingNavigationItem('/modeling-other')).toBeUndefined();
  expect(getModelingNavigationItem('/modeling/models/701/unknown')).toBeUndefined();
  expect(getActiveNavigationId('/modeling/logical', [], ['modeling-workspace'])).not.toBe('modeling-workspace');
});
