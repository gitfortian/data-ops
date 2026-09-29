import type {
  AnalysisSelection,
  DashboardFilter,
  DashboardWidget,
} from './model';

export type DashboardRuntimeSelections = Record<string, AnalysisSelection | undefined>;

export const sameDashboardSelection = (
  left?: AnalysisSelection,
  right?: AnalysisSelection,
) => Boolean(
  left
  && right
  && left.fieldId === right.fieldId
  && Object.is(left.value, right.value),
);

/**
 * Materialize chart-to-chart links as ordinary runtime Analysis filters. This deliberately
 * stays outside the persisted AnalysisSpec: a click is session state, not Dashboard dirty state.
 */
export const directCrossFiltersForWidget = (
  widgets: DashboardWidget[],
  selections: DashboardRuntimeSelections,
  targetWidgetId: string,
): DashboardFilter[] => widgets.flatMap((sourceWidget) => {
  const selection = selections[sourceWidget.id];
  if (!selection || !sourceWidget.inlineAnalysis) return [];
  const rules = sourceWidget.inlineAnalysis.dashboardBehavior?.crossFilters ?? [];
  return rules.flatMap((rule) => {
    if (
      rule.targetWidgetId !== targetWidgetId
      || rule.sourceField !== selection.fieldId
      || selection.value === undefined
      || selection.value === null
      || selection.value === ''
    ) return [];
    return [{
      id: `dashboard-cross-${sourceWidget.id}-${rule.id}`,
      field: rule.targetField,
      operator: 'eq' as const,
      value: String(selection.value),
    }];
  });
});

export const pruneRuntimeSelections = (
  widgets: DashboardWidget[],
  selections: DashboardRuntimeSelections,
): DashboardRuntimeSelections => {
  const widgetIds = new Set(widgets.map((widget) => widget.id));
  return Object.fromEntries(
    Object.entries(selections).filter(([widgetId, selection]) => widgetIds.has(widgetId) && selection),
  ) as DashboardRuntimeSelections;
};
