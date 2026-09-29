import { isCalculatedFieldKey } from './calculated-field';
import { resolveAnalysisEncoding } from './encoding';
import type {
  AnalysisMetricComputation,
  AnalysisNumberFormat,
  AnalysisQuickCalculation,
  AnalysisSpec,
  DatasetQueryResult,
  MetricBinding,
  Scalar,
} from './model';

export const QUICK_CALCULATION_OPTIONS: Array<{
  label: string;
  value: AnalysisQuickCalculation;
  description: string;
}> = [
  { label: '原始值', value: 'none', description: '直接展示聚合后的指标值' },
  { label: '占比', value: 'percent_of_total', description: '当前结果范围内的指标占比' },
  { label: '累计值', value: 'running_total', description: '按当前结果顺序累计指标值' },
  { label: '排名', value: 'rank', description: '按指标值从高到低排名' },
  { label: '较上期变化', value: 'previous_change', description: '与上一分类值相比的变化率' },
];

export const NUMBER_FORMAT_OPTIONS: Array<{ label: string; value: AnalysisNumberFormat }> = [
  { label: '自动', value: 'auto' },
  { label: '数值', value: 'number' },
  { label: '百分比', value: 'percent' },
];

const DEFAULT_METRIC_COMPUTATION: Required<AnalysisMetricComputation> = {
  quickCalculation: 'none',
  numberFormat: 'auto',
  decimalPlaces: 2,
  useGrouping: true,
};

const bindingIndex = (
  result: DatasetQueryResult,
  fieldId: string,
  aggregation?: MetricBinding['aggregation'],
) => result.bindings.findIndex((binding) => (
  binding.fieldId === fieldId
    && (aggregation ? binding.aggregation === aggregation : !binding.aggregation)
));

