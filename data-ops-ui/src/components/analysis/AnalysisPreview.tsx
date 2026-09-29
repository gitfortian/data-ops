import YakButton from '@/components/YakButton';
import { Empty, Spin } from 'antd';
import * as echarts from 'echarts';
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { AnalysisErrorBoundary } from './analysis-error-boundary';
import {
  analysisMetricValues,
  formatAnalysisMetricValue,
  resolveAnalysisTopN,
} from './analysis';
import {
  applyAnalysisChartTheme,
  resolveAnalysisThemeTokens,
  type AnalysisThemeTokens,
} from './analysis-theme';
import {
  isCalculatedFieldKey,
  materializeCalculatedFields,
  queryMetricsForAnalysis,
} from './calculated-field';
import { buildAnalysisChartOption } from './chart-options';
import {
  getAnalysisField,
  metricAnalysisDisplayName,
} from './display';
import { encodingMeetsChartRequirements, resolveAnalysisEncoding } from './encoding';
import type {
  Aggregation,
  AnalysisFilter,
  AnalysisSelection,
  AnalysisSpec,
  DatasetField,
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
  Scalar,
} from './model';
import { queryAnalysisDatasetShared } from './query-runtime';
import { resolveAnalysisStyle } from './style';

export {
  AGGREGATION_LABELS,
  getAnalysisField,
  metricDisplayName,
} from './display';

const filterValue = (field: DatasetField | undefined, value: string): Scalar => {
  if (field?.dataType === 'number') {
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  if (field?.dataType === 'boolean') {
    if (value.toLowerCase() === 'true') return true;
    if (value.toLowerCase() === 'false') return false;
  }
  return value;
};

export const buildDatasetQueryPayload = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  runtimeFilters: AnalysisFilter[] = [],
): DatasetQueryPayload => {
  const operators: Record<
    AnalysisFilter['operator'],
    DatasetQueryPayload['filters'][number]['operator']
  > = {
    eq: 'EQ',
    neq: 'NE',
    contains: 'LIKE',
    gt: 'GT',
    gte: 'GTE',
    lt: 'LT',
    lte: 'LTE',
  };
  const filters = [...spec.filters, ...runtimeFilters]
    .filter((filter) => filter.field && filter.value !== '')
    .map((filter) => {
      const field = getAnalysisField(dataset, filter.field);
      const value = filter.operator === 'contains'
        ? `%${filter.value}%`
        : filterValue(field, filter.value);
      return { fieldId: filter.field, operator: operators[filter.operator], value };
    });

  const sorts: DatasetQueryPayload['sorts'] = [];
  const topN = resolveAnalysisTopN(spec);
  if (topN) {
    sorts.push({
      fieldId: topN.metric.field,
      aggregation: topN.metric.aggregation,
      direction: topN.direction === 'top' ? 'DESC' : 'ASC',
    });
  }

  const sort = spec.sort;
  if (sort?.field) {
    const metric = spec.metrics.find((item) => item.field === sort.field);
    const physicalMetric = metric && !isCalculatedFieldKey(spec, metric.field) ? metric : undefined;
    const valid = Boolean(physicalMetric || spec.dimensions.includes(sort.field));
    const duplicatedByTopN = Boolean(topN && physicalMetric?.field === topN.metric.field);
    if (valid && !duplicatedByTopN) {
      sorts.push({
        fieldId: sort.field,
        aggregation: physicalMetric?.aggregation,
        direction: sort.direction === 'desc' ? 'DESC' : 'ASC',
      });
    }
  }

  const baseLimit = spec.limit ?? (spec.type === 'table' ? 200 : 500);
  return {
    dimensions: spec.type === 'metric' ? [] : spec.dimensions,
    metrics: queryMetricsForAnalysis(spec),
    filters,
    sorts,
    limit: topN ? Math.min(baseLimit, topN.count) : baseLimit,
    timeoutSeconds: spec.timeoutSeconds ?? 30,
  };
};

export const canQueryAnalysis = (spec: AnalysisSpec) => {
  if (!encodingMeetsChartRequirements(spec)) return false;
  if (spec.type === 'table') return spec.dimensions.length > 0 || spec.metrics.length > 0;
  return true;
};

