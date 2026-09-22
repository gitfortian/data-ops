import type {
  ScreenAggregation,
  ScreenComponent,
} from '@/components/screen-engine';

const BINDABLE_SCREEN_COMPONENT_TYPES = new Set<ScreenComponent['type']>([
  'metric',
  'line',
  'bar',
  'pie',
  'table',
]);

export const SCREEN_AGGREGATION_LABELS: Record<ScreenAggregation, string> = {
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  COUNT_DISTINCT: '去重计数',
  MAX: '最大值',
  MIN: '最小值',
};

export const isBindableScreenComponentType = (
  type: ScreenComponent['type'],
) => BINDABLE_SCREEN_COMPONENT_TYPES.has(type);

export const isBindableScreenComponent = (component?: ScreenComponent) => (
  Boolean(component && isBindableScreenComponentType(component.type))
);
