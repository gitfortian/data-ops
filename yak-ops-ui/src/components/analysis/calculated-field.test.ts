import {
  calculatedFieldKey,
  calculatedFieldMetric,
  materializeCalculatedFields,
  parseCalculatedFieldExpression,
  queryMetricsForAnalysis,
} from './calculated-field';
import { rebindAnalysisEncoding } from './encoding';
import type {
  AnalysisCalculatedField,
  AnalysisSpec,
  DatasetQueryResult,
  PublishedDataset,
} from './model';

const dataset: PublishedDataset = {
  id: 'dataset-1',
  name: '订单数据',
  description: '',
  status: 'ONLINE',
  sourceTaskId: 'task-1',
  sourceTaskName: '订单同步',
  currentVersionNo: 1,
  updatedAt: '2026-08-18T00:00:00Z',
  fields: [
    { key: 'category', label: '分类', physicalName: 'category', dataType: 'string', role: 'dimension', nullable: false },
    { key: 'sales', label: '销售额', physicalName: 'sales', dataType: 'number', role: 'metric', nullable: false },
    { key: 'profit', label: '利润', physicalName: 'profit', dataType: 'number', role: 'metric', nullable: true },
    { key: 'order_id', label: '订单ID', physicalName: 'order_id', dataType: 'string', role: 'dimension', nullable: false },
  ],
};

const calculated = (expression: string, id = 'average-order-value'): AnalysisCalculatedField => ({
  id,
  name: '客单价',
  expression,
  ast: parseCalculatedFieldExpression(expression, dataset).ast,
});

const specWith = (field: AnalysisCalculatedField): AnalysisSpec => {
  const metric = calculatedFieldMetric(field);
  return {
    type: 'bar',
    datasetId: dataset.id,
    dimensions: ['category'],
    metrics: [metric],
    encoding: {
      version: 1,
      category: [{ field: 'category', role: 'dimension' }],
      value: [{ field: metric.field, role: 'metric', aggregation: metric.aggregation }],
      color: [],
      size: [],
      label: [],
      detail: [],
      tooltip: [],
    },
    filters: [],
    style: {
      showLegend: false,
      showDataLabels: false,
      smooth: false,
      showGrid: true,
    },
    analysis: { version: 1, calculatedFields: [field] },
  };
};

const result: DatasetQueryResult = {
  datasetId: dataset.id,
  datasetVersionId: 'version-1',
  datasetVersionNo: 1,
  bindings: [
    { key: 'd0', fieldId: 'category', displayName: '分类', dataType: 'STRING', aggregation: null },
    { key: 'm1', fieldId: 'sales', displayName: '销售额', dataType: 'NUMBER', aggregation: 'SUM' },
    { key: 'm2', fieldId: 'order_id', displayName: '订单ID', dataType: 'NUMBER', aggregation: 'COUNT_DISTINCT' },
  ],
  columns: [],
  rows: [
    ['华东', 100, 4],
    ['华南', 90, 3],
  ],
  returnedRows: 2,
  truncated: false,
  elapsedMillis: 1,
};

describe('parseCalculatedFieldExpression', () => {
  it('parses aggregate arithmetic with precedence and dependencies', () => {
    const parsed = parseCalculatedFieldExpression(
      'SUM([sales]) / COUNT_DISTINCT([order_id]) + SUM([sales]) * 0.1',
      dataset,
    );
    expect(parsed.dependencies).toEqual([
      { field: 'sales', aggregation: 'SUM' },
      { field: 'order_id', aggregation: 'COUNT_DISTINCT' },
    ]);
    expect(parsed.ast.kind).toBe('binary');
  });

  it('supports scalar functions without eval', () => {
    const parsed = parseCalculatedFieldExpression(
      'ROUND(ABS(SUM([profit])) / COALESCE(COUNT([order_id]), 1), 2)',
      dataset,
    );
    expect(parsed.ast.kind).toBe('function');
    expect(parsed.dependencies).toEqual([
      { field: 'profit', aggregation: 'SUM' },
      { field: 'order_id', aggregation: 'COUNT' },
    ]);
  });

  it('rejects unknown fields and invalid numeric aggregations', () => {
    expect(() => parseCalculatedFieldExpression('SUM([missing])', dataset)).toThrow('不存在字段');
    expect(() => parseCalculatedFieldExpression('SUM([order_id])', dataset)).toThrow('仅支持数值字段');
  });
});

describe('calculated metric query expansion', () => {
  it('queries physical aggregate dependencies instead of virtual field ids', () => {
    const field = calculated('SUM([sales]) / COUNT_DISTINCT([order_id])');
    const spec = specWith(field);
    expect(queryMetricsForAnalysis(spec)).toEqual([
      { fieldId: 'sales', aggregation: 'SUM' },
      { fieldId: 'order_id', aggregation: 'COUNT_DISTINCT' },
    ]);
    expect(queryMetricsForAnalysis(spec).some((metric) => metric.fieldId === calculatedFieldKey(field))).toBe(false);
  });

  it('deduplicates dependencies shared with physical metrics', () => {
    const field = calculated('SUM([sales]) / COUNT_DISTINCT([order_id])');
    const spec = specWith(field);
    spec.metrics = [{ field: 'sales', aggregation: 'SUM' }, ...spec.metrics];
    expect(queryMetricsForAnalysis(spec)).toHaveLength(2);
  });
});

describe('materializeCalculatedFields', () => {
  it('appends virtual metric values to the normal Dataset result', () => {
    const field = calculated('SUM([sales]) / COUNT_DISTINCT([order_id])');
    const spec = specWith(field);
    const materialized = materializeCalculatedFields(spec, result);
    const binding = materialized.bindings.at(-1);
    expect(binding?.fieldId).toBe(calculatedFieldKey(field));
    expect(materialized.rows.map((row) => row.at(-1))).toEqual([25, 30]);
  });

  it('returns null for division by zero rather than Infinity', () => {
    const field = calculated('SUM([sales]) / (COUNT_DISTINCT([order_id]) - COUNT_DISTINCT([order_id]))');
    const spec = specWith(field);
    const materialized = materializeCalculatedFields(spec, result);
    expect(materialized.rows.map((row) => row.at(-1))).toEqual([null, null]);
  });
});

describe('encoding reconciliation', () => {
  it('preserves chart-local calculated metric bindings on Dataset refresh', () => {
    const field = calculated('SUM([sales]) / COUNT_DISTINCT([order_id])');
    const spec = specWith(field);
    const rebound = rebindAnalysisEncoding(spec, dataset);
    expect(rebound.metrics).toEqual([calculatedFieldMetric(field)]);
    expect(rebound.encoding?.value[0]?.field).toBe(calculatedFieldKey(field));
  });
});
