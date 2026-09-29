export interface DashboardPerformanceQueryBinding {
  widgetId: string;
  widgetName: string;
  datasetId: string;
  queryKey: string;
}

const bindings = new Map<string, DashboardPerformanceQueryBinding>();

/**
 * Keeps the latest query signature for each chart rendered in the current browser session.
 * Entries are intentionally overwritten by widget id so chart edits and runtime filters
 * always point performance analysis at the query the chart actually executed.
 */
export const registerDashboardPerformanceQuery = (
  binding: DashboardPerformanceQueryBinding,
) => {
  bindings.set(binding.widgetId, binding);
};

export const getDashboardPerformanceQuery = (widgetId: string) => bindings.get(widgetId);
