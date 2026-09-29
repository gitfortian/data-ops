import { SCREEN_AGGREGATION_LABELS } from '@/features/digital-screen/binding';
import type {
  Aggregation,
  DatasetQueryResult,
  PublishedDataset,
  Scalar,
} from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';

export { SCREEN_AGGREGATION_LABELS };

export const getCell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation?: Aggregation,
) => {
  const index = result.bindings.findIndex((item) => (
    item.fieldId === fieldId
    && (aggregation ? item.aggregation === aggregation : !item.aggregation)
  ));
  return index >= 0 ? row[index] : null;
};

export const getNumericCell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation: Aggregation,
) => {
  const value = Number(getCell(result, row, fieldId, aggregation) ?? 0);
  return Number.isFinite(value) ? value : 0;
};

export const getFieldLabel = (dataset: PublishedDataset, fieldId: string) => (
  dataset.fields.find((field) => field.key === fieldId)?.label || fieldId
);

export const getMetricLabel = (
  dataset: PublishedDataset,
  metric: DigitalScreenComponentBinding['metrics'][number],
) => `${getFieldLabel(dataset, metric.field)} · ${SCREEN_AGGREGATION_LABELS[metric.aggregation]}`;

export const getRowLabel = (
  dataset: PublishedDataset,
  result: DatasetQueryResult,
  row: Scalar[],
  dimensions: string[],
) => dimensions.map((fieldId) => {
  const value = getCell(result, row, fieldId);
  return value == null ? getFieldLabel(dataset, fieldId) : String(value);
}).join(' / ');
