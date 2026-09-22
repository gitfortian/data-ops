import { calculatedFieldKey } from './calculated-field';
import type {
  Aggregation,
  AnalysisEncoding,
  AnalysisEncodingBinding,
  AnalysisEncodingChannel,
  AnalysisSpec,
  ChartType,
  DatasetFieldRole,
  MetricBinding,
  PublishedDataset,
} from './model';

export interface AnalysisEncodingSlotRule {
  channel: AnalysisEncodingChannel;
  label: string;
  roles: DatasetFieldRole[];
  min: number;
  max: number;
  hint?: string;
}

const ENCODING_CHANNELS: AnalysisEncodingChannel[] = [
  'category',
  'value',
  'color',
  'size',
  'label',
  'detail',
  'tooltip',
];

const slot = (
  channel: AnalysisEncodingChannel,
  label: string,
  roles: DatasetFieldRole[],
  min: number,
  max: number,
  hint?: string,
): AnalysisEncodingSlotRule => ({ channel, label, roles, min, max, hint });

/** Semantic channel contracts for every chart renderer currently shipped by Yak Ops. */
export const ANALYSIS_ENCODING_RULES: Record<ChartType, AnalysisEncodingSlotRule[]> = {
  metric: [
    slot('value', '值', ['metric'], 1, 1, '指标卡需要 1 个指标'),
  ],
  bar: [
    slot('category', '分类', ['dimension'], 1, 1, '柱状图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 3, '最多 3 个指标'),
    slot('color', '颜色', ['dimension'], 0, 1, '可按 1 个维度拆分系列'),
  ],
  stackedBar: [
    slot('category', '分类', ['dimension'], 1, 1, '堆叠柱状图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 3, '最多 3 个指标；多个指标会自动堆叠'),
    slot('color', '堆叠分组', ['dimension'], 0, 1, '可按 1 个维度拆分堆叠系列'),
  ],
  line: [
    slot('category', '分类', ['dimension'], 1, 1, '折线图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 3, '最多 3 个指标'),
    slot('color', '颜色', ['dimension'], 0, 1, '可按 1 个维度拆分系列'),
  ],
  area: [
    slot('category', '分类', ['dimension'], 1, 1, '面积图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 3, '最多 3 个指标'),
    slot('color', '颜色', ['dimension'], 0, 1, '可按 1 个维度拆分系列'),
  ],
  pie: [
    slot('category', '分类', ['dimension'], 1, 1, '饼图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 1, '饼图需要 1 个指标'),
  ],
  scatter: [
    slot('category', '点标签', ['dimension'], 1, 1, '散点图需要 1 个维度标识每个点'),
    slot('value', '坐标值', ['metric'], 2, 2, '前两个指标分别作为 X / Y 轴'),
    slot('color', '颜色', ['dimension'], 0, 1, '可按 1 个维度拆分点系列'),
  ],
  radar: [
    slot('category', '对象', ['dimension'], 1, 1, '雷达图按 1 个维度生成对象'),
    slot('value', '指标轴', ['metric'], 2, 5, '配置 2~5 个指标作为雷达轴'),
  ],
  funnel: [
    slot('category', '阶段', ['dimension'], 1, 1, '漏斗图需要 1 个阶段字段'),
    slot('value', '值', ['metric'], 1, 1, '漏斗图需要 1 个指标'),
  ],
  treemap: [
    slot('category', '分类', ['dimension'], 1, 1, '矩形树图需要 1 个分类字段'),
    slot('value', '面积值', ['metric'], 1, 1, '矩形面积由 1 个指标决定'),
  ],
  table: [
    slot('category', '维度', ['dimension'], 0, 3, '最多 3 个维度'),
    slot('value', '指标', ['metric'], 0, 3, '最多 3 个指标'),
    slot('detail', '明细', ['dimension'], 0, 3, '追加明细维度，不改变指标定义'),
  ],
};

export const EMPTY_ANALYSIS_ENCODING: AnalysisEncoding = {
  version: 1,
  category: [],
  value: [],
  color: [],
  size: [],
  label: [],
  detail: [],
  tooltip: [],
};

const cloneBinding = (binding: AnalysisEncodingBinding): AnalysisEncodingBinding => ({
  field: binding.field,
  role: binding.role,
  aggregation: binding.aggregation,
});

export const cloneAnalysisEncoding = (encoding: AnalysisEncoding): AnalysisEncoding => ({
  version: 1,
  category: (encoding.category || []).map(cloneBinding),
  value: (encoding.value || []).map(cloneBinding),
  color: (encoding.color || []).map(cloneBinding),
  size: (encoding.size || []).map(cloneBinding),
  label: (encoding.label || []).map(cloneBinding),
  detail: (encoding.detail || []).map(cloneBinding),
  tooltip: (encoding.tooltip || []).map(cloneBinding),
});

/**
 * Legacy snapshots have only the query projection. Cartesian grouped charts can safely
 * infer category + color from the first two projected dimensions because Encoding v1 is
 * the first editor version that intentionally produces that shape.
 */
export const legacyAnalysisEncoding = (
  spec: Pick<AnalysisSpec, 'type' | 'dimensions' | 'metrics'>,
): AnalysisEncoding => {
  const groupedSeries = spec.type === 'bar'
    || spec.type === 'line'
    || spec.type === 'stackedBar'
    || spec.type === 'area';
  const categoryDimensions = groupedSeries ? spec.dimensions.slice(0, 1) : spec.dimensions;
  const colorDimensions = groupedSeries ? spec.dimensions.slice(1, 2) : [];
  return {
    ...cloneAnalysisEncoding(EMPTY_ANALYSIS_ENCODING),
    category: categoryDimensions.map((field) => ({ field, role: 'dimension' as const })),
    color: colorDimensions.map((field) => ({ field, role: 'dimension' as const })),
    value: spec.metrics.map((metric) => ({
      field: metric.field,
      role: 'metric' as const,
      aggregation: metric.aggregation,
    })),
  };
};

/** Existing snapshots without `encoding` are upgraded lazily and losslessly in memory. */
export const resolveAnalysisEncoding = (
  spec: Pick<AnalysisSpec, 'type' | 'encoding' | 'dimensions' | 'metrics'>,
): AnalysisEncoding => (
  spec.encoding?.version === 1
    ? cloneAnalysisEncoding(spec.encoding)
    : legacyAnalysisEncoding(spec)
);

const metricProjection = (bindings: AnalysisEncodingBinding[]): MetricBinding[] => bindings
  .filter((binding) => binding.role === 'metric')
  .map((binding) => ({
    field: binding.field,
    aggregation: binding.aggregation ?? 'SUM',
  }));

const unique = <T,>(values: T[]) => [...new Set(values)];

/**
 * Keeps the existing query/render contract alive while encoding becomes the editor's
 * semantic source of truth. Inactive and overflow bindings stay persisted in
 * `encoding`; only active chart slots are projected into dimensions / metrics.
 */
export const applyAnalysisEncoding = <T extends AnalysisSpec>(
  spec: T,
  encoding: AnalysisEncoding,
): T => {
  const rules = ANALYSIS_ENCODING_RULES[spec.type];
  const valueRule = rules.find((rule) => rule.channel === 'value');
  const dimensions = unique(rules.flatMap((rule) => (
    rule.roles.includes('dimension')
      ? encoding[rule.channel]
        .filter((binding) => binding.role === 'dimension')
        .slice(0, rule.max)
        .map((binding) => binding.field)
      : []
  )));

  return {
    ...spec,
    encoding: cloneAnalysisEncoding(encoding),
    dimensions: spec.type === 'metric' ? [] : dimensions,
    metrics: valueRule
      ? metricProjection(
        encoding.value.filter((binding) => valueRule.roles.includes(binding.role)),
      ).slice(0, valueRule.max)
      : [],
  };
};

const compatibleChannel = (
  bindings: AnalysisEncodingBinding[],
  rule: AnalysisEncodingSlotRule,
) => bindings
  .filter((binding) => rule.roles.includes(binding.role))
  .map(cloneBinding);

/**
 * Chart switching is non-destructive: incompatible roles are moved behind compatible
 * bindings for target-active channels, while overflow bindings remain parked. Switching
 * back can therefore restore previous field choices.
 */
export const changeAnalysisEncodingType = <T extends AnalysisSpec>(
  spec: T,
  type: ChartType,
): T => {
  const encoding = resolveAnalysisEncoding(spec);
  const next = cloneAnalysisEncoding(encoding);
  ANALYSIS_ENCODING_RULES[type].forEach((rule) => {
    const compatible = compatibleChannel(next[rule.channel], rule);
    const incompatible = next[rule.channel].filter((binding) => !rule.roles.includes(binding.role));
    next[rule.channel] = [...compatible, ...incompatible];
  });
  return applyAnalysisEncoding({ ...spec, type } as T, next);
};

export const analysisEncodingFieldKeys = (
  spec: Pick<AnalysisSpec, 'type' | 'encoding' | 'dimensions' | 'metrics'>,
) => {
  const encoding = resolveAnalysisEncoding(spec);
  return new Set<string>(ENCODING_CHANNELS.flatMap(
    (channel) => encoding[channel].map((binding) => binding.field),
  ));
};

const validBindingForDataset = (
  binding: AnalysisEncodingBinding,
  dataset: PublishedDataset,
  calculatedFields: Set<string>,
) => {
  if (binding.role === 'metric' && calculatedFields.has(binding.field)) {
    return { ...binding, aggregation: binding.aggregation ?? 'SUM' } satisfies AnalysisEncodingBinding;
  }
  const field = dataset.fields.find((item) => item.key === binding.field);
  if (!field || field.role !== binding.role) return undefined;
  return {
    ...binding,
    aggregation: binding.role === 'metric'
      ? (binding.aggregation ?? 'SUM')
      : undefined,
  } satisfies AnalysisEncodingBinding;
};

const fillRequiredBindings = (
  encoding: AnalysisEncoding,
  type: ChartType,
  dataset: PublishedDataset,
) => {
  const next = cloneAnalysisEncoding(encoding);
  ANALYSIS_ENCODING_RULES[type].forEach((rule) => {
    const compatible = next[rule.channel].filter((binding) => rule.roles.includes(binding.role));
    const active = compatible.slice(0, rule.max);
    if (active.length >= rule.min) return;

    const used = new Set(next[rule.channel].map((binding) => binding.field));
    const candidates = dataset.fields.filter((field) => (
      rule.roles.includes(field.role) && !used.has(field.key)
    ));
    const missing = Math.max(0, rule.min - active.length);
    next[rule.channel] = [
      ...compatible,
      ...candidates.slice(0, missing).map((field) => ({
        field: field.key,
        role: field.role,
        aggregation: field.role === 'metric' ? 'SUM' as Aggregation : undefined,
      })),
      ...next[rule.channel].filter((binding) => !rule.roles.includes(binding.role)),
    ];
  });
  return next;
};

/** Dataset changes validate all channels, preserve chart-local metrics, then seed required slots. */
export const rebindAnalysisEncoding = <T extends AnalysisSpec>(
  spec: T,
  dataset: PublishedDataset,
): T => {
  const source = resolveAnalysisEncoding(spec);
  const next = cloneAnalysisEncoding(EMPTY_ANALYSIS_ENCODING);
  const calculatedFields = new Set(
    (spec.analysis?.calculatedFields ?? []).map((field) => calculatedFieldKey(field)),
  );
  ENCODING_CHANNELS.forEach((channel) => {
    next[channel] = source[channel]
      .map((binding) => validBindingForDataset(binding, dataset, calculatedFields))
      .filter((binding): binding is AnalysisEncodingBinding => Boolean(binding));
  });
  const seeded = fillRequiredBindings(next, spec.type, dataset);
  return applyAnalysisEncoding({ ...spec, datasetId: dataset.id } as T, seeded);
};

export const updateEncodingMetricAggregation = <T extends AnalysisSpec>(
  spec: T,
  field: string,
  aggregation: Aggregation,
): T => {
  const encoding = resolveAnalysisEncoding(spec);
  const patchChannel = (bindings: AnalysisEncodingBinding[]) => bindings.map((binding) => (
    binding.field === field && binding.role === 'metric'
      ? { ...binding, aggregation }
      : binding
  ));
  const next: AnalysisEncoding = {
    ...encoding,
    value: patchChannel(encoding.value),
    color: patchChannel(encoding.color),
    size: patchChannel(encoding.size),
    label: patchChannel(encoding.label),
    detail: patchChannel(encoding.detail),
    tooltip: patchChannel(encoding.tooltip),
  };
  return applyAnalysisEncoding(spec, next);
};

export const encodingMeetsChartRequirements = (spec: AnalysisSpec) => {
  const encoding = resolveAnalysisEncoding(spec);
  return ANALYSIS_ENCODING_RULES[spec.type].every((rule) => {
    const activeCount = encoding[rule.channel]
      .filter((binding) => rule.roles.includes(binding.role))
      .slice(0, rule.max)
      .length;
    return activeCount >= rule.min;
  });
};
