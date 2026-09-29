import {
  applyAnalysisChartTheme,
  resolveAnalysisThemeTokens,
} from './analysis-theme';

describe('analysis theme', () => {
  it('resolves a partial theme without losing renderer defaults', () => {
    const theme = resolveAnalysisThemeTokens({
      palette: ['#38bdf8', '#818cf8'],
      backgroundColor: '#0a2138',
    });

    expect(theme.palette).toEqual(['#38bdf8', '#818cf8']);
    expect(theme.backgroundColor).toBe('#0a2138');
    expect(theme.textColor).toBeTruthy();
    expect(theme.gridColor).toBeTruthy();
  });

  it('preserves a chart-local palette while applying the remaining theme tokens', () => {
    const option = {
      color: ['#old'],
      tooltip: { trigger: 'item' },
      legend: { show: true, textStyle: { fontSize: 11 } },
      xAxis: {
        axisLine: { lineStyle: { width: 1 } },
        axisLabel: { fontSize: 11 },
        splitLine: { lineStyle: { type: 'dashed' } },
      },
      yAxis: {},
      series: [{
        type: 'pie',
        label: { show: true, position: 'outside' },
        itemStyle: { borderWidth: 2, borderColor: '#fff' },
      }],
    };

    const themed = applyAnalysisChartTheme(option, {
      palette: ['#38bdf8', '#818cf8'],
      backgroundColor: '#0a2138',
      textColor: '#e5f3ff',
      mutedTextColor: '#8db3d3',
      axisColor: '#2d5c7c',
      gridColor: '#15344d',
      borderColor: '#163b59',
      tooltipBackgroundColor: '#071624',
      tooltipTextColor: '#edf8ff',
    });

    expect(themed.color).toEqual(['#old']);
    expect(themed.legend.textStyle.color).toBe('#8db3d3');
    expect(themed.tooltip.backgroundColor).toBe('#071624');
    expect(themed.tooltip.textStyle.color).toBe('#edf8ff');
    expect(themed.xAxis.axisLine.lineStyle.color).toBe('#2d5c7c');
    expect(themed.xAxis.splitLine.lineStyle.color).toBe('#15344d');
    expect(themed.series[0].label.color).toBe('#e5f3ff');
    expect(themed.series[0].itemStyle.borderColor).toBe('#0a2138');
    expect(option.series[0].itemStyle.borderColor).toBe('#fff');
  });

  it('falls back to the dashboard theme palette when the chart does not define colors', () => {
    const themed = applyAnalysisChartTheme(
      { series: [{ type: 'bar', data: [1, 2] }] },
      { palette: ['#38bdf8', '#818cf8'] },
    );

    expect(themed.color).toEqual(['#38bdf8', '#818cf8']);
  });
});
