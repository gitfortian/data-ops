import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { useMemo } from 'react';

interface TrendChartProps {
  values: number[];
  labels: string[];
  name: string;
  height?: number;
}

export function TrendChart({
  values,
  labels,
  name,
  height = 152,
}: TrendChartProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      animation: true,
      animationDuration: 720,
      animationDurationUpdate: 620,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
      grid: {
        left: 0,
        right: 2,
        top: 18,
        bottom: 26,
        containLabel: false,
      },
      tooltip: {
        trigger: 'axis',
        confine: true,
        appendToBody: false,
        backgroundColor: 'rgba(255,255,255,0.98)',
        borderColor: '#edf0f4',
        borderWidth: 1,
        padding: [10, 12],
        textStyle: {
          color: '#30333b',
          fontSize: 12,
        },
        extraCssText:
          'box-shadow:0 8px 28px rgba(31,35,41,.10);border-radius:8px;',
        axisPointer: {
          type: 'line',
          lineStyle: {
            color: '#dfe6f4',
            width: 1,
          },
        },
        formatter: (params: any) => {
          const item = Array.isArray(params) ? params[0] : params;
          if (!item) return '';
          return `
            <div style="min-width:120px">
              <div style="font-size:12px;color:#555a64;margin-bottom:8px">${item.axisValue}</div>
              <div style="display:flex;align-items:center;justify-content:space-between;gap:20px">
                <span style="display:flex;align-items:center;color:#30333b;font-weight:600">
                  <i style="display:inline-block;width:7px;height:7px;border-radius:999px;background:#5b8cff;margin-right:6px"></i>
                  ${name}
                </span>
                <b style="font-size:12px;color:#30333b">${item.data}</b>
              </div>
            </div>
          `;
        },
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: labels,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: {
          color: '#8d929b',
          fontSize: 11,
          margin: 9,
          interval: labels.length > 10 ? 4 : 0,
          showMinLabel: true,
          showMaxLabel: true,
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: Math.max(...values, 1) * 1.2,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { show: false },
        splitLine: { show: false },
      },
      series: [
        {
          name,
          type: 'line',
          data: values,
          smooth: 0.42,
          showSymbol: false,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: {
            color: '#5b8cff',
            width: 1.5,
          },
          itemStyle: {
            color: '#5b8cff',
            borderColor: '#ffffff',
            borderWidth: 2,
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(91,140,255,0.18)' },
                { offset: 1, color: 'rgba(91,140,255,0.00)' },
              ],
            },
          },
          emphasis: {
            focus: 'series',
            scale: true,
          },
        },
      ],
    }),
    [labels, name, values],
  );

  return (
    <ReactECharts
      option={option}
      notMerge
      lazyUpdate
      style={{ height, width: '100%' }}
    />
  );
}
