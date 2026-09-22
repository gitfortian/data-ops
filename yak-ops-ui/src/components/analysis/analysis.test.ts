import {
  analysisMetricValues,
  formatAnalysisMetricValue,
  resolveAnalysisTopN,
} from './analysis';
import type {
  AnalysisQuickCalculation,
  AnalysisSpec,
  DatasetQueryResult,
  MetricBinding,
} from './model';

const metric: MetricBinding = { field: 'sales', aggregation: 'SUM' };

const baseSpec = (calculation: AnalysisSpec['analysis'] = undefined): AnalysisSpec => ({
  type: 'bar',
  datasetId: 'dataset-1',
  dimensions: ['category'],
  metrics: [metric],
  filters: [],
  style: {
    showLegend: false,
    showDataLabels: false,
    smooth: false,
    showGrid: true,
  },
  analysis: calculation,
});

const result = (values: number[]): DatasetQueryResult => ({
  datasetId: 'dataset-1',
  datasetVersionId: 'version-1',
  datasetVersionNo: 1,
  bindings: [
    {
      key: 'd0',
      fieldId: 'category',
      displayName: '分类',
      dataType: 'STRING',
      aggregation: null,
    },
    {
      key: 'm1',
      fieldId: 'sales',
      displayName: '销售额',
      dataType: 'NUMBER',
      aggregation: 'SUM',
    },
  ],
  columns: [],
  rows: values.map((value, index) => [`C${index + 1}`, value]),
  returnedRows: values.length,
  truncated: false,
  elapsedMillis: 1,
});

const withCalculation = (quickCalculation: AnalysisQuickCalculation): AnalysisSpec => baseSpec({
  version: 1,
  metrics: {
    sales: { quickCalculation },
  },
});

describe('analysisMetricValues', () => {
  it('calculates percent of total inside the current result', () => {
    expect(analysisMetricValues(withCalculation('percent_of_total'), result([10, 30]), metric))
      .toEqual([0.25, 0.75]);
  });

  it('calculates a running total in current result order', () => {
    expect(analysisMetricValues(withCalculation('running_total'), result([10, 30, 5]), metric))
      .toEqual([10, 40, 45]);
  });

  it('uses competition ranking for tied values', () => {
    expect(analysisMetricValues(withCalculation('rank'), result([10, 30, 30]), metric))
      .toEqual([3, 1, 1]);
  });

  it('calculates previous change and leaves the first row empty', () => {
    expect(analysisMetricValues(withCalculation('previous_change'), result([10, 30, 30]), metric))
      .toEqual([null, 2, 0]);
  });

  it('keeps sequential calculations dormant on metric cards', () => {
    const spec = { ...withCalculation('percent_of_total'), type: 'metric' as const, dimensions: [] };
    expect(analysisMetricValues(spec, result([42]), metric)).toEqual([42]);
  });
});

describe('resolveAnalysisTopN', () => {
  it('resolves a server-side metric sort and clamps count', () => {
    const spec = baseSpec({
      version: 1,
      topN: { enabled: true, metricField: 'sales', count: 999, direction: 'bottom' },
    });
    expect(resolveAnalysisTopN(spec)).toEqual({
      metric,
      count: 100,
      direction: 'bottom',
    });
  });

  it('suspends Top N while a color grouping is active', () => {
    const spec: AnalysisSpec = {
      ...baseSpec({
        version: 1,
        topN: { enabled: true, metricField: 'sales', count: 10, direction: 'top' },
      }),
      dimensions: ['category', 'region'],
      encoding: {
        version: 1,
        category: [{ field: 'category', role: 'dimension' }],
        value: [{ field: 'sales', role: 'metric', aggregation: 'SUM' }],
        color: [{ field: 'region', role: 'dimension' }],
        size: [],
        label: [],
        detail: [],
        tooltip: [],
      },
    };
    expect(resolveAnalysisTopN(spec)).toBeUndefined();
  });
});

describe('formatAnalysisMetricValue', () => {
  it('preserves legacy automatic decimals without trailing zeroes', () => {
    expect(formatAnalysisMetricValue(baseSpec(), metric, 12.3)).toBe('12.3');
  });

  it('uses an explicitly configured decimal precision', () => {
    const spec = baseSpec({
      version: 1,
      metrics: { sales: { decimalPlaces: 2 } },
    });
    expect(formatAnalysisMetricValue(spec, metric, 12.3)).toBe('12.30');
  });

  it('automatically formats percent calculations as percentages', () => {
    expect(formatAnalysisMetricValue(withCalculation('percent_of_total'), metric, 0.125)).toBe('12.5%');
  });
});
