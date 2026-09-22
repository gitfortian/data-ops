import { buildAnalysisChartOption } from './chart-options';
import {
  ANALYSIS_ENCODING_RULES,
  rebindAnalysisEncoding,
} from './encoding';
import type {
  AnalysisSpec,
  ChartType,
  DatasetQueryResult,
  PublishedDataset,
} from './model';

const dataset: PublishedDataset = {
  id: 'dataset-1',
  name: '订单分析',
  description: '',
  status: 'ONLINE',
  sourceTaskId: 'task-1',
  sourceTaskName: '订单同步',
  currentVersionNo: 1,
  updatedAt: '2026-08-18T00:00:00Z',
  fields: [
    { key: 'category', label: '区域', physicalName: 'category', dataType: 'string', role: 'dimension', nullable: false },
    { key: 'segment', label: '客群', physicalName: 'segment', dataType: 'string', role: 'dimension', nullable: false },
    { key: 'sales', label: '销售额', physicalName: 'sales', dataType: 'number', role: 'metric', nullable: false },
    { key: 'profit', label: '利润', physicalName: 'profit', dataType: 'number', role: 'metric', nullable: false },
    { key: 'orders', label: '订单数', physicalName: 'orders', dataType: 'number', role: 'metric', nullable: false },
  ],
};

const result: DatasetQueryResult = {
  datasetId: dataset.id,
  datasetVersionId: 'version-1',
  datasetVersionNo: 1,
  bindings: [
    { key: 'd0', fieldId: 'category', displayName: '区域', dataType: 'STRING', aggregation: null },
    { key: 'd1', fieldId: 'segment', displayName: '客群', dataType: 'STRING', aggregation: null },
    { key: 'm0', fieldId: 'sales', displayName: '销售额', dataType: 'NUMBER', aggregation: 'SUM' },
    { key: 'm1', fieldId: 'profit', displayName: '利润', dataType: 'NUMBER', aggregation: 'SUM' },
    { key: 'm2', fieldId: 'orders', displayName: '订单数', dataType: 'NUMBER', aggregation: 'SUM' },
  ],
  columns: [],
  rows: [
    ['华东', 'A', 100, 20, 5],
    ['华东', 'B', 80, 16, 4],
    ['华南', 'A', 60, 18, 3],
    ['华南', 'B', 40, 12, 2],
  ],
  returnedRows: 4,
  truncated: false,
  elapsedMillis: 1,
};

const specFor = (
  type: ChartType,
  metrics = ['sales'],
  color = false,
): AnalysisSpec => ({
  type,
  datasetId: dataset.id,
  dimensions: color ? ['category', 'segment'] : ['category'],
  metrics: metrics.map((field) => ({ field, aggregation: 'SUM' as const })),
  encoding: {
    version: 1,
    category: [{ field: 'category', role: 'dimension' }],
    value: metrics.map((field) => ({ field, role: 'metric' as const, aggregation: 'SUM' as const })),
    color: color ? [{ field: 'segment', role: 'dimension' }] : [],
    size: [],
    label: [],
    detail: [],
    tooltip: [],
  },
  filters: [],
  style: {
    showLegend: true,
    showDataLabels: false,
    smooth: true,
    showGrid: true,
  },
});

describe('advanced chart encoding contracts', () => {
  it('requires exactly two value metrics for scatter', () => {
    const value = ANALYSIS_ENCODING_RULES.scatter.find((rule) => rule.channel === 'value');
    expect(value).toMatchObject({ min: 2, max: 2, roles: ['metric'] });
  });

  it('seeds a missing second metric when switching to scatter', () => {
    const rebound = rebindAnalysisEncoding(specFor('scatter', ['sales']), dataset);
    expect(rebound.metrics.map((metric) => metric.field)).toEqual(['sales', 'profit']);
  });

  it('supports multiple radar metric axes', () => {
    const value = ANALYSIS_ENCODING_RULES.radar.find((rule) => rule.channel === 'value');
    expect(value).toMatchObject({ min: 2, max: 5 });
  });
});

describe('advanced chart options', () => {
  it('renders stacked bars with a shared stack', () => {
    const option = buildAnalysisChartOption(specFor('stackedBar', ['sales', 'profit']), dataset, result) as any;
    expect(option.series).toHaveLength(2);
    expect(option.series.every((series: any) => series.type === 'bar')).toBe(true);
    expect(option.series.map((series: any) => series.stack)).toEqual(['total', 'total']);
  });

  it('renders an area chart using line series with areaStyle', () => {
    const option = buildAnalysisChartOption(specFor('area', ['sales']), dataset, result) as any;
    expect(option.series[0].type).toBe('line');
    expect(option.series[0].areaStyle).toEqual({ opacity: 0.18 });
  });

  it('maps two scatter metrics to x/y and preserves color groups', () => {
    const option = buildAnalysisChartOption(specFor('scatter', ['sales', 'profit'], true), dataset, result) as any;
    expect(option.series).toHaveLength(2);
    expect(option.series[0].type).toBe('scatter');
    expect(option.series[0].data[0]).toMatchObject({ name: '华东', value: [100, 20], __rowIndex: 0 });
    expect(option.series[1].data[0]).toMatchObject({ name: '华东', value: [80, 16], __rowIndex: 1 });
  });

  it('maps radar metrics to indicator axes and category rows to polygons', () => {
    const option = buildAnalysisChartOption(specFor('radar', ['sales', 'profit']), dataset, result) as any;
    expect(option.radar.indicator.map((item: any) => item.name)).toEqual(['销售额', '利润']);
    expect(option.series[0].type).toBe('radar');
    expect(option.series[0].data[0]).toMatchObject({ name: '华东', value: [100, 20], __rowIndex: 0 });
  });

  it('renders funnel and treemap from category + metric semantics', () => {
    const funnel = buildAnalysisChartOption(specFor('funnel', ['sales']), dataset, result) as any;
    const treemap = buildAnalysisChartOption(specFor('treemap', ['sales']), dataset, result) as any;
    expect(funnel.series[0].type).toBe('funnel');
    expect(funnel.series[0].data[0]).toMatchObject({ name: '华东', value: 100, __rowIndex: 0 });
    expect(treemap.series[0].type).toBe('treemap');
    expect(treemap.series[0].data[0]).toMatchObject({ name: '华东', value: 100, __rowIndex: 0 });
  });

  it('stacks color groups per metric in stacked bar mode', () => {
    const option = buildAnalysisChartOption(specFor('stackedBar', ['sales'], true), dataset, result) as any;
    expect(option.series.map((series: any) => series.name)).toEqual(['A', 'B']);
    expect(option.series.map((series: any) => series.stack)).toEqual(['sales', 'sales']);
  });
});
