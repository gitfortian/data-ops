import {
  analysisThemeFromDashboardTheme,
  DASHBOARD_THEME_PRESETS,
  hasDashboardThemeOverrides,
  normalizeDashboardTheme,
  resolveDashboardTheme,
  themeFromPreset,
} from './dashboard-theme';

describe('dashboard themes', () => {
  it('ships a compact curated preset library', () => {
    expect(DASHBOARD_THEME_PRESETS.map((item) => item.presetId)).toEqual([
      'yak-light',
      'yak-dark',
      'ocean-night',
      'graphite',
      'mist-blue',
    ]);
    DASHBOARD_THEME_PRESETS.forEach((preset) => {
      expect(preset.chart.palette.length).toBeGreaterThanOrEqual(5);
      expect(preset.component.backgroundColor).toBeTruthy();
      expect(preset.canvas.backgroundColor).toBeTruthy();
      expect(preset.table.headerBackgroundColor).toBeTruthy();
    });
  });

  it('preserves the new preset ids during normalization', () => {
    expect(normalizeDashboardTheme(themeFromPreset('ocean-night')).presetId).toBe('ocean-night');
    expect(normalizeDashboardTheme(themeFromPreset('graphite')).presetId).toBe('graphite');
    expect(normalizeDashboardTheme(themeFromPreset('mist-blue')).presetId).toBe('mist-blue');
  });

  it('resolves sparse custom overrides on top of the selected preset', () => {
    const base = resolveDashboardTheme(themeFromPreset('ocean-night'));
    const theme = {
      presetId: 'ocean-night' as const,
      canvas: { backgroundColor: '#020617' },
      component: { textColor: '#ffffff' },
      chart: { palette: ['#111111', '#222222', '#333333'] },
      table: { headerBackgroundColor: '#08192b' },
    };
    const resolved = resolveDashboardTheme(theme);

    expect(resolved.canvas.backgroundColor).toBe('#020617');
    expect(resolved.component.textColor).toBe('#ffffff');
    expect(resolved.component.backgroundColor).toBe(base.component.backgroundColor);
    expect(resolved.chart.palette).toEqual(['#111111', '#222222', '#333333']);
    expect(resolved.chart.axisColor).toBe(base.chart.axisColor);
    expect(resolved.table.headerBackgroundColor).toBe('#08192b');
    expect(resolved.table.hoverBackgroundColor).toBe(base.table.hoverBackgroundColor);
  });

  it('detects custom overrides while a pure preset stays clean', () => {
    expect(hasDashboardThemeOverrides(themeFromPreset('yak-dark'))).toBe(false);
    expect(hasDashboardThemeOverrides({
      presetId: 'yak-dark',
      component: { backgroundColor: '#101010' },
    })).toBe(true);
  });

  it('turns dashboard tokens into renderer tokens without mutating the dashboard config', () => {
    const dashboardTheme = {
      presetId: 'ocean-night' as const,
      chart: { palette: ['#111111', '#222222'] },
      table: {
        headerBackgroundColor: '#091d30',
        hoverBackgroundColor: '#123957',
      },
    };
    const before = JSON.stringify(dashboardTheme);
    const rendererTheme = analysisThemeFromDashboardTheme(dashboardTheme);

    expect(rendererTheme.palette).toEqual(['#111111', '#222222']);
    expect(rendererTheme.backgroundColor).toBe(resolveDashboardTheme(dashboardTheme).component.backgroundColor);
    expect(rendererTheme.metricValueColor).toBe('#7dd3fc');
    expect(rendererTheme.headerBackgroundColor).toBe('#091d30');
    expect(rendererTheme.hoverBackgroundColor).toBe('#123957');
    expect(JSON.stringify(dashboardTheme)).toBe(before);
  });
});
