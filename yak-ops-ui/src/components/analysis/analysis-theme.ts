export interface AnalysisThemeTokens {
  palette: string[];
  backgroundColor: string;
  textColor: string;
  mutedTextColor: string;
  axisColor: string;
  gridColor: string;
  borderColor: string;
  headerBackgroundColor: string;
  stripedBackgroundColor: string;
  hoverBackgroundColor: string;
  tooltipBackgroundColor: string;
  tooltipTextColor: string;
  metricValueColor: string;
}

export const DEFAULT_ANALYSIS_THEME: AnalysisThemeTokens = {
  palette: ['#fe2c55', '#5b6df8', '#8b5cf6', '#f59e0b', '#0ea5e9', '#64748b'],
  backgroundColor: '#ffffff',
  textColor: '#344054',
  mutedTextColor: '#667085',
  axisColor: '#d8dde6',
  gridColor: '#eef1f5',
  borderColor: '#e7eaf0',
  headerBackgroundColor: '#fafafa',
  stripedBackgroundColor: '#fafbfc',
  hoverBackgroundColor: '#f7f8fa',
  tooltipBackgroundColor: 'rgba(17,24,39,.94)',
  tooltipTextColor: '#ffffff',
  metricValueColor: '#161823',
};

export const resolveAnalysisThemeTokens = (
  theme?: Partial<AnalysisThemeTokens>,
): AnalysisThemeTokens => ({
  ...DEFAULT_ANALYSIS_THEME,
  ...theme,
  palette: theme?.palette?.length ? [...theme.palette] : [...DEFAULT_ANALYSIS_THEME.palette],
});

const mapOption = (value: any, mapper: (item: any) => any) => {
  if (Array.isArray(value)) return value.map(mapper);
  if (value && typeof value === 'object') return mapper(value);
  return value;
};

const themeAxis = (axis: any, theme: AnalysisThemeTokens) => ({
  ...axis,
  nameTextStyle: {
    ...(axis?.nameTextStyle || {}),
    color: theme.mutedTextColor,
  },
  axisLine: {
    ...(axis?.axisLine || {}),
    lineStyle: {
      ...(axis?.axisLine?.lineStyle || {}),
      color: theme.axisColor,
    },
  },
  axisTick: {
    ...(axis?.axisTick || {}),
    lineStyle: {
      ...(axis?.axisTick?.lineStyle || {}),
      color: theme.axisColor,
    },
  },
  axisLabel: {
    ...(axis?.axisLabel || {}),
    color: theme.mutedTextColor,
  },
  splitLine: {
    ...(axis?.splitLine || {}),
    lineStyle: {
      ...(axis?.splitLine?.lineStyle || {}),
      color: theme.gridColor,
    },
  },
});

const themeSeries = (series: any, theme: AnalysisThemeTokens) => {
  const inside = series?.label?.position === 'inside';
  const blockChart = ['pie', 'funnel', 'treemap'].includes(series?.type);
  return {
    ...series,
    label: series?.label ? {
      ...series.label,
      color: series.type === 'treemap' || inside ? '#ffffff' : theme.textColor,
    } : series?.label,
    itemStyle: blockChart ? {
      ...(series?.itemStyle || {}),
      borderColor: theme.backgroundColor,
    } : series?.itemStyle,
  };
};

/**
 * Apply presentation-only tokens to an ECharts option built from Analysis semantics.
 * Chart-local colors stay authoritative; the Dashboard theme palette is only a fallback.
 */
export const applyAnalysisChartTheme = (
  option: any,
  value?: Partial<AnalysisThemeTokens>,
) => {
  if (!option) return option;
  const theme = resolveAnalysisThemeTokens(value);
  const palette = Array.isArray(option.color) && option.color.length
    ? option.color
    : theme.palette;
  return {
    ...option,
    backgroundColor: 'transparent',
    color: [...palette],
    textStyle: {
      ...(option.textStyle || {}),
      color: theme.textColor,
    },
    tooltip: option.tooltip ? {
      ...option.tooltip,
      backgroundColor: theme.tooltipBackgroundColor,
      borderColor: theme.borderColor,
      textStyle: {
        ...(option.tooltip?.textStyle || {}),
        color: theme.tooltipTextColor,
      },
    } : option.tooltip,
    legend: mapOption(option.legend, (legend) => ({
      ...legend,
      textStyle: {
        ...(legend?.textStyle || {}),
        color: theme.mutedTextColor,
      },
    })),
    xAxis: mapOption(option.xAxis, (axis) => themeAxis(axis, theme)),
    yAxis: mapOption(option.yAxis, (axis) => themeAxis(axis, theme)),
    radar: mapOption(option.radar, (radar) => ({
      ...radar,
      axisName: {
        ...(radar?.axisName || {}),
        color: theme.mutedTextColor,
      },
      axisLine: {
        ...(radar?.axisLine || {}),
        lineStyle: {
          ...(radar?.axisLine?.lineStyle || {}),
          color: theme.axisColor,
        },
      },
      splitLine: {
        ...(radar?.splitLine || {}),
        lineStyle: {
          ...(radar?.splitLine?.lineStyle || {}),
          color: theme.gridColor,
        },
      },
      splitArea: {
        ...(radar?.splitArea || {}),
        areaStyle: {
          ...(radar?.splitArea?.areaStyle || {}),
          color: ['transparent'],
        },
      },
    })),
    series: mapOption(option.series, (series) => themeSeries(series, theme)),
  };
};
