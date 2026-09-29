import type { ScreenRuntimeAdapterContext } from '../model';
import { getNumericCell } from './shared';

export const adaptMetricData = ({
  binding,
  dataset,
  result,
}: ScreenRuntimeAdapterContext) => {
  const metric = binding.metrics[0];
  if (!metric) return undefined;
  const row = result.rows[0];
  return {
    value: row ? getNumericCell(result, row, metric.field, metric.aggregation) : 0,
    trendLabel: `${dataset.name} · DV${result.datasetVersionNo}`,
  };
};
