import {
  analysisMetricValues,
  formatAnalysisMetricValue,
  metricComputationFor,
} from './analysis';
import {
  getAnalysisField,
  metricAnalysisDisplayName,
  metricFieldLabel,
} from './display';
import { resolveAnalysisEncoding } from './encoding';
import type {
  AnalysisSpec,
  DatasetQueryResult,
  MetricBinding,
  PublishedDataset,
  Scalar,
} from './model';
import { paletteColors, resolveAnalysisStyle } from './style';

const bindingIndex = (
  result: DatasetQueryResult,
  fieldId: string,
  aggregation?: MetricBinding['aggregation'],
) => result.bindings.findIndex((binding) => (
  binding.fieldId === fieldId
    && (aggregation ? binding.aggregation === aggregation : !binding.aggregation)
));

const cell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation?: MetricBinding['aggregation'],
) => {
  const index = bindingIndex(result, fieldId, aggregation);
  return index >= 0 ? row[index] : null;
};

const rowLabel = (result: DatasetQueryResult, row: Scalar[], dimensions: string[]) =>
  dimensions.map((fieldId) => String(cell(result, row, fieldId) ?? '')).join(' / ');

const scalarKey = (value: Scalar) => `${typeof value}:${String(value)}`;

const uniqueDimensionValues = (
  result: DatasetQueryResult,
  fieldId: string,
) => {
  const seen = new Set<string>();
  return result.rows.flatMap((row) => {
    const value = cell(result, row, fieldId);
    const key = scalarKey(value);
    if (seen.has(key)) return [];
    seen.add(key);
    return [{ key, value, label: String(value ?? '') }];
  });
};

const legendOption = (
  visible: boolean,
  position: 'top' | 'right' | 'bottom',
  axisText: string,
) => {
  if (!visible) return { show: false };
  const base = { textStyle: { color: axisText, fontSize: 11 } };
  if (position === 'right') return { ...base, orient: 'vertical', right: 0, top: 'middle' };
  if (position === 'bottom') return { ...base, orient: 'horizontal', left: 'center', bottom: 0 };
  return { ...base, orient: 'horizontal', right: 4, top: 0 };
};

const chartRuntime = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const encoding = resolveAnalysisEncoding(spec);
  const style = resolveAnalysisStyle(spec.style);
  const colors = paletteColors(spec.style);
  const firstDimension = encoding.category.find((binding) => binding.role === 'dimension')?.field
    ?? spec.dimensions[0];
  const colorDimension = encoding.color.find((binding) => binding.role === 'dimension')?.field;
  const metrics = spec.metrics;
  const computedValues = new Map(metrics.map((metric) => [
    metric.field,
    analysisMetricValues(spec, result, metric),
  ]));
  const metricValue = (metric: MetricBinding, rowIndex: number) =>
    computedValues.get(metric.field)?.[rowIndex] ?? null;
  const formatted = (metric: MetricBinding, value: unknown) => {
    if (value === null || value === undefined) return '—';
    const number = Number(value);
    return formatAnalysisMetricValue(spec, metric, Number.isFinite(number) ? number : null);
  };
  return {
    encoding,
    style,
    colors,
    firstDimension,
    colorDimension,
    metrics,
    computedValues,
    metricValue,
    formatted,
  };
};

const axisText = '#667085';
const axisLine = '#d8dde6';
const splitLine = '#eef1f5';

const cartesianSeriesOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const runtime = chartRuntime(spec, dataset, result);
  const {
    style,
    colors,
    firstDimension,
    colorDimension,
    metrics,
    metricValue,
    formatted,
  } = runtime;
  if (!firstDimension || !metrics.length) return undefined;

  const isLine = spec.type === 'line' || spec.type === 'area';
  const isArea = spec.type === 'area';
  const isStacked = spec.type === 'stackedBar';
  const categories = uniqueDimensionValues(result, firstDimension);
  const colorValues = colorDimension
    ? uniqueDimensionValues(result, colorDimension)
    : [];
  const legendVisible = style.showLegend;
  const labelPosition = style.dataLabelPosition === 'inside' ? 'inside' : 'top';
  const dimension = getAnalysisField(dataset, firstDimension);

  const rowIndexFor = (category: Scalar, color?: Scalar) => result.rows.findIndex((row) => (
    Object.is(cell(result, row, firstDimension), category)
    && (!colorDimension || Object.is(cell(result, row, colorDimension), color))
  ));

  const seriesStyleFor = (metric: MetricBinding, stack?: string) => ({
    type: isLine ? 'line' : 'bar',
    stack,
    smooth: isLine && style.smooth,
    symbolSize: isLine ? style.symbolSize : undefined,
    lineStyle: isLine ? { width: style.lineWidth } : undefined,
    areaStyle: isArea ? { opacity: 0.18 } : undefined,
    barMaxWidth: !isLine ? style.barMaxWidth : undefined,
    itemStyle: !isLine ? { borderRadius: style.barRadius } : undefined,
    label: {
      show: style.showDataLabels,
      position: labelPosition,
      formatter: (params: any) => formatted(metric, params?.value),
    },
    tooltip: {
      valueFormatter: (value: unknown) => formatted(metric, value),
    },
  });

  const series = colorDimension
    ? metrics.flatMap((metric) => colorValues.map((color) => ({
      name: metrics.length > 1
        ? `${color.label} · ${metricAnalysisDisplayName(spec, dataset, metric)}`
        : color.label,
      ...seriesStyleFor(metric, isStacked ? metric.field : undefined),
      data: categories.map((category) => {
        const rowIndex = rowIndexFor(category.value, color.value);
        if (rowIndex < 0) return null;
        return {
          value: metricValue(metric, rowIndex),
          __rowIndex: rowIndex,
        };
      }),
    })))
    : metrics.map((metric) => ({
      name: metricAnalysisDisplayName(spec, dataset, metric),
      ...seriesStyleFor(metric, isStacked ? 'total' : undefined),
      data: categories.map((category) => {
        const rowIndex = rowIndexFor(category.value);
        if (rowIndex < 0) return null;
        return {
          value: metricValue(metric, rowIndex),
          __rowIndex: rowIndex,
        };
      }),
    }));

  return {
    color: colors,
    grid: {
      left: 22,
      right: legendVisible && style.legendPosition === 'right' ? 112 : 16,
      top: legendVisible && style.legendPosition === 'top' ? 34 : 14,
      bottom: legendVisible && style.legendPosition === 'bottom' ? 40 : 22,
      containLabel: true,
    },
    tooltip: { trigger: 'axis' },
    legend: legendOption(legendVisible, style.legendPosition, axisText),
    xAxis: {
      type: 'category',
      boundaryGap: !isLine,
      name: dimension?.label,
      nameTextStyle: { color: '#98a2b3', fontSize: 10 },
      data: categories.map((item) => item.label),
      axisLine: { lineStyle: { color: axisLine } },
      axisTick: { show: false },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        rotate: style.axisLabelRotation,
      },
    },
    yAxis: {
      type: 'value',
      splitLine: { show: style.showGrid, lineStyle: { color: splitLine } },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => formatAnalysisMetricValue(spec, metrics[0], value),
      },
    },
    series,
  };
};

const pieOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const runtime = chartRuntime(spec, dataset, result);
  const { style, colors, firstDimension, metrics, metricValue, formatted } = runtime;
  if (!firstDimension || !metrics.length) return undefined;
  const metric = metrics[0];
  const metricConfig = metricComputationFor(spec, metric.field);
  const legacyPieLabel = metricConfig.quickCalculation === 'none'
    && metricConfig.numberFormat === 'auto';
  const legendVisible = style.showLegend;
  const legendOnRight = legendVisible && style.legendPosition === 'right';
  const legendOnTop = legendVisible && style.legendPosition === 'top';
  const legendOnBottom = legendVisible && style.legendPosition === 'bottom';
  const labelPosition = style.dataLabelPosition === 'inside' ? 'inside' : 'outside';

  return {
    color: colors,
    tooltip: {
      trigger: 'item',
      valueFormatter: (value: unknown) => formatted(metric, value),
    },
    legend: legendOption(legendVisible, style.legendPosition, axisText),
    series: [{
      name: metricAnalysisDisplayName(spec, dataset, metric),
      type: 'pie',
      radius: [`${style.pieInnerRadius}%`, '70%'],
      center: [
        legendOnRight ? '38%' : '50%',
        legendOnTop ? '56%' : legendOnBottom ? '45%' : '52%',
      ],
      label: {
        show: style.showDataLabels,
        position: labelPosition,
        formatter: legacyPieLabel
          ? (labelPosition === 'inside' ? '{d}%' : '{b} {d}%')
          : (params: any) => labelPosition === 'inside'
            ? formatted(metric, params?.value)
            : `${params?.name ?? ''} ${formatted(metric, params?.value)}`.trim(),
      },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: result.rows.map((row, rowIndex) => ({
        name: rowLabel(result, row, [firstDimension]),
        value: metricValue(metric, rowIndex),
        __rowIndex: rowIndex,
      })),
    }],
  };
};

const scatterOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const runtime = chartRuntime(spec, dataset, result);
  const {
    style,
    colors,
    firstDimension,
    colorDimension,
    metrics,
    metricValue,
  } = runtime;
  if (!firstDimension || metrics.length < 2) return undefined;
  const [xMetric, yMetric] = metrics;
  const colorValues = colorDimension
    ? uniqueDimensionValues(result, colorDimension)
    : [{ key: 'all', value: undefined as Scalar | undefined, label: metricFieldLabel(spec, dataset, yMetric) }];
  const pointSize = Math.max(6, style.symbolSize);

  const series = colorValues.map((color) => ({
    name: color.label,
    type: 'scatter',
    symbolSize: pointSize,
    label: {
      show: style.showDataLabels,
      position: style.dataLabelPosition === 'inside' ? 'inside' : 'top',
      formatter: (params: any) => params?.name ?? '',
    },
    data: result.rows.flatMap((row, rowIndex) => {
      if (
        colorDimension
        && !Object.is(cell(result, row, colorDimension), color.value)
      ) return [];
      const x = metricValue(xMetric, rowIndex);
      const y = metricValue(yMetric, rowIndex);
      if (x === null || y === null) return [];
      return [{
        name: rowLabel(result, row, [firstDimension]),
        value: [x, y],
        __rowIndex: rowIndex,
      }];
    }),
  }));

  return {
    color: colors,
    grid: {
      left: 24,
      right: colorDimension && style.showLegend && style.legendPosition === 'right' ? 112 : 20,
      top: colorDimension && style.showLegend && style.legendPosition === 'top' ? 34 : 18,
      bottom: colorDimension && style.showLegend && style.legendPosition === 'bottom' ? 46 : 34,
      containLabel: true,
    },
    tooltip: { trigger: 'item' },
    legend: legendOption(Boolean(colorDimension) && style.showLegend, style.legendPosition, axisText),
    xAxis: {
      type: 'value',
      name: metricFieldLabel(spec, dataset, xMetric),
      nameLocation: 'middle',
      nameGap: 28,
      nameTextStyle: { color: '#98a2b3', fontSize: 10 },
      axisLine: { lineStyle: { color: axisLine } },
      splitLine: { show: style.showGrid, lineStyle: { color: splitLine } },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => formatAnalysisMetricValue(spec, xMetric, value),
      },
    },
    yAxis: {
      type: 'value',
      name: metricFieldLabel(spec, dataset, yMetric),
      nameTextStyle: { color: '#98a2b3', fontSize: 10 },
      axisLine: { lineStyle: { color: axisLine } },
      splitLine: { show: style.showGrid, lineStyle: { color: splitLine } },
      axisLabel: {
        color: axisText,
        fontSize: 11,
        formatter: (value: number) => formatAnalysisMetricValue(spec, yMetric, value),
      },
    },
    series,
  };
};

const radarOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const runtime = chartRuntime(spec, dataset, result);
  const { style, colors, firstDimension, metrics, computedValues } = runtime;
  if (!firstDimension || metrics.length < 2) return undefined;

  const indicator = metrics.map((metric) => {
    const values = (computedValues.get(metric.field) ?? [])
      .filter((value): value is number => value !== null && Number.isFinite(value));
    const minValue = values.length ? Math.min(...values) : 0;
    const maxValue = values.length ? Math.max(...values) : 0;
    const low = Math.min(0, minValue);
    const high = Math.max(0, maxValue);
    const span = Math.max(1, high - low, Math.abs(high), Math.abs(low));
    return {
      name: metricFieldLabel(spec, dataset, metric),
      min: low < 0 ? low - span * 0.1 : 0,
      max: high + span * 0.1,
    };
  });

  const data = result.rows.flatMap((row, rowIndex) => {
    const values = metrics.map((metric) => computedValues.get(metric.field)?.[rowIndex] ?? null);
    if (values.some((value) => value === null)) return [];
    return [{
      name: rowLabel(result, row, [firstDimension]),
      value: values,
      __rowIndex: rowIndex,
    }];
  });

  return {
    color: colors,
    tooltip: { trigger: 'item' },
    legend: legendOption(style.showLegend, style.legendPosition, axisText),
    radar: {
      indicator,
      radius: '62%',
      center: [style.showLegend && style.legendPosition === 'right' ? '42%' : '50%', '53%'],
      splitNumber: 4,
      axisName: { color: axisText, fontSize: 10 },
      axisLine: { lineStyle: { color: axisLine } },
      splitLine: { lineStyle: { color: splitLine } },
      splitArea: { show: false },
    },
    series: [{
      type: 'radar',
      symbolSize: Math.max(4, style.symbolSize),
      lineStyle: { width: style.lineWidth },
      areaStyle: { opacity: 0.08 },
      data,
    }],
  };
};

const funnelOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const runtime = chartRuntime(spec, dataset, result);
  const { style, colors, firstDimension, metrics, metricValue, formatted } = runtime;
  if (!firstDimension || !metrics.length) return undefined;
  const metric = metrics[0];
  const labelPosition = style.dataLabelPosition === 'inside' ? 'inside' : 'outside';

  return {
    color: colors,
    tooltip: {
      trigger: 'item',
      valueFormatter: (value: unknown) => formatted(metric, value),
    },
    legend: legendOption(style.showLegend, style.legendPosition, axisText),
    series: [{
      name: metricAnalysisDisplayName(spec, dataset, metric),
      type: 'funnel',
      left: '10%',
      top: style.showLegend && style.legendPosition === 'top' ? 38 : 18,
      bottom: style.showLegend && style.legendPosition === 'bottom' ? 42 : 18,
      width: style.showLegend && style.legendPosition === 'right' ? '68%' : '80%',
      minSize: '10%',
      maxSize: '100%',
      sort: 'descending',
      gap: 2,
      label: {
        show: style.showDataLabels,
        position: labelPosition,
        formatter: (params: any) => `${params?.name ?? ''} ${formatted(metric, params?.value)}`.trim(),
      },
      itemStyle: { borderColor: '#fff', borderWidth: 1 },
      data: result.rows.flatMap((row, rowIndex) => {
        const value = metricValue(metric, rowIndex);
        if (value === null) return [];
        return [{
          name: rowLabel(result, row, [firstDimension]),
          value,
          __rowIndex: rowIndex,
        }];
      }),
    }],
  };
};

const treemapOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const runtime = chartRuntime(spec, dataset, result);
  const { style, colors, firstDimension, metrics, metricValue, formatted } = runtime;
  if (!firstDimension || !metrics.length) return undefined;
  const metric = metrics[0];

  return {
    color: colors,
    tooltip: {
      trigger: 'item',
      valueFormatter: (value: unknown) => formatted(metric, value),
    },
    series: [{
      name: metricAnalysisDisplayName(spec, dataset, metric),
      type: 'treemap',
      roam: false,
      nodeClick: false,
      breadcrumb: { show: false },
      label: {
        show: true,
        color: '#fff',
        fontSize: 11,
        lineHeight: 16,
        formatter: (params: any) => style.showDataLabels
          ? `${params?.name ?? ''}\n${formatted(metric, params?.value)}`
          : params?.name ?? '',
      },
      itemStyle: {
        borderColor: '#fff',
        borderWidth: 2,
        gapWidth: 2,
      },
      data: result.rows.flatMap((row, rowIndex) => {
        const value = metricValue(metric, rowIndex);
        if (value === null) return [];
        return [{
          name: rowLabel(result, row, [firstDimension]),
          value,
          __rowIndex: rowIndex,
        }];
      }),
    }],
  };
};

/** Build the ECharts option for every non-table/non-metric Analysis chart type. */
export const buildAnalysisChartOption = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  if (
    spec.type === 'bar'
    || spec.type === 'stackedBar'
    || spec.type === 'line'
    || spec.type === 'area'
  ) return cartesianSeriesOption(spec, dataset, result);
  if (spec.type === 'pie') return pieOption(spec, dataset, result);
  if (spec.type === 'scatter') return scatterOption(spec, dataset, result);
  if (spec.type === 'radar') return radarOption(spec, dataset, result);
  if (spec.type === 'funnel') return funnelOption(spec, dataset, result);
  if (spec.type === 'treemap') return treemapOption(spec, dataset, result);
  return undefined;
};
