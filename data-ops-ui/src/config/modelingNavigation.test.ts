import {
  appRoutes, canAccessNavigationRoute, getActiveNavigationId,
  getMainNavigationGroups, getRouteMetadata,
} from './navigation';
import { getModelingNavigationItem, MODELING_NAVIGATION_ITEMS } from './modelingNavigation';
import { getActiveContextNavigationId, getContextNavigationLinks, getContextNavigationModel } from './taskNavigation';

const modelingLinks = (permissions: string[], menus: string[]) => {
  const group = getMainNavigationGroups(permissions, menus).find(item => item.id === 'modeling');
  return group ? getContextNavigationModel(group).sections.flatMap(getContextNavigationLinks) : [];
};

it('projects five existing destinations from the one granted Modeling menu without inventing menu codes', () => {
  const permissions = ['modeling:read'];
  const menus = ['modeling-workspace'];
  const links = modelingLinks(permissions, menus);
  expect(links.map(item => item.title)).toEqual(['建模总览', '逻辑模型', '物理模型', '过程覆盖', '变更影响']);
  expect(new Set(links.map(item => item.id)).size).toBe(5);
  for (const link of links) {
    expect(link.ownerRouteId).toBe('modeling-workspace');
    const destination = getRouteMetadata(link.path);
    expect(destination).toBeDefined();
    expect(canAccessNavigationRoute(destination!, permissions, menus)).toBe(true);
    expect(getActiveContextNavigationId(
      getActiveNavigationId(link.path, permissions, menus),
      link.path.split('?')[0], link.path.includes('?') ? link.path.slice(link.path.indexOf('?')) : '',
    )).toBe(link.id);
  }
  expect(appRoutes.filter(route => route.id === 'modeling-workspace')).toHaveLength(1);
});

it.each([
  [[], ['modeling-workspace']],
  [['modeling:read'], []],
  [['semantic:read'], ['semantic-standards']],
])('does not expose modeling shortcuts without both the original menu and permission', (permissions, menus) => {
  expect(modelingLinks(permissions, menus).some(link => link.ownerRouteId === 'modeling-workspace')).toBe(false);
  for (const link of MODELING_NAVIGATION_ITEMS) {
    const owner = getActiveNavigationId(link.path, permissions, menus);
    expect(getActiveContextNavigationId(owner, link.path.split('?')[0])).toBeUndefined();
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
])('keeps %s %s in its exact task, including existing deep links', (pathname, search, id) => {
  expect(getModelingNavigationItem(pathname, search)?.id).toBe(id);
  expect(getActiveContextNavigationId('modeling-workspace', pathname, search)).toBe(id);
});

it('does not reclassify another authorized domain or unknown URLs', () => {
  expect(getActiveContextNavigationId('semantic-standards', '/semantic/standards')).toBe('semantic-standards');
  expect(getModelingNavigationItem('/modeling-other')).toBeUndefined();
  expect(getModelingNavigationItem('/modeling/models/701/unknown')).toBeUndefined();
  expect(getActiveContextNavigationId(undefined, '/modeling/logical')).toBeUndefined();
});
