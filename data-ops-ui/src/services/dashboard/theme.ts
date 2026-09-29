import type { AnalysisThemeTokens } from '@/components/analysis/analysis-theme';

import type {
  DashboardTheme,
  DashboardThemePresetId,
} from './types';

export type DashboardThemeTone = 'light' | 'dark';

export interface ResolvedDashboardTheme {
  presetId: DashboardThemePresetId;
  name: string;
  tone: DashboardThemeTone;
  canvas: {
    backgroundColor: string;
    gridDotColor: string;
  };
  component: {
    backgroundColor: string;
    textColor: string;
    mutedTextColor: string;
    borderColor: string;
    subtleBackgroundColor: string;
    hoverBackgroundColor: string;
  };
  chart: {
    palette: string[];
    textColor: string;
    axisColor: string;
    gridColor: string;
    tooltipBackgroundColor: string;
    tooltipTextColor: string;
    metricValueColor: string;
  };
  table: {
    headerBackgroundColor: string;
    stripedBackgroundColor: string;
    hoverBackgroundColor: string;
  };
}

export const DASHBOARD_THEME_PRESETS: ResolvedDashboardTheme[] = [
  {
    presetId: 'yak-light',
    name: '默认亮色',
    tone: 'light',
    canvas: {
      backgroundColor: '#f3f4f6',
      gridDotColor: 'rgba(15,23,42,.035)',
    },
    component: {
      backgroundColor: '#ffffff',
      textColor: '#344054',
      mutedTextColor: '#667085',
      borderColor: '#e7e9ed',
      subtleBackgroundColor: '#fafbfc',
      hoverBackgroundColor: '#f5f7fa',
    },
    chart: {
      palette: [
        '#fe2c55',
        '#5b6df8',
        '#8b5cf6',
        '#f59e0b',
        '#0ea5e9',
        '#64748b',
      ],
      textColor: '#475467',
      axisColor: '#d8dde6',
      gridColor: '#eef1f5',
      tooltipBackgroundColor: 'rgba(17,24,39,.94)',
      tooltipTextColor: '#ffffff',
      metricValueColor: '#161823',
    },
    table: {
      headerBackgroundColor: '#fafafa',
      stripedBackgroundColor: '#fafbfc',
      hoverBackgroundColor: '#f7f8fa',
    },
  },
  {
    presetId: 'yak-dark',
    name: '极夜',
    tone: 'dark',
    canvas: {
      backgroundColor: '#111827',
      gridDotColor: 'rgba(255,255,255,.06)',
    },
    component: {
      backgroundColor: '#1d2736',
      textColor: '#f1f5f9',
      mutedTextColor: '#9aa9bc',
      borderColor: '#344054',
      subtleBackgroundColor: '#182230',
      hoverBackgroundColor: '#243244',
    },
    chart: {
      palette: [
        '#35d0ff',
        '#5b8cff',
        '#8b5cf6',
        '#2dd4bf',
        '#fbbf24',
        '#f472b6',
      ],
      textColor: '#d6deea',
      axisColor: '#52637a',
      gridColor: '#2d3b4f',
      tooltipBackgroundColor: '#0b1220',
      tooltipTextColor: '#f8fafc',
      metricValueColor: '#f8fafc',
    },
    table: {
      headerBackgroundColor: '#182230',
      stripedBackgroundColor: '#202b3a',
      hoverBackgroundColor: '#29384b',
    },
  },
  {
    presetId: 'ocean-night',
    name: '深海蓝',
    tone: 'dark',
    canvas: {
      backgroundColor: '#061526',
      gridDotColor: 'rgba(125,211,252,.075)',
    },
    component: {
      backgroundColor: '#0a2138',
      textColor: '#e5f3ff',
      mutedTextColor: '#8db3d3',
      borderColor: '#163b59',
      subtleBackgroundColor: '#0b2944',
      hoverBackgroundColor: '#103553',
    },
    chart: {
      palette: [
        '#38bdf8',
        '#22d3ee',
        '#60a5fa',
        '#818cf8',
        '#2dd4bf',
        '#fbbf24',
      ],
      textColor: '#c7e4f7',
      axisColor: '#2d5c7c',
      gridColor: '#15344d',
      tooltipBackgroundColor: '#071624',
      tooltipTextColor: '#edf8ff',
      metricValueColor: '#7dd3fc',
    },
    table: {
      headerBackgroundColor: '#0b2944',
      stripedBackgroundColor: '#0c2841',
      hoverBackgroundColor: '#103553',
    },
  },
  {
    presetId: 'graphite',
    name: '石墨',
    tone: 'dark',
    canvas: {
      backgroundColor: '#18191b',
      gridDotColor: 'rgba(255,255,255,.05)',
    },
    component: {
      backgroundColor: '#242528',
      textColor: '#f4f4f5',
      mutedTextColor: '#a1a1aa',
      borderColor: '#3f3f46',
      subtleBackgroundColor: '#2b2c30',
      hoverBackgroundColor: '#34353a',
    },
    chart: {
      palette: [
        '#f4f4f5',
        '#a1a1aa',
        '#71717a',
        '#fe2c55',
        '#d4d4d8',
        '#52525b',
      ],
      textColor: '#d4d4d8',
      axisColor: '#52525b',
      gridColor: '#35363b',
      tooltipBackgroundColor: '#0f0f10',
      tooltipTextColor: '#fafafa',
      metricValueColor: '#ffffff',
    },
    table: {
      headerBackgroundColor: '#2b2c30',
      stripedBackgroundColor: '#292a2e',
      hoverBackgroundColor: '#34353a',
    },
  },
  {
    presetId: 'mist-blue',
    name: '云雾蓝',
    tone: 'light',
    canvas: {
      backgroundColor: '#eaf1f8',
      gridDotColor: 'rgba(44,82,130,.055)',
    },
    component: {
      backgroundColor: '#fafdff',
      textColor: '#26364b',
      mutedTextColor: '#6f8096',
      borderColor: '#d6e0eb',
      subtleBackgroundColor: '#f1f6fb',
      hoverBackgroundColor: '#eaf2f9',
    },
    chart: {
      palette: [
        '#3b82f6',
        '#0ea5e9',
        '#6366f1',
        '#14b8a6',
        '#f59e0b',
        '#64748b',
      ],
      textColor: '#52657c',
      axisColor: '#b7c5d6',
      gridColor: '#dce6f0',
      tooltipBackgroundColor: '#23364d',
      tooltipTextColor: '#ffffff',
      metricValueColor: '#172b4d',
    },
    table: {
      headerBackgroundColor: '#f1f6fb',
      stripedBackgroundColor: '#f5f9fc',
      hoverBackgroundColor: '#eaf2f9',
    },
  },
];

