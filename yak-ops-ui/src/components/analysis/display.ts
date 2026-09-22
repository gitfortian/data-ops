import {
  metricComputationFor,
  quickCalculationLabel,
} from './analysis';
import { calculatedFieldFor } from './calculated-field';
import type {
  Aggregation,
  AnalysisSpec,
  MetricBinding,
  PublishedDataset,
} from './model';

export const AGGREGATION_LABELS: Record<Aggregation, string> = {
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  COUNT_DISTINCT: '去重计数',
  MAX: '最大值',
  MIN: '最小值',
};

export const getAnalysisField = (dataset: PublishedDataset, fieldKey?: string) =>
  dataset.fields.find((field) => field.key === fieldKey);

export const metricFieldLabel = (
  spec: Pick<AnalysisSpec, 'analysis'>,
  dataset: PublishedDataset,
  metric: MetricBinding,
) => calculatedFieldFor(spec, metric.field)?.name
  ?? getAnalysisField(dataset, metric.field)?.label
  ?? metric.field;

export const metricDisplayName = (
  dataset: PublishedDataset,
  metric: MetricBinding,
) => `${getAnalysisField(dataset, metric.field)?.label ?? metric.field} · ${AGGREGATION_LABELS[metric.aggregation]}`;

export const metricAnalysisDisplayName = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  metric: MetricBinding,
) => {
  const calculated = calculatedFieldFor(spec, metric.field);
  const base = calculated
    ? `${calculated.name} · 计算字段`
    : metricDisplayName(dataset, metric);
  const calculation = spec.type === 'metric'
    ? 'none'
    : metricComputationFor(spec, metric.field).quickCalculation;
  return calculation === 'none' ? base : `${base} · ${quickCalculationLabel(calculation)}`;
};
