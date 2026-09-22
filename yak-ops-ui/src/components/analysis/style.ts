import type {
  AnalysisColorPalette,
  AnalysisVisualConfig,
  ChartType,
} from './model';

export const ANALYSIS_PALETTES: Record<
  AnalysisColorPalette,
  { label: string; colors: string[] }
> = {
  yak: {
    label: 'Yak',
    colors: ['#fe2c55', '#5b6df8', '#8b5cf6', '#f59e0b', '#0ea5e9', '#64748b'],
  },
  ocean: {
    label: '海洋',
    colors: ['#2563eb', '#0ea5e9', '#06b6d4', '#14b8a6', '#6366f1', '#3b82f6'],
  },
  sunset: {
    label: '日落',
    colors: ['#f97316', '#ef4444', '#f59e0b', '#ec4899', '#8b5cf6', '#fb7185'],
  },
  violet: {
    label: '紫罗兰',
    colors: ['#7c3aed', '#a855f7', '#c026d3', '#6366f1', '#8b5cf6', '#d946ef'],
  },
  mono: {
    label: '灰阶',
    colors: ['#111827', '#374151', '#6b7280', '#9ca3af', '#cbd5e1', '#e2e8f0'],
  },
};

export const DEFAULT_ANALYSIS_VISUAL_CONFIG: Required<AnalysisVisualConfig> = {
  version: 1,
  showLegend: false,
  showDataLabels: false,
  smooth: false,
  showGrid: true,
  palette: 'yak',
  legendPosition: 'top',
  dataLabelPosition: 'top',
  axisLabelRotation: 0,
  lineWidth: 2,
  symbolSize: 5,
  barMaxWidth: 34,
  barRadius: 3,
  pieInnerRadius: 42,
  metricAlign: 'left',
  metricValueSize: 'md',
  showMetricMeta: true,
  tableDensity: 'comfortable',
  stripedRows: false,
};

const clamp = (value: number | undefined, min: number, max: number, fallback: number) => {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return fallback;
  return Math.min(max, Math.max(min, numeric));
};

/** Resolve legacy / partial style snapshots to one stable renderer contract. */
export const resolveAnalysisStyle = (
  style?: AnalysisVisualConfig,
): Required<AnalysisVisualConfig> => ({
  ...DEFAULT_ANALYSIS_VISUAL_CONFIG,
  ...style,
  version: 1,
  palette: style?.palette ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.palette,
  legendPosition: style?.legendPosition ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.legendPosition,
  dataLabelPosition: style?.dataLabelPosition ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.dataLabelPosition,
  axisLabelRotation: style?.axisLabelRotation ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.axisLabelRotation,
  lineWidth: clamp(style?.lineWidth, 1, 6, DEFAULT_ANALYSIS_VISUAL_CONFIG.lineWidth),
  symbolSize: clamp(style?.symbolSize, 0, 14, DEFAULT_ANALYSIS_VISUAL_CONFIG.symbolSize),
  barMaxWidth: clamp(style?.barMaxWidth, 12, 72, DEFAULT_ANALYSIS_VISUAL_CONFIG.barMaxWidth),
  barRadius: clamp(style?.barRadius, 0, 16, DEFAULT_ANALYSIS_VISUAL_CONFIG.barRadius),
  pieInnerRadius: clamp(style?.pieInnerRadius, 0, 64, DEFAULT_ANALYSIS_VISUAL_CONFIG.pieInnerRadius),
  metricAlign: style?.metricAlign ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.metricAlign,
  metricValueSize: style?.metricValueSize ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.metricValueSize,
  showMetricMeta: style?.showMetricMeta ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.showMetricMeta,
  tableDensity: style?.tableDensity ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.tableDensity,
  stripedRows: style?.stripedRows ?? DEFAULT_ANALYSIS_VISUAL_CONFIG.stripedRows,
});

const defaultLegendTypes = new Set<ChartType>(['pie', 'stackedBar', 'area', 'radar']);
const smoothTypes = new Set<ChartType>(['line', 'area']);
const gridTypes = new Set<ChartType>(['bar', 'stackedBar', 'line', 'area', 'scatter']);

export const createAnalysisVisualConfig = (type: ChartType): AnalysisVisualConfig => ({
  ...DEFAULT_ANALYSIS_VISUAL_CONFIG,
  showLegend: defaultLegendTypes.has(type),
  smooth: smoothTypes.has(type),
  showGrid: gridTypes.has(type),
});

export const paletteColors = (style?: AnalysisVisualConfig) => {
  const resolved = resolveAnalysisStyle(style);
  return ANALYSIS_PALETTES[resolved.palette].colors;
};