function useAnalysisQuery(
  spec: AnalysisSpec,
  dataset?: PublishedDataset,
  runtimeFilters: AnalysisFilter[] = [],
) {
  const [rawResult, setRawResult] = useState<DatasetQueryResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [retryVersion, setRetryVersion] = useState(0);
  const sequence = useRef(0);
  const runtimeFilterKey = useMemo(() => JSON.stringify(runtimeFilters), [runtimeFilters]);
  const payload = useMemo(
    () => dataset ? buildDatasetQueryPayload(spec, dataset, runtimeFilters) : undefined,
    [dataset, spec, runtimeFilterKey],
  );
  const payloadKey = useMemo(() => JSON.stringify(payload), [payload]);
  const result = useMemo(
    () => rawResult ? materializeCalculatedFields(spec, rawResult) : undefined,
    [rawResult, spec],
  );

  useEffect(() => {
    if (!dataset || !payload || !canQueryAnalysis(spec)) {
      setRawResult(undefined);
      setError('');
      setLoading(false);
      return undefined;
    }
    const requestId = ++sequence.current;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError('');
      try {
        const value = await queryAnalysisDatasetShared(dataset, payload);
        if (requestId === sequence.current) setRawResult(value);
      } catch (queryError) {
        if (requestId === sequence.current) {
          setRawResult(undefined);
          setError(queryError instanceof Error ? queryError.message : 'Dataset 查询失败');
        }
      } finally {
        if (requestId === sequence.current) setLoading(false);
      }
    }, 180);
    return () => {
      window.clearTimeout(timer);
      if (sequence.current === requestId) sequence.current += 1;
    };
  }, [dataset?.id, dataset?.currentVersionNo, payloadKey, retryVersion]);

  return {
    result,
    loading,
    error,
    retry: () => setRetryVersion((value) => value + 1),
  };
}

const bindingIndex = (
  result: DatasetQueryResult,
  fieldId: string,
  aggregation?: Aggregation,
) => result.bindings.findIndex((binding) => (
  binding.fieldId === fieldId
    && (aggregation ? binding.aggregation === aggregation : !binding.aggregation)
));

const cell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation?: Aggregation,
) => {
  const index = bindingIndex(result, fieldId, aggregation);
  return index >= 0 ? row[index] : null;
};

const selectionForRow = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
  rowIndex: number,
): AnalysisSelection | undefined => {
  const encoding = resolveAnalysisEncoding(spec);
  const fieldId = encoding.category.find((binding) => binding.role === 'dimension')?.field
    ?? spec.dimensions[0];
  const row = result.rows[rowIndex];
  if (!fieldId || !row) return undefined;
  const value = cell(result, row, fieldId);
  if (value === null) return undefined;
  return {
    fieldId,
    value,
    label: `${getAnalysisField(dataset, fieldId)?.label ?? fieldId}: ${String(value)}`,
    rowIndex,
  };
};

function EChartAnalysis({
  spec,
  dataset,
  result,
  theme,
  onSelect,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
  theme?: AnalysisThemeTokens;
  onSelect?: (selection: AnalysisSelection) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const option = useMemo(
    () => applyAnalysisChartTheme(buildAnalysisChartOption(spec, dataset, result), theme),
    [spec, dataset, result, theme],
  );

  useEffect(() => {
    if (!containerRef.current || !option) return undefined;
    const chart = echarts.init(containerRef.current);
    chart.setOption(option, true);
    const click = (params: any) => {
      const rowIndex = Number(params?.data?.__rowIndex ?? params?.dataIndex);
      const selection = selectionForRow(spec, dataset, result, rowIndex);
      if (selection) onSelect?.(selection);
    };
    chart.on('click', click);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.off('click', click);
      chart.dispose();
    };
  }, [option, onSelect, spec, dataset, result]);

  if (!option) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置必填编码" className="mt-8" />;
  }
  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}

