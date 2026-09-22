import type { DashboardDocument } from './model';

/** Unsaved shell. The first explicit save creates a server-side Dashboard V1. */
export const DEFAULT_DASHBOARD: DashboardDocument = {
  version: 1,
  id: 'dashboard-new',
  name: '未命名仪表盘',
  description: '',
  activeDatasetId: '',
  theme: { presetId: 'yak-light' },
  widgets: [],
  globalFilters: [],
  interactions: [],
};
