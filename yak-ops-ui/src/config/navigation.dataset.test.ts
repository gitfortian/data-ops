import {
  appRoutes,
  getActiveNavigationId,
  getNavigationGroups,
  getRouteMetadata,
} from './navigation';

describe('Dataset management navigation', () => {
  it('registers Dataset as the visible data-consumption entry', () => {
    const group = getNavigationGroups([]).find((item) => item.id === 'data-analysis');

    expect(group?.routes.some((route) => route.id === 'dataset-management')).toBe(true);
    expect(group?.routes.some((route) => route.id === 'data-analysis-catalog')).toBe(false);
  });

  it('keeps Dataset detail under the Dataset navigation item', () => {
    expect(getRouteMetadata('/dataset/123')?.id).toBe('dataset-management-detail');
    expect(getActiveNavigationId('/dataset/123', [])).toBe('dataset-management');
  });

  it('keeps the legacy catalog route addressable but hidden', () => {
    const route = appRoutes.find((item) => item.id === 'data-analysis-catalog');

    expect(route?.path).toBe('/data-analysis/data-catalog');
    expect(route?.hidden).toBe(true);
  });
});
