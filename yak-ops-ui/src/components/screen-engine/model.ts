export type ScreenComponentType = 'metric' | 'line' | 'bar' | 'pie' | 'table' | 'text' | 'map' | 'ticker';
export type ScreenScalar = string | number | boolean | null;
export type ScreenAggregation = 'SUM' | 'AVG' | 'COUNT' | 'COUNT_DISTINCT' | 'MAX' | 'MIN';

export interface ScreenTheme {
  background: string;
  textColor: string;
  mutedTextColor: string;
  primaryColor: string;
  panelBackground: string;
  panelBorderColor: string;
  fontFamily?: string;
}

export interface ScreenMetricBinding {
  field: string;
  aggregation: ScreenAggregation;
}

/**
 * Dataset binding is intentionally optional in phase 1. A template can render
 * entirely from preview data first, then phase 2 can persist a dataset binding
 * on the created screen instance without changing the template protocol.
 */
export interface ScreenDataBinding {
  datasetId?: string;
  dimensions?: string[];
  metrics?: ScreenMetricBinding[];
}

export interface ScreenComponentStyle {
  background?: string;
  borderColor?: string;
  borderRadius?: number;
  padding?: number;
  color?: string;
  titleColor?: string;
  subtitleColor?: string;
  valueColor?: string;
  accentColor?: string;
  shadow?: string;
  fontSize?: number;
  fontWeight?: number;
  letterSpacing?: number;
  textAlign?: 'left' | 'center' | 'right';
  frame?: 'standard' | 'glass' | 'hud';
  glow?: boolean;
  effect?: 'none' | 'scan' | 'pulse';
}

export interface ScreenComponentBase {
  id: string;
  type: ScreenComponentType;
  x: number;
  y: number;
  width: number;
  height: number;
  title?: string;
  subtitle?: string;
  style?: ScreenComponentStyle;
  dataBinding?: ScreenDataBinding;
}

export interface ScreenMetricData {
  value: string | number;
  unit?: string;
  trend?: number;
  trendDirection?: 'up' | 'down' | 'flat';
  trendLabel?: string;
}

export interface ScreenMetricComponent extends ScreenComponentBase {
  type: 'metric';
  data?: ScreenMetricData;
}

export interface ScreenSeries {
  name: string;
  values: number[];
}

export interface ScreenCartesianData {
  categories: string[];
  series: ScreenSeries[];
}

export interface ScreenChartOptions {
  showLegend?: boolean;
  showGrid?: boolean;
  showLabels?: boolean;
  smooth?: boolean;
  horizontal?: boolean;
  showArea?: boolean;
  gradient?: boolean;
  neon?: boolean;
  rose?: boolean;
}

export interface ScreenLineComponent extends ScreenComponentBase {
  type: 'line';
  data?: ScreenCartesianData;
  options?: ScreenChartOptions;
}

export interface ScreenBarComponent extends ScreenComponentBase {
  type: 'bar';
  data?: ScreenCartesianData;
  options?: ScreenChartOptions;
}

export interface ScreenPieItem {
  name: string;
  value: number;
}

export interface ScreenPieData {
  items: ScreenPieItem[];
}

export interface ScreenPieComponent extends ScreenComponentBase {
  type: 'pie';
  data?: ScreenPieData;
  options?: Pick<ScreenChartOptions, 'showLegend' | 'showLabels' | 'gradient' | 'neon' | 'rose'>;
}

export interface ScreenTableColumn {
  key: string;
  title: string;
  align?: 'left' | 'center' | 'right';
  width?: number;
}

export interface ScreenTableData {
  columns: ScreenTableColumn[];
  rows: Array<Record<string, ScreenScalar>>;
}

export interface ScreenTableComponent extends ScreenComponentBase {
  type: 'table';
  data?: ScreenTableData;
}

export interface ScreenTextData {
  content: string;
}

export interface ScreenTextComponent extends ScreenComponentBase {
  type: 'text';
  data?: ScreenTextData;
}

export interface ScreenMapPoint {
  id: string;
  name: string;
  x: number;
  y: number;
  value?: number;
  tone?: 'primary' | 'success' | 'warning' | 'danger';
}

export interface ScreenMapRoute {
  from: string;
  to: string;
  intensity?: number;
}

export interface ScreenMapData {
  /** Normalized 0-100 polygon used as a lightweight offline geographic silhouette. */
  outline: Array<[number, number]>;
  points: ScreenMapPoint[];
  routes: ScreenMapRoute[];
  centerId?: string;
}

export interface ScreenMapOptions {
  showLabels?: boolean;
  showRoutes?: boolean;
  pulse?: boolean;
  scan?: boolean;
}

export interface ScreenMapComponent extends ScreenComponentBase {
  type: 'map';
  data?: ScreenMapData;
  options?: ScreenMapOptions;
}

export interface ScreenTickerItem {
  label: string;
  value: string;
  meta?: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger';
}

export interface ScreenTickerData {
  items: ScreenTickerItem[];
}

export interface ScreenTickerOptions {
  speed?: number;
}

export interface ScreenTickerComponent extends ScreenComponentBase {
  type: 'ticker';
  data?: ScreenTickerData;
  options?: ScreenTickerOptions;
}

export type ScreenComponent =
  | ScreenMetricComponent
  | ScreenLineComponent
  | ScreenBarComponent
  | ScreenPieComponent
  | ScreenTableComponent
  | ScreenTextComponent
  | ScreenMapComponent
  | ScreenTickerComponent;

export type ScreenComponentData = NonNullable<ScreenComponent['data']>;

export interface ScreenTemplate {
  version: 1;
  id: string;
  name: string;
  description?: string;
  category: string;
  width: number;
  height: number;
  theme: ScreenTheme;
  components: ScreenComponent[];
}

export type ScreenDataOverrides = Partial<Record<string, ScreenComponentData>>;
