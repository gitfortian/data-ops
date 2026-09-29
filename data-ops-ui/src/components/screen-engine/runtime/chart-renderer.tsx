import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { useEffect, useMemo, useRef } from 'react';
import type {
  ScreenBarComponent,
  ScreenLineComponent,
  ScreenPieComponent,
  ScreenTheme,
} from '../model';
import { alpha } from './frame';

export type ScreenChartComponent = ScreenLineComponent | ScreenBarComponent | ScreenPieComponent;

const CHART_COLORS = ['#46d9ff', '#5cf2b5', '#ffc866', '#8f7cff', '#ff668f', '#43e6cf'];

const chartOptionFor = (component: ScreenChartComponent, theme: ScreenTheme): EChartsOption => {
  const axisText = theme.mutedTextColor;
  const axisLine = alpha(theme.panelBorderColor, '');
  const splitLine = theme.panelBorderColor;
  const colors = [component.style?.accentColor ?? theme.primaryColor, ...CHART_COLORS];
  const neon = component.options?.neon ?? false;
  const gradient = component.options?.gradient ?? false;

  if (component.type === 'pie') {
    const data = component.data?.items ?? [];
    return {
      color: colors,
      animation: true,
      animationDuration: 1000,
      animationEasing: 'cubicOut',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(3, 13, 25, .94)',
        borderColor: alpha(theme.primaryColor, '55'),
        textStyle: { color: theme.textColor },
      },
      legend: component.options?.showLegend === false
        ? { show: false }
        : {
            right: 6,
            top: 'middle',
            orient: 'vertical',
            itemWidth: 8,
            itemHeight: 8,
            textStyle: { color: axisText, fontSize: 11 },
          },
      series: [{
        type: 'pie',
        radius: component.options?.rose ? ['34%', '76%'] : ['48%', '72%'],
        center: [component.options?.showLegend === false ? '50%' : '39%', '52%'],
        roseType: component.options?.rose ? 'radius' : undefined,
        minAngle: 4,
        padAngle: component.options?.rose ? 2 : 0,
        label: {
          show: component.options?.showLabels ?? false,
          color: theme.textColor,
          formatter: '{b}  {d}%',
        },
        emphasis: {
          scale: true,
          scaleSize: 6,
          itemStyle: { shadowBlur: 22, shadowColor: alpha(theme.primaryColor, '66') },
        },
        itemStyle: {
          borderColor: theme.panelBackground,
          borderWidth: 2,
          borderRadius: component.options?.rose ? 7 : 2,
          shadowBlur: neon ? 10 : 0,
          shadowColor: neon ? alpha(theme.primaryColor, '44') : undefined,
        },
        data,
      }],
    };
  }

  const data = component.data ?? { categories: [], series: [] };
  const isLine = component.type === 'line';
  const horizontal = component.type === 'bar' && (component.options?.horizontal ?? false);
  const categoryAxis = {
    type: 'category' as const,
    boundaryGap: !isLine,
    data: data.categories,
    axisLine: { lineStyle: { color: axisLine } },
    axisTick: { show: false },
    axisLabel: { color: axisText, fontSize: 10, margin: 10 },
  };
  const valueAxis = {
    type: 'value' as const,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: axisText, fontSize: 10 },
    splitLine: {
      show: component.options?.showGrid ?? true,
      lineStyle: { color: splitLine, type: 'dashed' as const, opacity: 0.52 },
    },
  };

  return {
    color: colors,
    animation: true,
    animationDuration: 900,
    animationEasing: 'cubicOut',
    animationDelay: (index: number) => index * 45,
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(3, 13, 25, .94)',
      borderColor: alpha(theme.primaryColor, '55'),
      textStyle: { color: theme.textColor },
      axisPointer: { type: 'line', lineStyle: { color: alpha(theme.primaryColor, '55') } },
    },
    legend: component.options?.showLegend === false
      ? { show: false }
      : {
          top: 0,
          right: 4,
          itemWidth: 10,
          itemHeight: 6,
          textStyle: { color: axisText, fontSize: 11 },
        },
    grid: {
      left: horizontal ? 10 : 12,
      right: 10,
      top: component.options?.showLegend === false ? 14 : 34,
      bottom: 8,
      containLabel: true,
    },
    xAxis: horizontal ? valueAxis : categoryAxis,
    yAxis: horizontal ? categoryAxis : valueAxis,
    series: data.series.map((series, seriesIndex) => {
      const color = colors[seriesIndex % colors.length];
      const lineGradient = new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: alpha(color, '4d') },
        { offset: 1, color: alpha(color, '00') },
      ]);
      const barGradient = new echarts.graphic.LinearGradient(
        horizontal ? 0 : 0,
        horizontal ? 0 : 1,
        horizontal ? 1 : 0,
        0,
        [
          { offset: 0, color: alpha(color, '8c') },
          { offset: 0.6, color },
          { offset: 1, color: '#b7f7ff' },
        ],
      );
      return {
        name: series.name,
        type: component.type,
        data: series.values,
        smooth: isLine && (component.options?.smooth ?? true),
        symbol: isLine ? 'circle' : undefined,
        showSymbol: isLine ? false : undefined,
        symbolSize: isLine ? 5 : undefined,
        barMaxWidth: component.type === 'bar' ? 24 : undefined,
        barCategoryGap: component.type === 'bar' ? '42%' : undefined,
        label: {
          show: component.options?.showLabels ?? false,
          position: horizontal ? 'right' : 'top',
          color: theme.textColor,
          fontSize: 10,
        },
        lineStyle: isLine
          ? {
              width: neon ? 2.5 : 2,
              color,
              shadowBlur: neon ? 10 : 0,
              shadowColor: neon ? alpha(color, '99') : undefined,
            }
          : undefined,
        areaStyle: isLine
          ? {
              opacity: component.options?.showArea === false ? 0 : 1,
              color: gradient || neon ? lineGradient : alpha(color, '16'),
            }
          : undefined,
        itemStyle: component.type === 'bar'
          ? {
              color: gradient ? barGradient : color,
              borderRadius: horizontal ? [0, 7, 7, 0] : [7, 7, 1, 1],
              shadowBlur: neon ? 8 : 0,
              shadowColor: neon ? alpha(color, '66') : undefined,
            }
          : { color },
        emphasis: {
          focus: 'series',
          itemStyle: { shadowBlur: 16, shadowColor: alpha(color, '99') },
        },
      };
    }),
  };
};

export function ScreenChart({
  component,
  theme,
}: {
  component: ScreenChartComponent;
  theme: ScreenTheme;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const option = useMemo(() => chartOptionFor(component, theme), [component, theme]);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const chart = echarts.init(containerRef.current);
    chart.setOption(option, true);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
    };
  }, [option]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}