const presetById = (presetId?: DashboardThemePresetId) =>
  DASHBOARD_THEME_PRESETS.find((item) => item.presetId === presetId) ??
  DASHBOARD_THEME_PRESETS[0];

const hasKeys = (value?: Record<string, unknown>) =>
  Boolean(value && Object.keys(value).length);

export const hasDashboardThemeOverrides = (theme?: DashboardTheme) =>
  hasKeys(theme?.canvas as Record<string, unknown> | undefined) ||
  hasKeys(theme?.component as Record<string, unknown> | undefined) ||
  hasKeys(theme?.chart as Record<string, unknown> | undefined) ||
  hasKeys(theme?.table as Record<string, unknown> | undefined);

export const normalizeDashboardTheme = (
  theme?: DashboardTheme,
): DashboardTheme => ({
  presetId: presetById(theme?.presetId).presetId,
  canvas: theme?.canvas ? { ...theme.canvas } : undefined,
  component: theme?.component ? { ...theme.component } : undefined,
  chart: theme?.chart
    ? {
        ...theme.chart,
        palette: theme.chart.palette ? [...theme.chart.palette] : undefined,
      }
    : undefined,
  table: theme?.table ? { ...theme.table } : undefined,
});

export const resolveDashboardTheme = (
  theme?: DashboardTheme,
): ResolvedDashboardTheme => {
  const normalized = normalizeDashboardTheme(theme);
  const preset = presetById(normalized.presetId);
  return {
    ...preset,
    presetId: normalized.presetId || preset.presetId,
    canvas: { ...preset.canvas, ...normalized.canvas },
    component: { ...preset.component, ...normalized.component },
    chart: {
      ...preset.chart,
      ...normalized.chart,
      palette: normalized.chart?.palette?.length
        ? [...normalized.chart.palette]
        : [...preset.chart.palette],
    },
    table: { ...preset.table, ...normalized.table },
  };
};

export const analysisThemeFromDashboardTheme = (
  theme?: DashboardTheme,
): AnalysisThemeTokens => {
  const resolved = resolveDashboardTheme(theme);
  return {
    palette: [...resolved.chart.palette],
    backgroundColor: resolved.component.backgroundColor,
    textColor: resolved.component.textColor,
    mutedTextColor: resolved.component.mutedTextColor,
    axisColor: resolved.chart.axisColor,
    gridColor: resolved.chart.gridColor,
    borderColor: resolved.component.borderColor,
    headerBackgroundColor: resolved.table.headerBackgroundColor,
    stripedBackgroundColor: resolved.table.stripedBackgroundColor,
    hoverBackgroundColor: resolved.table.hoverBackgroundColor,
    tooltipBackgroundColor: resolved.chart.tooltipBackgroundColor,
    tooltipTextColor: resolved.chart.tooltipTextColor,
    metricValueColor: resolved.chart.metricValueColor,
  };
};

export const themeFromPreset = (
  presetId: DashboardThemePresetId,
): DashboardTheme => ({ presetId });
