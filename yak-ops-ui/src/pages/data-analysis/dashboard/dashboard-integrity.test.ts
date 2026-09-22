import {
  normalizeDashboardDocument,
  stripDashboardWidgetReferences,
} from './dashboard-integrity';
import type { DashboardDocument, DashboardWidget } from './model';

const widget = (id: string): DashboardWidget => ({
  id,
  title: id,
  x: 0,
  y: 0,
  w: 6,
  h: 6,
  inlineAnalysis: {
    type: 'bar',
    datasetId: `dataset-${id}`,
    dimensions: ['region'],
    metrics: [{ field: 'sales', aggregation: 'SUM' }],
    filters: [],
    style: {
      showLegend: false,
      showDataLabels: false,
      smooth: false,
      showGrid: true,
    },
  },
});

const baseDocument = (): DashboardDocument => ({
  version: 1,
  id: 'dashboard-1',
  name: 'Dashboard',
  activeDatasetId: 'dataset-a',
  widgets: [widget('a'), widget('b')],
  globalFilters: [{
    id: 'filter-region',
    name: '区域',
    operator: 'eq',
    bindings: [
      { widgetId: 'a', field: 'region' },
      { widgetId: 'b', field: 'region' },
    ],
  }],
  interactions: [{
    id: 'interaction-1',
    event: 'select',
    sourceWidgetId: 'a',
    sourceField: 'region',
    targetFilterId: 'filter-region',
  }],
});

describe('dashboard integrity', () => {
  it('defaults legacy snapshots to the light dashboard theme', () => {
    const normalized = normalizeDashboardDocument(baseDocument());

    expect(normalized.theme).toEqual({
      presetId: 'yak-light',
      canvas: undefined,
      component: undefined,
      chart: undefined,
    });
  });

  it('preserves supported dashboard theme overrides', () => {
    const document = baseDocument();
    document.theme = {
      presetId: 'yak-dark',
      canvas: { backgroundColor: '#101828' },
      chart: { palette: ['#35d0ff', '#8b5cf6'] },
    };

    const normalized = normalizeDashboardDocument(document);

    expect(normalized.theme?.presetId).toBe('yak-dark');
    expect(normalized.theme?.canvas?.backgroundColor).toBe('#101828');
    expect(normalized.theme?.chart?.palette).toEqual(['#35d0ff', '#8b5cf6']);
  });

  it('drops stale references and duplicate semantic links', () => {
    const document = baseDocument();
    document.widgets[0].inlineAnalysis!.dashboardBehavior = {
      crossFilters: [
        { id: 'cross-1', sourceField: 'region', targetWidgetId: 'b', targetField: 'region' },
        { id: 'cross-2', sourceField: 'region', targetWidgetId: 'b', targetField: 'region' },
        { id: 'cross-stale', sourceField: 'region', targetWidgetId: 'missing', targetField: 'region' },
      ],
    };
    document.globalFilters[0].bindings.push({ widgetId: 'missing', field: 'region' });
    document.interactions.push({
      id: 'interaction-stale',
      event: 'select',
      sourceWidgetId: 'missing',
      sourceField: 'region',
      targetFilterId: 'filter-region',
    });

    const normalized = normalizeDashboardDocument(document);

    expect(normalized.widgets[0].inlineAnalysis?.dashboardBehavior?.crossFilters).toEqual([
      { id: 'cross-1', sourceField: 'region', targetWidgetId: 'b', targetField: 'region' },
    ]);
    expect(normalized.globalFilters[0].bindings).toHaveLength(2);
    expect(normalized.interactions).toHaveLength(1);
  });

  it('removes incoming and outgoing edges when a widget is rebound', () => {
    const document = baseDocument();
    document.widgets[0].inlineAnalysis!.dashboardBehavior = {
      crossFilters: [{ id: 'a-b', sourceField: 'region', targetWidgetId: 'b', targetField: 'region' }],
    };
    document.widgets[1].inlineAnalysis!.dashboardBehavior = {
      crossFilters: [{ id: 'b-a', sourceField: 'region', targetWidgetId: 'a', targetField: 'region' }],
    };

    const next = stripDashboardWidgetReferences(document, 'a', false);

    expect(next.widgets).toHaveLength(2);
    expect(next.widgets[0].inlineAnalysis?.dashboardBehavior?.crossFilters).toBeUndefined();
    expect(next.widgets[1].inlineAnalysis?.dashboardBehavior?.crossFilters).toBeUndefined();
    expect(next.globalFilters[0].bindings).toEqual([{ widgetId: 'b', field: 'region' }]);
    expect(next.interactions).toEqual([]);
  });

  it('removes the widget itself when requested', () => {
    const next = stripDashboardWidgetReferences(baseDocument(), 'a', true);

    expect(next.widgets.map((item) => item.id)).toEqual(['b']);
    expect(next.globalFilters[0].bindings).toEqual([{ widgetId: 'b', field: 'region' }]);
    expect(next.interactions).toEqual([]);
  });
});