const rawMetricValue = (
  result: DatasetQueryResult,
  rowIndex: number,
  metric: MetricBinding,
) => {
  const index = bindingIndex(result, metric.field, metric.aggregation);
  if (index < 0) return 0;
  const value = Number(result.rows[rowIndex]?.[index] ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const dimensionValue = (
  result: DatasetQueryResult,
  rowIndex: number,
  fieldId?: string,
): Scalar => {
  if (!fieldId) return null;
  const index = bindingIndex(result, fieldId);
  return index >= 0 ? result.rows[rowIndex]?.[index] ?? null : null;
};

const scalarKey = (value: Scalar) => `${typeof value}:${String(value)}`;

export const metricComputationFor = (
  spec: Pick<AnalysisSpec, 'analysis'>,
  field: string,
): Required<AnalysisMetricComputation> => ({
  ...DEFAULT_METRIC_COMPUTATION,
  ...(spec.analysis?.metrics?.[field] ?? {}),
});

export const patchMetricComputation = (
  spec: AnalysisSpec,
  field: string,
  patch: Partial<AnalysisMetricComputation>,
) => ({
  version: 1 as const,
  ...spec.analysis,
  metrics: {
    ...(spec.analysis?.metrics ?? {}),
    [field]: {
      ...(spec.analysis?.metrics?.[field] ?? {}),
      ...patch,
    },
  },
});

/**
 * Resolve an enabled Top/Bottom N definition against the active query projection. Grouped
 * color series are deliberately excluded because limiting grouped rows could truncate a
 * category before all of its series values are returned. Calculated metrics stay client-side,
 * so they cannot be used for a server-side Top N in Phase 9.
 */
export const resolveAnalysisTopN = (spec: AnalysisSpec) => {
  const topN = spec.analysis?.topN;
  if (!topN?.enabled || spec.type === 'metric' || !spec.dimensions.length) return undefined;
  const colorField = resolveAnalysisEncoding(spec).color.find((item) => item.role === 'dimension')?.field;
  if (colorField) return undefined;
  const metric = spec.metrics.find((item) => item.field === topN.metricField);
  if (!metric || isCalculatedFieldKey(spec, metric.field)) return undefined;
  const count = Math.min(100, Math.max(1, Math.round(Number(topN.count) || 10)));
  return {
    metric,
    count,
    direction: topN.direction === 'bottom' ? 'bottom' as const : 'top' as const,
  };
};

/**
 * Quick calculations intentionally run after the Dataset query. They are therefore
 * portable across Dataset SQL dialects and leave the existing server query contract intact.
 * When a color channel exists, sequential calculations are scoped to each color series.
 */
export const analysisMetricValues = (
  spec: AnalysisSpec,
  result: DatasetQueryResult,
  metric: MetricBinding,
): Array<number | null> => {
  const config = metricComputationFor(spec, metric.field);
  const raw = result.rows.map((_, rowIndex) => rawMetricValue(result, rowIndex, metric));
  // A metric card has no category sequence. Keep any calculation selected on another chart
  // type parked in the persisted analysis config, but render the single aggregated value.
  if (spec.type === 'metric' || config.quickCalculation === 'none') return raw;

  const colorField = resolveAnalysisEncoding(spec).color.find((item) => item.role === 'dimension')?.field;
  const groupKeys = result.rows.map((_, rowIndex) => scalarKey(dimensionValue(result, rowIndex, colorField)));
  const indexesByGroup = new Map<string, number[]>();
  groupKeys.forEach((key, index) => {
    const indexes = indexesByGroup.get(key) ?? [];
    indexes.push(index);
    indexesByGroup.set(key, indexes);
  });

  const values: Array<number | null> = Array.from({ length: raw.length }, () => null);
  indexesByGroup.forEach((indexes) => {
    if (config.quickCalculation === 'percent_of_total') {
      const total = indexes.reduce((sum, index) => sum + raw[index], 0);
      indexes.forEach((index) => {
        values[index] = total === 0 ? 0 : raw[index] / total;
      });
      return;
    }

    if (config.quickCalculation === 'running_total') {
      let total = 0;
      indexes.forEach((index) => {
        total += raw[index];
        values[index] = total;
      });
      return;
    }

    if (config.quickCalculation === 'rank') {
      const ordered = [...indexes].sort((left, right) => raw[right] - raw[left]);
      let rank = 0;
      let previous: number | undefined;
      ordered.forEach((index, orderedIndex) => {
        if (previous === undefined || raw[index] !== previous) rank = orderedIndex + 1;
        values[index] = rank;
        previous = raw[index];
      });
      return;
    }

    if (config.quickCalculation === 'previous_change') {
      indexes.forEach((index, offset) => {
        if (offset === 0) {
          values[index] = null;
          return;
        }
        const previous = raw[indexes[offset - 1]];
        values[index] = previous === 0 ? null : (raw[index] - previous) / Math.abs(previous);
      });
    }
  });
  return values;
};

const effectiveNumberFormat = (
  type: AnalysisSpec['type'],
  config: Required<AnalysisMetricComputation>,
): Exclude<AnalysisNumberFormat, 'auto'> => {
  if (config.numberFormat !== 'auto') return config.numberFormat;
  if (type === 'metric') return 'number';
  return config.quickCalculation === 'percent_of_total' || config.quickCalculation === 'previous_change'
    ? 'percent'
    : 'number';
};

export const formatAnalysisMetricValue = (
  spec: Pick<AnalysisSpec, 'analysis' | 'type'>,
  metric: MetricBinding,
  value: number | null | undefined,
) => {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  const stored = spec.analysis?.metrics?.[metric.field];
  const config = metricComputationFor(spec, metric.field);
  const format = effectiveNumberFormat(spec.type, config);
  const explicitDecimals = stored?.decimalPlaces;
  const options: Intl.NumberFormatOptions = {
    useGrouping: config.useGrouping,
  };

  if (explicitDecimals !== undefined) {
    const decimalPlaces = Math.min(4, Math.max(0, Number(explicitDecimals)));
    options.minimumFractionDigits = decimalPlaces;
    options.maximumFractionDigits = decimalPlaces;
  } else {
    // Preserve the pre-Phase-8 display contract for legacy/raw metrics: do not force
    // trailing zeroes, but still cap noisy floating-point output at two decimals.
    options.maximumFractionDigits = 2;
  }

  if (format === 'percent') options.style = 'percent';
  return new Intl.NumberFormat('zh-CN', options).format(value);
};

export const quickCalculationLabel = (
  calculation: AnalysisQuickCalculation,
) => QUICK_CALCULATION_OPTIONS.find((item) => item.value === calculation)?.label ?? '原始值';
