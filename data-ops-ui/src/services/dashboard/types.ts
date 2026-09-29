import type {
  AnalysisSpec,
  FilterOperator,
  Scalar,
} from '@/components/analysis/model';

export type { AnalysisThemeTokens } from '@/components/analysis/analysis-theme';
export type {
  Aggregation,
  AnalysisAsset,
  AnalysisEncoding,
  AnalysisEncodingBinding,
  AnalysisEncodingChannel,
  AnalysisFilter as DashboardFilter,
  AnalysisSelection,
  AnalysisSort as DashboardSort,
  AnalysisVisualConfig as DashboardWidgetStyle,
  ChartType,
  DatasetField,
  DatasetFieldRole,
  DatasetFieldType,
  DatasetQueryColumn,
  DatasetQueryColumnBinding,
  DatasetQueryFilter,
  DatasetQueryMetric,
  DatasetQueryPayload,
  DatasetQueryResult,
  DatasetQuerySort,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  Scalar,
  SortDirection,
} from '@/components/analysis/model';

export type DashboardWidgetClickAction =
  | 'none'
  | 'drill'
  | 'dashboard'
  | 'yak';
export type DashboardThemePresetId =
  | 'yak-light'
  | 'yak-dark'
  | 'ocean-night'
  | 'graphite'
  | 'mist-blue';

export interface DashboardThemeCanvas {
  backgroundColor?: string;
}

export interface DashboardThemeComponent {
  backgroundColor?: string;
  textColor?: string;
  mutedTextColor?: string;
  borderColor?: string;
}

export interface DashboardThemeChart {
  palette?: string[];
  textColor?: string;
  axisColor?: string;
  gridColor?: string;
  metricValueColor?: string;
}

export interface DashboardThemeTable {
  headerBackgroundColor?: string;
  stripedBackgroundColor?: string;
  hoverBackgroundColor?: string;
}

/** Dashboard-level visual defaults persisted with each immutable version. */
export interface DashboardTheme {
  presetId?: DashboardThemePresetId;
  canvas?: DashboardThemeCanvas;
  component?: DashboardThemeComponent;
  chart?: DashboardThemeChart;
  table?: DashboardThemeTable;
}

export interface DashboardCrossFilterRule {
  id: string;
  sourceField: string;
  targetWidgetId: string;
  targetField: string;
}

export interface DashboardWidgetBehavior {
  crossFilters?: DashboardCrossFilterRule[];
  clickAction?: DashboardWidgetClickAction;
  drillFields?: string[];
  targetDashboardId?: string;
  targetPath?: string;
  queryParam?: string;
}

export interface DashboardInlineAnalysisSpec extends AnalysisSpec {
  dashboardBehavior?: DashboardWidgetBehavior;
}

export interface DashboardDrillStep {
  field: string;
  value: Scalar;
  label: string;
}

export interface DashboardWidget {
  id: string;
  analysisId?: string;
  title?: string;
  inlineAnalysis?: DashboardInlineAnalysisSpec;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
}

export type DashboardWidgetEditorModel = DashboardWidget &
  AnalysisSpec & { title: string };

export interface DashboardGlobalFilterBinding {
  widgetId: string;
  field: string;
}

export interface DashboardGlobalFilter {
  id: string;
  name: string;
  operator: FilterOperator;
  defaultValue?: Scalar;
  bindings: DashboardGlobalFilterBinding[];
}

export interface DashboardInteraction {
  id: string;
  event: 'select';
  sourceWidgetId: string;
  sourceField: string;
  targetFilterId: string;
}

export interface DashboardDocument {
  version: 1;
  id: string;
  name: string;
  description?: string;
  activeDatasetId: string;
  theme?: DashboardTheme;
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  currentVersionNo?: number;
  currentVersionId?: string;
  publishedVersionNo?: number;
  publishedVersionId?: string;
  publishedAt?: string;
  updatedAt?: string;
}

export interface DashboardSummary {
  id: string;
  name: string;
  description: string;
  currentVersionId?: string;
  currentVersionNo: number;
  publishedVersionId?: string;
  publishedVersionNo: number;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface DashboardVersionSummary {
  id: string;
  dashboardId: string;
  versionNo: number;
  name: string;
  description: string;
  activeDatasetId?: string;
  createTime?: string;
}

export interface DashboardServerDetail {
  dashboard: DashboardSummary;
  currentVersion?: DashboardVersionSummary;
  theme?: DashboardTheme;
  versions: DashboardVersionSummary[];
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
}

export interface DashboardVersionDetail {
  dashboard: DashboardSummary;
  version: DashboardVersionSummary;
  theme?: DashboardTheme;
  widgets: DashboardWidget[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
}

export interface DashboardOverview {
  dashboardCount: number;
  publishedDashboardCount: number;
  recentDashboards: DashboardSummary[];
}

export interface DashboardQueryPerformance {
  queryId: string;
  datasetId: string;
  datasetName: string;
  datasetVersionId: string;
  datasetVersionNo: number;
  sourceType: 'QUERY_REVISION' | 'SQL_QUERY' | 'TABLE' | 'VIEW';
  dataSourceId?: string | null;
  sql: string;
  waitMillis: number;
  prepareMillis: number;
  executeMillis: number;
  transferMillis: number;
  totalMillis: number;
  returnedRows: number;
  truncated: boolean;
  startedAt: string;
}
