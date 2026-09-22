import type { ScreenComponent, ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import {
  buildScreenDatasetQueryPayload,
  canQueryScreenComponent,
  isBindableScreenComponent,
} from './binding';
import {
  collectScreenRuntimeDatasetIds,
  planScreenRuntimeQueries,
} from './planner';
import { screenRuntimeComponentRegistry } from './registry/builtin-plugins';

const component = (type: ScreenComponent['type'], id = type) => ({
  id,
  type,
  x: 0,
  y: 0,
  width: 100,
  height: 100,
}) as ScreenComponent;

const binding = {
  datasetId: '12',
  dimensions: ['region'],
  metrics: [{ field: 'amount', aggregation: 'SUM' as const }],
};

describe('digital screen runtime roles', () => {
  it('registers every supported component type explicitly', () => {
    expect(screenRuntimeComponentRegistry.list().map((plugin) => plugin.type).sort()).toEqual([
      'bar', 'line', 'map', 'metric', 'pie', 'table', 'text', 'ticker',
    ]);
  });

  it('keeps binding/query semantics inside component plugins', () => {
    expect(isBindableScreenComponent(component('text'))).toBe(false);
    expect(isBindableScreenComponent(component('bar'))).toBe(true);
    expect(canQueryScreenComponent(component('bar'), binding)).toBe(true);
    expect(buildScreenDatasetQueryPayload(component('table'), binding).limit).toBe(100);
    expect(buildScreenDatasetQueryPayload(component('metric'), binding).dimensions).toEqual([]);
  });

  it('planner only schedules queryable components with an available dataset', () => {
    const template = {
      components: [component('bar', 'bound'), component('text', 'static'), component('pie', 'missing')],
    } as ScreenTemplate;
    const bindings: DigitalScreenBindings = {
      bound: binding,
      missing: { ...binding, datasetId: '404' },
    };
    const datasets = [{ id: '12' }] as PublishedDataset[];

    expect(planScreenRuntimeQueries(template, bindings, datasets).map((item) => item.component.id))
      .toEqual(['bound']);
  });

  it('assigns the same query key to different component types with an identical Dataset query', () => {
    const template = {
      components: [component('line', 'line'), component('bar', 'bar')],
    } as ScreenTemplate;
    const bindings: DigitalScreenBindings = { line: binding, bar: binding };
    const datasets = [{ id: '12', currentVersionNo: 3 }] as PublishedDataset[];
    const plan = planScreenRuntimeQueries(template, bindings, datasets);

    expect(plan).toHaveLength(2);
    expect(plan[0].queryKey).toBe(plan[1].queryKey);
  });

  it('collects only Dataset ids for queryable components in the active template', () => {
    const template = {
      components: [
        component('line', 'line'),
        component('bar', 'bar'),
        component('map', 'map'),
      ],
    } as ScreenTemplate;
    const bindings: DigitalScreenBindings = {
      line: binding,
      bar: binding,
      map: { ...binding, datasetId: '77' },
      removed: { ...binding, datasetId: '99' },
    };

    expect(collectScreenRuntimeDatasetIds(template, bindings)).toEqual(['12']);
  });
});
