import type { Scalar } from '@/services/dataset';
import type { ScreenRuntimeAdapterContext } from '../model';
import { getCell, getFieldLabel, getMetricLabel } from './shared';

export const adaptTableData = ({
  binding,
  dataset,
  result,
}: ScreenRuntimeAdapterContext) => {
  const dimensionColumns = binding.dimensions.map((fieldId) => ({
    key: `dimension:${fieldId}`,
    title: getFieldLabel(dataset, fieldId),
    align: 'left' as const,
  }));
  const metricColumns = binding.metrics.map((metric) => ({
    key: `metric:${metric.field}:${metric.aggregation}`,
    title: getMetricLabel(dataset, metric),
    align: 'right' as const,
  }));
  return {
    columns: [...dimensionColumns, ...metricColumns],
    rows: result.rows.map((row) => {
      const record: Record<string, Scalar> = {};
      binding.dimensions.forEach((fieldId) => {
        record[`dimension:${fieldId}`] = getCell(result, row, fieldId);
      });
      binding.metrics.forEach((metric) => {
        record[`metric:${metric.field}:${metric.aggregation}`] = getCell(
          result,
          row,
          metric.field,
          metric.aggregation,
        );
      });
      return record;
    }),
  };
};
