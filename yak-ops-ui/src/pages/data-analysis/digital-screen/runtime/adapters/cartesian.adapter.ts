import type { ScreenRuntimeAdapterContext } from '../model';
import { getMetricLabel, getNumericCell, getRowLabel } from './shared';

export const adaptCartesianData = ({
  binding,
  dataset,
  result,
}: ScreenRuntimeAdapterContext) => ({
  categories: result.rows.map((row) => getRowLabel(dataset, result, row, binding.dimensions)),
  series: binding.metrics.map((metric) => ({
    name: getMetricLabel(dataset, metric),
    values: result.rows.map((row) => getNumericCell(
      result,
      row,
      metric.field,
      metric.aggregation,
    )),
  })),
});