function MetricAnalysis({
  spec,
  dataset,
  result,
  theme,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
  theme?: AnalysisThemeTokens;
}) {
  const metric = spec.metrics[0];
  if (!metric) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请添加指标" className="mt-4" />;
  const value = analysisMetricValues(spec, result, metric)[0] ?? null;
  const style = resolveAnalysisStyle(spec.style);
  const tokens = resolveAnalysisThemeTokens(theme);
  const alignment = {
    left: { alignItems: 'flex-start' as const, textAlign: 'left' as const },
    center: { alignItems: 'center' as const, textAlign: 'center' as const },
    right: { alignItems: 'flex-end' as const, textAlign: 'right' as const },
  }[style.metricAlign];
  const valueSize = { sm: 24, md: 28, lg: 36 }[style.metricValueSize];
  return (
    <div
      className="flex h-full flex-col justify-center px-5"
      style={{ alignItems: alignment.alignItems, backgroundColor: tokens.backgroundColor }}
    >
      <div
        className="text-[12px] font-medium"
        style={{ textAlign: alignment.textAlign, color: tokens.mutedTextColor }}
      >
        {metricAnalysisDisplayName(spec, dataset, metric)}
      </div>
      <div
        className="mt-2 font-semibold tracking-[-0.02em]"
        style={{
          color: tokens.metricValueColor,
          fontSize: valueSize,
          lineHeight: 1.15,
          textAlign: alignment.textAlign,
        }}
      >
        {formatAnalysisMetricValue(spec, metric, value)}
      </div>
      {style.showMetricMeta ? (
        <div
          className="mt-2 text-[11px]"
          style={{ textAlign: alignment.textAlign, color: tokens.mutedTextColor, opacity: 0.78 }}
        >
          {dataset.name} · DV{result.datasetVersionNo} · {result.elapsedMillis}ms
        </div>
      ) : null}
    </div>
  );
}

