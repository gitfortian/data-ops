import type { ScreenRuntimeAdapterContext } from '../model';
import { getNumericCell, getRowLabel } from './shared';

export const adaptPieData = ({
  binding,
  dataset,
  result,
}: ScreenRuntimeAdapterContext) => {
  const metric = binding.metrics[0];
  if (!metric) return undefined;
  return {
    items: result.rows.map((row) => ({
      name: getRowLabel(dataset, result, row, binding.dimensions),
      value: getNumericCell(result, row, metric.field, metric.aggregation),
    })),
  };
};
