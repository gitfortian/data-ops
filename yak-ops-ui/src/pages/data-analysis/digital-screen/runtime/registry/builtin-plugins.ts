import { isBindableScreenComponentType } from '@/features/digital-screen/binding';
import type { DatasetQueryPayload } from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import { adaptCartesianData } from '../adapters/cartesian.adapter';
import { adaptMetricData } from '../adapters/metric.adapter';
import { adaptPieData } from '../adapters/pie.adapter';
import { adaptTableData } from '../adapters/table.adapter';
import {
  ScreenRuntimeComponentRegistry,
  type ScreenRuntimeComponentPlugin,
} from './component-registry';

const payload = (
  binding: DigitalScreenComponentBinding,
  dimensions: string[],
  limit: number,
): DatasetQueryPayload => ({
  dimensions,
  metrics: binding.metrics.map((metric) => ({
    fieldId: metric.field,
    aggregation: metric.aggregation,
  })),
  filters: [],
  sorts: [],
  limit,
  timeoutSeconds: 30,
});

const metricPlugin: ScreenRuntimeComponentPlugin = {
  type: 'metric',
  bindable: isBindableScreenComponentType('metric'),
  canQuery: (binding) => binding.metrics.length === 1,
  buildQuery: (binding) => payload(binding, [], 200),
  adaptData: adaptMetricData,
};

const cartesianPlugin = (type: 'line' | 'bar'): ScreenRuntimeComponentPlugin => ({
  type,
  bindable: isBindableScreenComponentType(type),
  canQuery: (binding) => binding.dimensions.length > 0 && binding.metrics.length > 0,
  buildQuery: (binding) => payload(binding, binding.dimensions, 200),
  adaptData: adaptCartesianData,
});

const piePlugin: ScreenRuntimeComponentPlugin = {
  type: 'pie',
  bindable: isBindableScreenComponentType('pie'),
  canQuery: (binding) => binding.dimensions.length > 0 && binding.metrics.length > 0,
  buildQuery: (binding) => payload(binding, binding.dimensions, 200),
  adaptData: adaptPieData,
};

const tablePlugin: ScreenRuntimeComponentPlugin = {
  type: 'table',
  bindable: isBindableScreenComponentType('table'),
  canQuery: (binding) => binding.dimensions.length > 0 || binding.metrics.length > 0,
  buildQuery: (binding) => payload(binding, binding.dimensions, 100),
  adaptData: adaptTableData,
};

const staticPlugin = (type: 'text' | 'map' | 'ticker'): ScreenRuntimeComponentPlugin => ({
  type,
  bindable: isBindableScreenComponentType(type),
});

export const createBuiltinScreenRuntimeComponentRegistry = () => [
  metricPlugin,
  cartesianPlugin('line'),
  cartesianPlugin('bar'),
  piePlugin,
  tablePlugin,
  staticPlugin('text'),
  staticPlugin('map'),
  staticPlugin('ticker'),
].reduce(
  (registry, plugin) => registry.register(plugin),
  new ScreenRuntimeComponentRegistry(),
);

export const screenRuntimeComponentRegistry = createBuiltinScreenRuntimeComponentRegistry();