function TableAnalysis({
  spec,
  dataset,
  result,
  theme,
  onSelect,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
  theme?: AnalysisThemeTokens;
  onSelect?: (selection: AnalysisSelection) => void;
}) {
  const style = resolveAnalysisStyle(spec.style);
  const tokens = resolveAnalysisThemeTokens(theme);
  const dimensions = spec.dimensions.map((field) => getAnalysisField(dataset, field)).filter(Boolean);
  const computedValues = new Map(spec.metrics.map((metric) => [
    metric.field,
    analysisMetricValues(spec, result, metric),
  ]));
  const cellPadding = style.tableDensity === 'compact'
    ? 'px-3 py-1.5'
    : style.tableDensity === 'relaxed'
      ? 'px-3 py-3'
      : 'px-3 py-2';
  const tableVars = {
    '--analysis-table-hover': tokens.hoverBackgroundColor,
  } as CSSProperties;
  return (
    <div
      className="h-full overflow-auto"
      style={{ ...tableVars, backgroundColor: tokens.backgroundColor }}
    >
      <table className="w-full border-collapse text-[11px]">
        <thead
          className="sticky top-0 z-10"
          style={{ backgroundColor: tokens.headerBackgroundColor, color: tokens.textColor }}
        >
          <tr>
            {dimensions.map((field) => (
              <th
                key={field?.key}
                className={`whitespace-nowrap border-b text-left font-medium ${cellPadding}`}
                style={{ borderColor: tokens.borderColor }}
              >
                {field?.label}
              </th>
            ))}
            {spec.metrics.map((metric) => (
              <th
                key={`${metric.field}-${metric.aggregation}`}
                className={`whitespace-nowrap border-b text-right font-medium ${cellPadding}`}
                style={{ borderColor: tokens.borderColor }}
              >
                {metricAnalysisDisplayName(spec, dataset, metric)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => {
            const striped = style.stripedRows && rowIndex % 2 === 1;
            const clickable = Boolean(onSelect && spec.dimensions.length);
            return (
              <tr
                key={rowIndex}
                className={`${clickable ? 'cursor-pointer' : ''} hover:!bg-[var(--analysis-table-hover)]`}
                style={{
                  backgroundColor: striped ? tokens.stripedBackgroundColor : tokens.backgroundColor,
                  color: tokens.textColor,
                }}
                onClick={() => {
                  const selection = selectionForRow(spec, dataset, result, rowIndex);
                  if (selection) onSelect?.(selection);
                }}
              >
                {spec.dimensions.map((field) => (
                  <td
                    key={field}
                    className={`border-b ${cellPadding}`}
                    style={{ borderColor: tokens.borderColor }}
                  >
                    {String(cell(result, row, field) ?? '')}
                  </td>
                ))}
                {spec.metrics.map((metric) => (
                  <td
                    key={`${metric.field}-${metric.aggregation}`}
                    className={`border-b text-right tabular-nums ${cellPadding}`}
                    style={{ borderColor: tokens.borderColor }}
                  >
                    {formatAnalysisMetricValue(spec, metric, computedValues.get(metric.field)?.[rowIndex])}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
      {result.truncated ? (
        <div className="px-3 py-2 text-[10px]" style={{ color: tokens.mutedTextColor }}>
          结果已截断，仅展示前 {result.returnedRows} 行
        </div>
      ) : null}
    </div>
  );
}

interface AnalysisPreviewProps {
  spec: AnalysisSpec;
  dataset?: PublishedDataset;
  runtimeFilters?: AnalysisFilter[];
  theme?: AnalysisThemeTokens;
  onSelect?: (selection: AnalysisSelection) => void;
  className?: string;
}

function AnalysisPreviewContent({
  spec,
  dataset,
  runtimeFilters = [],
  theme,
  onSelect,
  className = 'h-full min-h-0',
}: AnalysisPreviewProps) {
  const { result, loading, error, retry } = useAnalysisQuery(spec, dataset, runtimeFilters);
  const tokens = resolveAnalysisThemeTokens(theme);
  const themedStyle = { backgroundColor: tokens.backgroundColor, color: tokens.textColor };
  if (!dataset) {
    return <div className={className} style={themedStyle}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Dataset 不存在或已下线" className="mt-8" /></div>;
  }
  if (!canQueryAnalysis(spec)) {
    return <div className={className} style={themedStyle}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置必填编码" className="mt-8" /></div>;
  }
  if (loading && !result) {
    return <div className={`${className} flex items-center justify-center`} style={themedStyle}><Spin size="small" /></div>;
  }
  if (error) {
    return (
      <div className={`${className} flex items-center justify-center px-5 text-center`} style={themedStyle}>
        <div>
          <div className="text-[11px] text-[#b42318]">{error}</div>
          <YakButton size="small" type="text" className="mt-2 !h-7 !text-[10px] !text-[#667085]" onClick={retry}>
            重新查询
          </YakButton>
        </div>
      </div>
    );
  }
  if (!result) return <div className={className} style={themedStyle} />;

  return (
    <div className={`relative ${className}`} style={themedStyle}>
      {loading ? <Spin size="small" className="absolute right-3 top-3 z-20" /> : null}
      {spec.type === 'metric' ? (
        <MetricAnalysis spec={spec} dataset={dataset} result={result} theme={theme} />
      ) : null}
      {spec.type === 'table' ? (
        <TableAnalysis
          spec={spec}
          dataset={dataset}
          result={result}
          theme={theme}
          onSelect={onSelect}
        />
      ) : null}
      {spec.type !== 'metric' && spec.type !== 'table' ? (
        <EChartAnalysis
          spec={spec}
          dataset={dataset}
          result={result}
          theme={theme}
          onSelect={onSelect}
        />
      ) : null}
    </div>
  );
}

export function AnalysisPreview(props: AnalysisPreviewProps) {
  const resetKey = [
    props.dataset?.id ?? 'missing',
    props.dataset?.currentVersionNo ?? 0,
    JSON.stringify(props.spec),
    JSON.stringify(props.runtimeFilters ?? []),
    JSON.stringify(props.theme ?? {}),
  ].join(':');
  return (
    <AnalysisErrorBoundary resetKey={resetKey}>
      <AnalysisPreviewContent {...props} />
    </AnalysisErrorBoundary>
  );
}